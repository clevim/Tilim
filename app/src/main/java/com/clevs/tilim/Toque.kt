package com.clevs.tilim

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

private val PT_BR = Locale.forLanguageTag("pt-BR")

/** Voz em português que funciona sem internet (já baixada no aparelho). */
fun vozOffline(tts: TextToSpeech): android.speech.tts.Voice? = runCatching {
    tts.voices?.filter {
        it.locale.language == "pt" && it.locale.country == "BR" && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
    }?.maxByOrNull { it.quality }
}.getOrNull()

/**
 * Liga o sintetizador só para checar a voz. Pedir o português já faz o Google baixar a voz offline
 * (precisa de internet uma vez), por isso o app chama isto ao abrir: no dia do alarme ela já está lá.
 */
fun checarVoz(ctx: Context, resultado: (Boolean) -> Unit = {}) {
    var tts: TextToSpeech? = null
    tts = TextToSpeech(ctx.applicationContext) { st ->
        val t = tts ?: return@TextToSpeech
        val ok = st == TextToSpeech.SUCCESS && t.setLanguage(PT_BR) >= TextToSpeech.LANG_AVAILABLE && vozOffline(t) != null
        t.shutdown()
        resultado(ok)
    }
}

/**
 * Silencioso não cala alarme (o canal de alarme fica fora do modo silencioso). O que cala é o "Não perturbe"
 * em silêncio total ou com alarmes desligados. Com o acesso liberado, troca para "só alarmes" enquanto toca.
 * O modo antigo fica salvo: se o serviço morrer sem onDestroy, o app devolve ao abrir (devolverNaoPerturbe).
 */
fun furarNaoPerturbe(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (!nm.isNotificationPolicyAccessGranted) return
    val f = nm.currentInterruptionFilter
    val alarmesCalados = f == NotificationManager.INTERRUPTION_FILTER_NONE ||
        (f == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
            nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0)
    if (!alarmesCalados) return
    ctx.getSharedPreferences("tilim", Context.MODE_PRIVATE).edit().putInt("filtro_antigo", f).commit()
    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
}

fun devolverNaoPerturbe(ctx: Context) {
    val p = ctx.getSharedPreferences("tilim", Context.MODE_PRIVATE)
    val f = p.getInt("filtro_antigo", -1).takeIf { it >= 0 } ?: return
    runCatching { ctx.getSystemService(NotificationManager::class.java).setInterruptionFilter(f) }
    p.edit().remove("filtro_antigo").apply()
}

data class Tocando(val titulo: String, val inicio: Long, val local: String)

object Toque {
    val atual = MutableStateFlow<Tocando?>(null)
    const val PARAR = "parar"
    const val ADIAR = "adiar"
    const val RENOTIFICAR = "renotificar"
    const val ADIAR_MIN = 5L
    // ponytail: para sozinho depois de 10 min para não esgotar bateria; vira opção se pedirem
    const val TEMPO_MAX = 10 * 60_000L

    fun tocar(ctx: Context, titulo: String, inicio: Long, local: String) {
        val i = Intent(ctx, ToqueServico::class.java).putExtra("titulo", titulo).putExtra("inicio", inicio)
            .putExtra("local", local)
        try {
            ctx.startForegroundService(i)
        } catch (_: IllegalStateException) {
            // Android 12+ bloqueia serviço em 2º plano fora do alarme exato. Só acontece se a varredura
            // de mudança da agenda achar um aviso que o alarme já deveria ter tocado — raro, ignorado.
        }
    }

    fun comando(ctx: Context, acao: String) =
        ctx.startService(Intent(ctx, ToqueServico::class.java).setAction(acao))
}

class ToqueServico : Service() {
    private var player: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private var volumeAntigo = -1
    private val handler = Handler(Looper.getMainLooper())
    private var voz: TextToSpeech? = null
    private var vozPronta = false
    private val falarDeNovo = Runnable { falar() }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Toque.PARAR -> { stopSelf(); return START_NOT_STICKY }
            Toque.ADIAR -> { adiar(); stopSelf(); return START_NOT_STICKY }
            // arrastaram a notificação (Android 14+ deixa): põe de volta enquanto tocar
            Toque.RENOTIFICAR -> {
                Toque.atual.value?.let { getSystemService(NotificationManager::class.java).notify(1, notificacao(it)) } ?: stopSelf()
                return START_NOT_STICKY
            }
        }
        val t = Tocando(intent?.getStringExtra("titulo").orEmpty(), intent?.getLongExtra("inicio", 0) ?: 0,
            intent?.getStringExtra("local").orEmpty())
        Toque.atual.value = t
        val n = notificacao(t)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        if (player == null) tocarSom()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, Toque.TEMPO_MAX)
        if (falar(this)) iniciarVoz()
        return START_NOT_STICKY
    }

    private fun tocarSom() {
        furarNaoPerturbe(this) // antes do volume: no silêncio total o Android recusa mexer no volume
        val audio = getSystemService(AudioManager::class.java)
        // alguns aparelhos recusam mudar volume em "Não perturbe": toca no volume atual em vez de quebrar o alarme
        try {
            volumeAntigo = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: SecurityException) { volumeAntigo = -1 }

        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        // o escolhido nos Ajustes; se falhar (toque apagado do celular), cai no alarme do sistema
        val uris = listOfNotNull(
            som(this)?.let { uriDoSom(this, it) },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in uris) {
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@ToqueServico, uri)
                    isLooping = true
                    setWakeMode(this@ToqueServico, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
            } catch (_: Exception) { null }
            if (player != null) break
        }

        vibrador = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        @Suppress("DEPRECATION")
        vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0), attrs)
    }

    /** Pausa o som, fala o compromisso e volta a tocar; repete enquanto ninguém parar. */
    private fun iniciarVoz() {
        if (voz != null) { handler.postDelayed(falarDeNovo, 3000); return }
        voz = TextToSpeech(this) { st ->
            val tts = voz ?: return@TextToSpeech
            // sem voz em português instalada, falar em outra língua confunde mais que ajuda: fica só o som
            vozPronta = st == TextToSpeech.SUCCESS && tts.setLanguage(PT_BR) >= TextToSpeech.LANG_AVAILABLE
            if (!vozPronta) return@TextToSpeech
            // prefere a voz baixada: a de servidor falha sem internet (aí só o som toca)
            vozOffline(tts)?.let { tts.voice = it }
            tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            tts.setSpeechRate(0.85f) // um pouco mais devagar: público idoso
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { voltarSom() }
                @Deprecated("API antiga") override fun onError(id: String?) { voltarSom() }
            })
            handler.postDelayed(falarDeNovo, 3000) // 3 s de som antes: chama atenção primeiro
        }
    }

    private fun falar() {
        val t = Toque.atual.value ?: return
        val tts = voz?.takeIf { vozPronta } ?: return
        player?.pause()
        tts.speak(fala(t.titulo, t.inicio, t.local, System.currentTimeMillis(), java.time.ZoneId.systemDefault()),
            TextToSpeech.QUEUE_FLUSH, null, "fala")
    }

    private fun voltarSom() = handler.post {
        if (Toque.atual.value == null) return@post
        runCatching { player?.start() }
        handler.postDelayed(falarDeNovo, 20_000)
    }

    private fun adiar() {
        val t = Toque.atual.value ?: return
        val i = Intent(this, Despertador::class.java).setAction(Despertador.ADIADO).putExtra("titulo", t.titulo)
            .putExtra("inicio", t.inicio).putExtra("local", t.local)
        val pi = PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // ponytail: o "lembrar de novo" some se o celular reiniciar nesses 5 min
        getSystemService(AlarmManager::class.java).setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + Toque.ADIAR_MIN * 60_000, abrirApp(this)), pi)
    }

    private fun notificacao(t: Tocando): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("alarme", "Alarmes de compromisso", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null); enableVibration(false) }) // som e vibração vêm do serviço
        val tela = PendingIntent.getActivity(this, 2, Intent(this, ToqueActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        fun acao(nome: String, acao: String, req: Int) = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_sino), nome,
            PendingIntent.getService(this, req, Intent(this, ToqueServico::class.java).setAction(acao), PendingIntent.FLAG_IMMUTABLE)
        ).build()
        return Notification.Builder(this, "alarme")
            .setSmallIcon(R.drawable.ic_sino)
            .setColor(0xFFB5502E.toInt())
            .setContentTitle(t.titulo)
            .setContentText(frase(t))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(tela, true)
            .setContentIntent(tela)
            .setOngoing(true)
            .setDeleteIntent(PendingIntent.getService(this, 5, Intent(this, ToqueServico::class.java)
                .setAction(Toque.RENOTIFICAR), PendingIntent.FLAG_IMMUTABLE))
            .addAction(acao("Parar", Toque.PARAR, 3))
            .addAction(acao("Adiar ${Toque.ADIAR_MIN} minutos", Toque.ADIAR, 4))
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        voz?.run { stop(); shutdown() }
        voz = null
        player?.run { stop(); release() }
        player = null
        vibrador?.cancel()
        if (volumeAntigo >= 0) runCatching {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, volumeAntigo, 0)
        }
        Toque.atual.value = null
        devolverNaoPerturbe(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}

/** Tela cheia por cima do bloqueio, igual despertador. Fecha sozinha quando o som para. */
class ToqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Android 8.0 não entende showWhenLocked/turnScreenOn do manifest
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 27) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContent {
            TilimTema {
                val t by Toque.atual.collectAsState()
                LaunchedEffect(t) { if (t == null) finish() }
                t?.let { TelaToque(it) }
            }
        }
    }

    @Composable
    private fun TelaToque(t: Tocando) {
        // única animação do app: o sino balança enquanto toca
        val transicao = rememberInfiniteTransition(label = "sino")
        val angulo by transicao.animateFloat(-16f, 16f,
            infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sino")
        // ondas de som saindo do sino
        val onda by transicao.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "onda")
        Surface(color = Terracota, contentColor = Creme, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp)) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (k in 0..2) {
                            val q = (onda + k / 3f) % 1f
                            drawCircle(Creme.copy(alpha = (1f - q) * 0.45f), radius = size.minDimension / 2 * (0.42f + 0.58f * q),
                                style = Stroke(3.dp.toPx()))
                        }
                    }
                    Image(painterResource(R.drawable.ic_sino), null,
                        Modifier.size(84.dp).graphicsLayer { rotationZ = angulo; transformOrigin = TransformOrigin(0.5f, 0f) },
                        colorFilter = ColorFilter.tint(Creme))
                }
                Spacer(Modifier.height(32.dp))
                Text(frase(t), fontFamily = Lexend, fontSize = 26.sp, lineHeight = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text(t.titulo, fontFamily = Lexend, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold)
                if (t.local.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(t.local, fontFamily = Lexend, fontSize = 22.sp, lineHeight = 28.sp)
                }
                Spacer(Modifier.weight(1f))
                Button({ Toque.comando(this@ToqueActivity, Toque.PARAR) }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Creme, contentColor = Terracota)) {
                    Text("Parar", fontFamily = Lexend, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton({ Toque.comando(this@ToqueActivity, Toque.ADIAR) }, Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, Creme),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Creme)) {
                    Text("Adiar ${Toque.ADIAR_MIN} minutos", fontFamily = Lexend, fontSize = 24.sp)
                }
            }
        }
    }
}

/** "Daqui a 30 minutos, às 14h30" — calculado na hora, então serve para adiar e testar. */
fun frase(t: Tocando): String {
    val f = falta(t.inicio, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
    return if (f == "Agora") "Agora, ${hora(t.inicio)}" else "$f, às ${hora(t.inicio)}"
}

