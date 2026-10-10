package com.clevs.tilim

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.runtime.DisposableEffect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import androidx.compose.foundation.lazy.LazyListScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Tema ----------
// Referência: a folhinha de parede (número do dia enorme) + o sino recortado do ícone.
// Duas cores como o ícone: terracota só para o que importa (número do dia, ações, sino ativo).

val Terracota = Color(0xFFB5502E)
val Creme = Color(0xFFF3EDE4)
val Tinta = Color(0xFF2B2420)
val Fio = Color(0xFFE0D3C2)      // divisórias e bordas neutras
val Lapis = Color(0xFF6B5D52)    // texto secundário (5,6:1 sobre o creme)

// Lexend: desenhada para fluência de leitura; zero sem corte (a Lexend corta o zero e "23h00" vira código)
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Lexend = FontFamily(
    Font(R.font.lexend, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lexend, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// ponytail: só tema claro — contraste alto e previsível para o público; escuro se pedirem
@Composable
fun TilimTema(content: @Composable () -> Unit) {
    fun estilo(tam: Int, linha: Int, peso: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Lexend, fontSize = tam.sp, lineHeight = linha.sp, fontWeight = peso)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Terracota, onPrimary = Creme,
            background = Creme, onBackground = Tinta,
            surface = Creme, onSurface = Tinta,
            onSurfaceVariant = Lapis, outline = Terracota, outlineVariant = Fio,
        ),
        typography = Typography(
            displayLarge = estilo(72, 72, FontWeight.Bold),
            headlineLarge = estilo(32, 38, FontWeight.Bold),
            titleLarge = estilo(24, 30, FontWeight.Bold),
            bodyLarge = estilo(22, 30),
            bodyMedium = estilo(19, 26),
            labelLarge = estilo(22, 28, FontWeight.Bold),
        ),
    ) {
        // as letras do Tilim já são grandes; somar a fonte gigante do sistema estoura a tela e esconde a lista
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(FONTE_MAX)), content = content)
    }
}

// ponytail: teto fixo; ainda cresce 30% sobre letras que já são grandes
const val FONTE_MAX = 1.3f

// ---------- Datas em português ----------

private val ptBR = Locale.forLanguageTag("pt-BR")
private val fmtHora = DateTimeFormatter.ofPattern("H'h'mm")
private val fmtDia = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)

internal fun zdt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
fun hora(ms: Long): String = zdt(ms).format(fmtHora)
fun diaPorExtenso(ms: Long): String = zdt(ms).format(fmtDia).replace("-feira", "").replaceFirstChar { it.uppercase() }

private fun nomeDoDia(d: LocalDate, ms: Long) = when (d) {
    LocalDate.now() -> "Hoje"
    LocalDate.now().plusDays(1) -> "Amanhã"
    else -> diaPorExtenso(ms)
}

// ---------- Permissões ----------

/** dica = o que a pessoa vai ver depois de tocar no botão: quem não sabe o que esperar acaba tocando em "Negar". */
private data class Passo(val titulo: String, val texto: String, val ok: Boolean, val obrigatorio: Boolean, val dica: String, val acao: () -> Unit)

private const val PERGUNTA = "Vai aparecer uma pergunta. Toque em \u201cPermitir\u201d."
private const val TELA_DO_CELULAR = "Vai abrir uma tela do celular. Ligue a chave do Tilim e depois volte para cá."

private fun tem(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

private fun abrir(ctx: Context, acao: String) =
    ctx.startActivity(Intent(acao, Uri.parse("package:${ctx.packageName}")))

private fun dicaDaMarca() = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> "No Xiaomi: Configurações › Apps › Tilim › ative \"Início automático\" e em Economia de bateria escolha \"Sem restrições\"."
    "samsung" -> "No Samsung: Configurações › Bateria › Limites de uso em segundo plano › tire o Tilim de \"Apps em suspensão\"."
    "motorola" -> "No Motorola: Configurações › Apps › Tilim › Bateria › escolha \"Sem restrições\"."
    else -> "Se o seu celular tiver \"início automático\" ou \"apps em suspensão\", libere o Tilim lá também."
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    private var volta by mutableIntStateOf(0) // muda a cada onResume: relê permissões e agenda

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (falar(this)) checarVoz(this) // já pede o download da voz enquanto há internet
        if (Toque.atual.value == null) devolverNaoPerturbe(this) // serviço morreu tocando: devolve o "Não perturbe"
        setContent { TilimTema { App(volta) } }
    }

    override fun onResume() {
        super.onResume()
        reagendar(this)
        sincronizar(this)
        volta++
    }
}

@Composable
private fun App(volta: Int) {
    val ctx = LocalContext.current
    var pulados by rememberSaveable { mutableStateOf(listOf<String>()) } // lista comum: StateList não sobrevive a girar a tela
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        // negou duas vezes: o Android não mostra mais o pedido, então leva direto à tela de permissões do app
        val bloqueado = r.any { (p, ok) -> !ok && !(ctx as ComponentActivity).shouldShowRequestPermissionRationale(p) }
        if (bloqueado) abrir(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        reagendar(ctx)
    }

    val passos = remember(volta) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        // agenda e notificações num toque só: o Android mostra as duas perguntas em seguida
        val basicas = listOfNotNull(Manifest.permission.READ_CALENDAR,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null)
        listOfNotNull(
            Passo("Ver sua agenda", "Para o Tilim mostrar os compromissos do Google Agenda e avisar na hora.",
                basicas.all { tem(ctx, it) }, true, PERGUNTA + " Se aparecer outra, toque em \u201cPermitir\u201d de novo.") {
                pedir.launch(basicas.toTypedArray())
            },
            // estes dois o Android costuma liberar sozinho; só aparecem se o celular negou
            if (Build.VERSION.SDK_INT >= 31) Passo("Tocar na hora certa", "Para o alarme não atrasar.",
                am.canScheduleExactAlarms(), true, TELA_DO_CELULAR) { abrir(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) } else null,
            if (Build.VERSION.SDK_INT >= 34) Passo("Acender a tela", "Para o alarme aparecer na tela, mesmo com o celular bloqueado.",
                nm.canUseFullScreenIntent(), true, TELA_DO_CELULAR) { abrir(ctx, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) } else null,
            Passo("Não economizar bateria", "Alguns celulares desligam apps para poupar bateria, e o alarme deixa de tocar. " + dicaDaMarca(),
                pm.isIgnoringBatteryOptimizations(ctx.packageName), false,
                PERGUNTA + " O celular avisa que pode gastar mais bateria, mas o Tilim quase não gasta.") {
                abrir(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            },
            // opcional: silencioso já não cala alarme; isto cobre o "Não perturbe" em silêncio total
            Passo("Tocar no \u201cNão perturbe\u201d", "Para o alarme tocar mesmo com o celular no modo \u201cNão perturbe\u201d.",
                nm.isNotificationPolicyAccessGranted, false, "Vai abrir uma lista do celular. Toque em \u201cTilim\u201d, ligue a chave e depois volte para cá.") {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
        )
    }

    // quem estava faltando quando a tela abriu: dá o "Passo 2 de 3" estável enquanto a pessoa avança
    val faltavam = rememberSaveable { ArrayList(passos.filter { !it.ok }.map { it.titulo }) }
    val atual = passos.firstOrNull { !it.ok && it.titulo !in pulados }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            if (atual != null) {
                val total = maxOf(faltavam.size, 1)
                Configurar(atual, numero = (faltavam.indexOf(atual.titulo) + 1).coerceIn(1, total), total = total) { pulados = pulados + atual.titulo }
            } else {
                Agenda(volta)
            }
        }
    }
}

/** Um passo por vez, letra grande, dizendo o que vai aparecer. Ao aceitar, o onResume relê e o próximo entra sozinho. */
@Composable
private fun Configurar(p: Passo, numero: Int, total: Int, pular: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Sino(64.dp)
        Text(if (total > 1) "Passo $numero de $total" else "Falta só isto",
            style = MaterialTheme.typography.bodyLarge, color = Terracota)
        Text(p.titulo, style = MaterialTheme.typography.headlineLarge)
        Text(p.texto, style = MaterialTheme.typography.bodyLarge, color = Lapis)
        HorizontalDivider(color = Fio, thickness = 2.dp)
        Text(p.dica, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Botao("Permitir", p.acao)
        if (!p.obrigatorio) BotaoContorno("Pular esta parte", pular)
    }
}

private sealed interface Tela {
    data object Lista : Tela
    data class Detalhe(val oc: Ocorrencia) : Tela
    data object Ajustes : Tela
    data object Novo : Tela
    data class Editar(val oc: Ocorrencia) : Tela
}

@Composable
private fun Agenda(volta: Int) {
    val ctx = LocalContext.current
    var tela by remember { mutableStateOf<Tela>(Tela.Lista) }
    var mudou by remember { mutableIntStateOf(0) }
    val pad = remember(volta, mudou) { padrao(ctx) }
    val per = remember(volta, mudou) { personalizados(ctx) }
    val cals = remember(volta, mudou) { calendarios(ctx) }
    val z = ZoneId.systemDefault()
    val dias = remember(volta, mudou) {
        val hoje = LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli()
        // ponytail: janela fixa de 14 dias; paginar só se alguém tiver agenda lotada
        ocorrencias(ctx, hoje, hoje + 14 * 86_400_000L)
            .filter { it.diaInteiro || it.inicio >= System.currentTimeMillis() - 3_600_000 }
            .groupBy { zdt(inicioLocal(it, z)).toLocalDate() }
    }

    var nova by remember { mutableStateOf<Versao?>(null) }
    LaunchedEffect(Unit) { nova = withContext(Dispatchers.IO) { versaoNova(ctx) } }
    val podeInstalar = remember(volta) { ctx.packageManager.canRequestPackageInstalls() } // relê ao voltar dos Ajustes do Android

    var modoMes by rememberSaveable { mutableStateOf(false) }
    var mes by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var diaSel by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val doMes = remember(volta, mudou, mes, modoMes) {
        if (!modoMes) emptyMap()
        else ocorrencias(ctx, mes.atDay(1).atStartOfDay(z).toInstant().toEpochMilli(),
            mes.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli())
            .groupBy { zdt(inicioLocal(it, z)).toLocalDate() }
    }

    BackHandler(tela != Tela.Lista) { tela = Tela.Lista }
    // entrar desliza da direita, voltar desliza da esquerda: a pessoa sabe para onde foi
    AnimatedContent(tela, contentKey = { it::class }, label = "tela", transitionSpec = {
        val volta = targetState == Tela.Lista
        (slideInHorizontally(tween(320)) { if (volta) -it / 4 else it / 4 } + fadeIn(tween(320))) togetherWith
            (slideOutHorizontally(tween(320)) { if (volta) it / 4 else -it / 4 } + fadeOut(tween(200)))
    }) { t ->
        when (t) {
            Tela.Lista -> Lista(cals.isNotEmpty(), mudou, nova, podeInstalar, modoMes, { modoMes = it }, abrir = { tela = it }) {
                val avisosDe = { id: Long -> per[id] ?: pad }
                if (!modoMes) {
                    if (cals.isNotEmpty() && dias.isEmpty()) item { Vazio("Nada marcado nos próximos 14 dias") }
                    dias.forEach { (dia, ocs) ->
                        item(key = dia.toString()) { Folhinha(dia) }
                        items(ocs, key = { "${it.eventoId}-${it.inicio}" }) { oc ->
                            Linha(oc, avisosDe(oc.eventoId)) { tela = Tela.Detalhe(oc) }
                        }
                    }
                } else {
                    item(key = "mes") {
                        Mes(mes, diaSel, doMes.keys, mudarMes = { mes = it }) { diaSel = it }
                    }
                    item(key = "sel") { Folhinha(diaSel) }
                    val doDia = if (YearMonth.from(diaSel) == mes) doMes[diaSel].orEmpty() else emptyList()
                    if (doDia.isEmpty()) item { Vazio("Nada marcado neste dia") }
                    items(doDia, key = { "m-${it.eventoId}-${it.inicio}" }) { oc ->
                        Linha(oc, avisosDe(oc.eventoId)) { tela = Tela.Detalhe(oc) }
                    }
                }
            }
            is Tela.Detalhe -> Detalhe(t.oc, per[t.oc.eventoId] ?: pad, t.oc.eventoId in per, voltar = { tela = Tela.Lista },
                apagado = { mudou++; tela = Tela.Lista }, editar = { tela = Tela.Editar(t.oc) }) {
                salvarEscolha(ctx, t.oc.eventoId, it); mudou++
            }
            Tela.Ajustes -> Ajustes(pad, cals, voltar = { tela = Tela.Lista },
                usar = { c, u -> usarCalendario(ctx, c, u); mudou++ }) { salvarPadrao(ctx, it); mudou++ }
            Tela.Novo -> Novo(cals, pad, voltar = { tela = Tela.Lista }) { mudou++; tela = Tela.Lista }
            is Tela.Editar -> Novo(cals, pad, voltar = { tela = Tela.Detalhe(t.oc) }, editando = t.oc) { mudou++; tela = Tela.Lista }
        }
    }
}

@Composable
private fun Lista(temAgenda: Boolean, mudou: Int, nova: Versao?, podeInstalar: Boolean, modoMes: Boolean, mudarModo: (Boolean) -> Unit,
                  abrir: (Tela) -> Unit, corpo: LazyListScope.() -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SinoQueToca(32.dp, mudou)
                    Spacer(Modifier.width(10.dp))
                    Text("Tilim", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton({ abrir(Tela.Ajustes) }, Modifier.heightIn(min = 56.dp)) {
                        Text("Ajustes", style = MaterialTheme.typography.titleLarge, color = Terracota)
                    }
                }
            }
            if (nova != null) item {
                var baixando by remember { mutableStateOf(false) }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chegou uma versão nova do Tilim", style = MaterialTheme.typography.titleLarge)
                    val dica = when {
                        baixando -> "Baixando… Daqui a pouco aparece uma pergunta. Toque em \u201cInstalar\u201d (ou \u201cAtualizar\u201d). " +
                            "Se não aparecer, puxe a barra de cima da tela e toque em \u201cTilim\u201d."
                        // sem essa autorização o instalador do Android trava com uma mensagem assustadora; melhor explicar antes
                        !podeInstalar -> "Antes, o celular precisa deixar o Tilim se atualizar. Toque no botão, ligue a chave \u201cPermitir\u201d e volte aqui."
                        else -> "Leva um minutinho. Seus compromissos não se perdem."
                    }
                    Text(dica, style = MaterialTheme.typography.bodyLarge, color = Lapis)
                    if (!podeInstalar) Botao("Deixar o Tilim se atualizar") {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                    } else if (!baixando) Botao("Atualizar agora") { baixarEInstalar(ctx, nova); baixando = true }
                }
            }
            if (!temAgenda) item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Conecte sua agenda do Google", style = MaterialTheme.typography.headlineLarge)
                    Text("Entre com o e-mail e a senha do Google. Os compromissos aparecem aqui sozinhos.",
                        style = MaterialTheme.typography.bodyLarge, color = Lapis)
                    Spacer(Modifier.height(4.dp))
                    Botao("Entrar com a conta Google") { adicionarContaGoogle(ctx) }
                }
            } else {
                item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etiqueta("Lista", !modoMes, Modifier.weight(1f)) { mudarModo(false) }
                        Etiqueta("Mês", modoMes, Modifier.weight(1f)) { mudarModo(true) }
                    }
                }
                corpo()
            }
        }
        // sempre à vista: a ação principal não pode ficar escondida no fim da rolagem
        if (temAgenda) Column(Modifier.background(Creme)) {
            Picote()
            Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { Botao("Novo compromisso") { abrir(Tela.Novo) } }
        }
    }
}

@Composable
private fun Vazio(titulo: String) = Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(titulo, style = MaterialTheme.typography.titleLarge)
    Text("Use o botão abaixo para marcar um compromisso.", style = MaterialTheme.typography.bodyLarge, color = Lapis)
}

private val iniciais = listOf("D", "S", "T", "Q", "Q", "S", "S")
private val fmtMes = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)

/** Folha do mês, semana começando no domingo como nas folhinhas brasileiras. Ponto = tem compromisso. */
@Composable
private fun Mes(mes: YearMonth, sel: LocalDate, comCompromisso: Set<LocalDate>, mudarMes: (YearMonth) -> Unit, escolher: (LocalDate) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ mudarMes(mes.minusMonths(1)) }, Modifier.size(56.dp).semantics { contentDescription = "Mês anterior" }) {
                Text("‹", fontSize = 32.sp, color = Terracota)
            }
            Text(mes.format(fmtMes).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton({ mudarMes(mes.plusMonths(1)) }, Modifier.size(56.dp).semantics { contentDescription = "Próximo mês" }) {
                Text("›", fontSize = 32.sp, color = Terracota)
            }
        }
        Row {
            iniciais.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = Lapis) }
        }
        Spacer(Modifier.height(4.dp))
        // virar a folha: o mês novo entra pelo lado para onde a pessoa foi
        AnimatedContent(mes, label = "mes", transitionSpec = {
            val frente = targetState > initialState
            (slideInHorizontally(tween(280)) { if (frente) it / 3 else -it / 3 } + fadeIn(tween(280))) togetherWith
                (slideOutHorizontally(tween(280)) { if (frente) -it / 3 else it / 3 } + fadeOut(tween(160)))
        }) { m ->
            val vazios = m.atDay(1).dayOfWeek.value % 7 // domingo = 0
            val total = vazios + m.lengthOfMonth()
            Column {
                for (semana in 0 until (total + 6) / 7) Row {
                    for (col in 0..6) {
                        val n = semana * 7 + col - vazios + 1
                        if (n < 1 || n > m.lengthOfMonth()) Spacer(Modifier.weight(1f).height(60.dp))
                        else Dia(m.atDay(n), m.atDay(n) == sel, m.atDay(n) in comCompromisso, Modifier.weight(1f)) { escolher(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dia(d: LocalDate, escolhido: Boolean, marcado: Boolean, modifier: Modifier, escolher: (LocalDate) -> Unit) {
    val hoje = d == LocalDate.now()
    val fundo by animateColorAsState(if (escolhido) Terracota else Creme, tween(180), label = "dia")
    Box(modifier.height(60.dp).selectable(escolhido, role = Role.Button) { escolher(d) }
        .semantics { if (marcado) contentDescription = "${d.dayOfMonth}, tem compromisso" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).background(fundo, CircleShape)
                .then(if (hoje && !escolhido) Modifier.border(2.dp, Terracota, CircleShape) else Modifier),
                contentAlignment = Alignment.Center) {
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hoje || escolhido) FontWeight.Bold else FontWeight.Normal,
                    color = if (escolhido) Creme else Tinta)
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(7.dp).background(if (marcado) Terracota else Color.Transparent, CircleShape))
        }
    }
}

/** O sino da marca dá um "tilim" ao abrir e sempre que algo muda. */
@Composable
private fun SinoQueToca(tamanho: Dp, toque: Int) {
    var giro by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(toque) {
        animate(0f, 0f, animationSpec = keyframes {
            durationMillis = 900
            -18f at 110; 14f at 270; -9f at 430; 5f at 590; -2f at 740
        }) { v, _ -> giro = v }
    }
    Image(painterResource(R.drawable.ic_sino), null, Modifier.size(tamanho)
        .graphicsLayer { rotationZ = giro; transformOrigin = TransformOrigin(0.5f, 0.1f) },
        colorFilter = ColorFilter.tint(Terracota))
}

/** Picote da folhinha: cada dia é uma folha destacável. */
@Composable
private fun Picote() = Canvas(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(8.dp)) {
    val passo = 11.dp.toPx()
    var x = passo / 2
    while (x < size.width) { drawCircle(Fio, 2.2.dp.toPx(), Offset(x, size.height / 2)); x += passo }
}

/** Cabeçalho do dia no estilo folhinha: o número é o elemento marcante do app. */
@Composable
private fun Folhinha(dia: LocalDate) {
    val hoje = LocalDate.now()
    // "Quarta", não "Quarta-feira": cabe na coluna ao lado do número mesmo com fonte grande
    val semana = dia.format(DateTimeFormatter.ofPattern("EEEE", ptBR)).replace("-feira", "").replaceFirstChar { it.uppercase() }
    val mes = dia.format(DateTimeFormatter.ofPattern("MMMM", ptBR))
    val (linha1, linha2) = when (dia) {
        hoje -> "Hoje" to "$semana, $mes"
        hoje.plusDays(1) -> "Amanhã" to "$semana, $mes"
        else -> semana to mes
    }
    Column(Modifier.padding(top = 20.dp)) {
        Picote()
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${dia.dayOfMonth}", style = MaterialTheme.typography.displayLarge, color = Terracota,
                maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 108.dp).padding(end = 12.dp))
            Column {
                Text(linha1, style = MaterialTheme.typography.titleLarge)
                Text(linha2, style = MaterialTheme.typography.bodyMedium, color = Lapis)
            }
        }
    }
}

@Composable
private fun Linha(oc: Ocorrencia, avisos: Set<Aviso>, abrir: () -> Unit) {
    val ativo = avisos.any { it in avisosPermitidos(oc) }
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Escolher avisos", onClick = abrir)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (oc.diaInteiro) "Dia todo" else hora(oc.inicio), fontFamily = Lexend, fontWeight = FontWeight.Bold,
                fontSize = if (oc.diaInteiro) 18.sp else 24.sp, softWrap = false,
                modifier = Modifier.widthIn(min = 108.dp).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(oc.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (oc.local.isNotBlank()) Text(oc.local, style = MaterialTheme.typography.bodyMedium, color = Lapis,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // quieto de propósito: o destaque da lista é o número do dia
            if (ativo) Box(Modifier.padding(start = 12.dp).semantics { contentDescription = "Vai tocar" }) { Sino(28.dp) }
            else Text("Sem\naviso", style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(start = 12.dp))
        }
        HorizontalDivider(Modifier.padding(start = 132.dp, end = 24.dp), color = Fio)
    }
}

@Composable
private fun Voltar(voltar: () -> Unit) = TextButton(voltar, Modifier.padding(start = 8.dp, top = 8.dp).heightIn(min = 56.dp)) {
    Text("‹  Voltar", style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Detalhe(oc: Ocorrencia, avisos: Set<Aviso>, personalizado: Boolean, voltar: () -> Unit, apagado: () -> Unit,
                    editar: () -> Unit, mudar: (Set<Aviso>?) -> Unit) {
    val ctx = LocalContext.current
    val z = ZoneId.systemDefault()
    val ini = inicioLocal(oc, z)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Voltar(voltar)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(oc.titulo, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(diaPorExtenso(ini) + if (oc.diaInteiro) ", o dia todo" else ", às ${hora(oc.inicio)}",
                style = MaterialTheme.typography.bodyLarge)
            if (oc.local.isNotBlank()) Text(oc.local, style = MaterialTheme.typography.bodyLarge, color = Lapis)
            if (oc.agenda.isNotBlank()) Text("Agenda: ${oc.agenda}", style = MaterialTheme.typography.bodyMedium, color = Lapis)

            Spacer(Modifier.height(36.dp))
            Text("Quando o Tilim deve tocar?", style = MaterialTheme.typography.titleLarge)
            if (oc.diaInteiro) Text("Este compromisso está sem horário na agenda (dia todo), por isso não dá para avisar " +
                "1 hora antes. Para isso, coloque um horário nele no Google Agenda.",
                style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
        }
        avisosPermitidos(oc).forEach { a ->
            Marcar(a.rotulo, a in avisos) { mudar(if (it) avisos + a else avisos - a) }
        }

        Column(Modifier.padding(horizontal = 24.dp).animateContentSize()) {
            val agora = System.currentTimeMillis()
            val vai = avisos.filter { it in avisosPermitidos(oc) }.map { quando(it, oc, z) }.filter { it > agora }.sorted()
            Spacer(Modifier.height(24.dp))
            if (vai.isNotEmpty()) {
                Text("Vai tocar", style = MaterialTheme.typography.titleLarge, color = Terracota)
                vai.forEach { Text("${diaPorExtenso(it)}, às ${hora(it)}", style = MaterialTheme.typography.bodyLarge) }
            } else {
                Text("Este compromisso não vai tocar.", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            if (personalizado) {
                Text("Diferente do padrão. Vale também quando este compromisso se repete.",
                    style = MaterialTheme.typography.bodyMedium, color = Lapis)
                TextButton({ mudar(null) }, Modifier.heightIn(min = 56.dp)) {
                    Text("Usar o padrão dos Ajustes", style = MaterialTheme.typography.labelLarge, color = Terracota)
                }
            } else {
                Text("Seguindo o padrão dos Ajustes.", style = MaterialTheme.typography.bodyMedium, color = Lapis)
            }
            Spacer(Modifier.height(32.dp))
            BotaoContorno("Ouvir como vai tocar") { Toque.tocar(ctx, oc.titulo, oc.inicio, oc.local) }
            Mexer(oc, editar, apagado)
        }
    }
}

/** Apagar sempre pergunta antes: um toque errado não pode sumir com a consulta. */
@Composable
private fun Mexer(oc: Ocorrencia, editar: () -> Unit, apagado: () -> Unit) {
    val ctx = LocalContext.current
    val repete = remember(oc.eventoId) { podeApagar(ctx, oc.eventoId) } ?: return // agenda só de leitura: sem botão
    var perguntar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf(false) }
    var soEste by remember { mutableStateOf(true) }
    fun apagar() { if (apagarCompromisso(ctx, oc, soEste)) apagado() else erro = true }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) apagar() else erro = true }
    fun pedir(este: Boolean) {
        soEste = este; perguntar = false
        if (tem(ctx, Manifest.permission.WRITE_CALENDAR)) apagar() else permissao.launch(Manifest.permission.WRITE_CALENDAR)
    }

    Spacer(Modifier.height(16.dp))
    // dia todo: o jeito de ganhar "1 hora antes" é colocar horário, então o botão diz isso
    BotaoContorno(if (oc.diaInteiro) "Colocar horário" else "Mudar horário ou nome", editar)
    Spacer(Modifier.height(32.dp))
    TextButton({ perguntar = true }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text("Apagar compromisso", style = MaterialTheme.typography.labelLarge, color = Terracota)
    }
    if (erro) Text("Não deu para apagar. Tente pelo Google Agenda.", style = MaterialTheme.typography.bodyLarge,
        color = Terracota, fontWeight = FontWeight.Bold)

    if (perguntar) AlertDialog(onDismissRequest = { perguntar = false }, containerColor = Creme,
        title = { Text("Apagar \u201c${oc.titulo}\u201d?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(if (repete) "Este compromisso se repete. Apagar só o de ${diaPorExtenso(inicioLocal(oc, ZoneId.systemDefault())).lowercase()} ou todos?"
                else "Ele some do Tilim e do Google Agenda.", style = MaterialTheme.typography.bodyLarge)
        },
        // botões empilhados e largos: "Não apagar" fica em último, longe do dedo que foi para "Apagar"
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repete) {
                    Botao("Só este dia") { pedir(true) }
                    BotaoContorno("Todos") { pedir(false) }
                } else Botao("Apagar") { pedir(false) }
                TextButton({ perguntar = false }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Não apagar", style = MaterialTheme.typography.labelLarge, color = Tinta)
                }
            }
        })
}

@Composable
private fun Ajustes(pad: Set<Aviso>, cals: List<Calendario>, voltar: () -> Unit,
                    usar: (Calendario, Boolean) -> Unit, mudar: (Set<Aviso>) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Voltar(voltar)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Avisos de sempre", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Todo compromisso do Google Agenda toca nestes horários. Para mudar só um, toque nele na lista.",
                style = MaterialTheme.typography.bodyLarge, color = Lapis)
            Spacer(Modifier.height(16.dp))
        }
        (Aviso.entries - Aviso.MANHA).forEach { a -> Marcar(a.rotulo, a in pad) { mudar(if (it) pad + a else pad - a) } }
        Text("Compromisso de dia todo (sem horário) também toca:",
            style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp))
        Marcar(Aviso.MANHA.rotulo, Aviso.MANHA in pad) { mudar(if (it) pad + Aviso.MANHA else pad - Aviso.MANHA) }
        Text("O \"1 dia antes\" toca às ${HORA_VESPERA}h da véspera.",
            style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(24.dp))

        Picote()
        EscolherSom()

        Picote()
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
            Text("Agendas", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Marque de quais agendas o Tilim deve avisar.", style = MaterialTheme.typography.bodyLarge, color = Lapis)
        }
        // a mesma agenda compartilhada aparece em cada conta: mostra uma vez só
        cals.distinctBy { it.dono }.groupBy { it.conta }.forEach { (conta, lista) ->
            Text(conta, style = MaterialTheme.typography.bodyMedium, color = Lapis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp))
            lista.forEach { c -> Marcar(c.nome, c.usado) { usar(c, it) } }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoContorno("Adicionar outra conta Google") { adicionarContaGoogle(ctx) }
            Text("Os compromissos da conta nova aparecem em alguns minutos.",
                style = MaterialTheme.typography.bodyMedium, color = Lapis)
        }

        val widgets = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
        if (widgets.isRequestPinAppWidgetSupported) {
            Picote()
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Na tela inicial", style = MaterialTheme.typography.headlineLarge)
                Text("Um quadro com o próximo compromisso, sem precisar abrir o Tilim.",
                    style = MaterialTheme.typography.bodyLarge, color = Lapis)
                Spacer(Modifier.height(4.dp))
                BotaoContorno("Colocar na tela inicial") {
                    widgets.requestPinAppWidget(android.content.ComponentName(ctx, Widget::class.java), null, null)
                }
            }
        }
    }
}

/** Avisa se a voz em português ainda não está no aparelho: sem ela, sem internet, o alarme só toca. */
@Composable
private fun EstadoDaVoz() {
    val ctx = LocalContext.current
    var pronta by remember { mutableStateOf<Boolean?>(null) } // null = conferindo
    var vez by remember { mutableIntStateOf(0) }
    LaunchedEffect(vez) { checarVoz(ctx) { ok -> pronta = ok } }
    val baixar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vez++ } // voltou: confere de novo
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (pronta) {
            true -> Text("Voz em português pronta. Funciona sem internet.", style = MaterialTheme.typography.bodyMedium, color = Lapis)
            false -> {
                Text("A voz em português ainda não está no celular. Com internet ligada, toque em \u201cBaixar a voz\u201d.",
                    style = MaterialTheme.typography.bodyLarge, color = Terracota, fontWeight = FontWeight.Bold)
                BotaoContorno("Baixar a voz") {
                    runCatching { baixar.launch(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }
            }
            null -> {}
        }
    }
}

/** Som do alarme: toques do Tilim, o do celular ou qualquer outro pelo seletor do Android. Tocar = ouvir um pouco. */
@Composable
private fun EscolherSom() {
    val ctx = LocalContext.current
    var atual by remember { mutableStateOf(som(ctx)) }
    val previa = remember { arrayOfNulls<MediaPlayer>(1) }
    var vez by remember { mutableIntStateOf(0) }
    fun ouvir(s: String?) {
        previa[0]?.release(); vez++
        val uri = s?.let { uriDoSom(ctx, it) } ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
        previa[0] = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(ctx, uri); isLooping = true; prepare(); start()
            }
        }.getOrNull()
    }
    fun escolher(s: String?) { atual = s; salvarSom(ctx, s); ouvir(s) }
    DisposableEffect(Unit) { onDispose { previa[0]?.release() } }
    // ponytail: prévia para em 4 s ou ao sair da tela; botão "parar" se acharem pouco
    LaunchedEffect(vez) { kotlinx.coroutines.delay(4000); previa[0]?.release(); previa[0] = null }
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION")
        r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { escolher(it.toString()) }
    }
    val doCelular = atual?.takeIf { ':' in it }
    var voz by remember { mutableStateOf(falar(ctx)) }
    Spacer(Modifier.height(28.dp))
    Text("Voz", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 24.dp))
    Marcar("Falar o compromisso em voz alta", voz) { voz = it; salvarFalar(ctx, it) }
    Text("O alarme para de tempo em tempo e diz o que é e a que horas.", style = MaterialTheme.typography.bodyMedium,
        color = Lapis, modifier = Modifier.padding(horizontal = 24.dp))
    if (voz) EstadoDaVoz()
    Spacer(Modifier.height(28.dp))
    Picote()
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)) {
        Text("Som do alarme", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Toque num som para ouvir.", style = MaterialTheme.typography.bodyLarge, color = Lapis)
        Spacer(Modifier.height(8.dp))
        for ((grupo, sons) in listOf("Fortes, para não perder" to SONS_FORTES, "Suaves" to SONS_SUAVES)) {
            Text(grupo, style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(top = 16.dp))
            sons.forEach { (id, nome) -> Opcao(nome, nome, atual == id) { escolher(id) } }
        }
        Text("Do celular", style = MaterialTheme.typography.bodyMedium, color = Lapis, modifier = Modifier.padding(top = 16.dp))
        Opcao("Alarme do celular", "Alarme do celular", atual == null) { escolher(null) }
        if (doCelular != null) Opcao(RingtoneManager.getRingtone(ctx, Uri.parse(doCelular))?.getTitle(ctx) ?: "Toque do celular",
            "Toque do celular", true) { ouvir(doCelular) }
        Spacer(Modifier.height(12.dp))
        BotaoContorno("Outros toques do celular") {
            seletor.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, doCelular?.let(Uri::parse)))
        }
    }
}

private fun descrever(avisos: Set<Aviso>) = (Aviso.entries - Aviso.MANHA).filter { it in avisos }.joinToString(" e ") { it.rotulo }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Novo(cals: List<Calendario>, pad: Set<Aviso>, voltar: () -> Unit, editando: Ocorrencia? = null, salvo: () -> Unit) {
    val ctx = LocalContext.current
    // editando: mesma tela, já preenchida
    val atual = remember(editando) { editando?.let { lerCompromisso(ctx, it.eventoId) } }
    val base = remember {
        if (editando == null) LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        else zdt(inicioLocal(editando, ZoneId.systemDefault())).toLocalDateTime()
            .let { if (editando.diaInteiro) it.withHour(9) else it } // dia todo ainda não tem hora: sugere 9h
    }
    var titulo by rememberSaveable { mutableStateOf(atual?.titulo.orEmpty()) }
    var local by rememberSaveable { mutableStateOf(atual?.local.orEmpty()) }
    var dia by rememberSaveable { mutableStateOf(base.toLocalDate()) }
    var horario by rememberSaveable { mutableStateOf(base.toLocalTime()) }
    // regra de repetição como texto: guarda também uma regra do Google que não é nenhuma das nossas ("seg e qua")
    var regra by rememberSaveable { mutableStateOf(atual?.rrule) }
    val opcoes = Repete.entries.map { it.rotulo to it.rrule } +
        listOfNotNull(atual?.rrule?.takeIf { r -> Repete.entries.none { it.rrule == r } }?.let { "Como está" to it })
    // série que continua série: só a hora muda; o dia de início fica
    val serie = atual?.rrule != null && regra != null
    // só agendas onde dá para escrever; a principal da conta (dono = e-mail da conta) vem primeiro
    val graváveis = remember(cals) { cals.filter { it.podeEscrever && it.usado }.distinctBy { it.dono }.sortedBy { it.dono != it.conta } }
    var agenda by rememberSaveable { mutableStateOf(graváveis.firstOrNull()?.dono) }
    var erro by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var escolherDia by remember { mutableStateOf(false) }
    var escolherHora by remember { mutableStateOf(false) }

    fun salvar() {
        val inicio = dia.atTime(horario).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val cal = graváveis.find { it.dono == agenda }
        erro = when {
            titulo.isBlank() -> "Escreva o que é o compromisso."
            // repetido: o primeiro dia pode já ter passado sem problema, os próximos tocam
            regra == null && inicio <= System.currentTimeMillis() -> "Esse horário já passou. Escolha outro."
            editando != null -> if (editarCompromisso(ctx, editando.eventoId, titulo.trim(), local.trim(), inicio, regra)) null
                else "Não consegui salvar. Tente de novo."
            cal == null -> "Escolha uma agenda."
            !criarCompromisso(ctx, cal, titulo.trim(), inicio, local.trim(), regra) -> "Não consegui salvar. Tente de novo."
            else -> null
        }
        ok = erro == null
    }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) salvar()
        else erro = "O Tilim precisa de permissão para escrever na agenda." }

    if (ok) { Salvo(if (editando == null) descrever(pad) else "", salvo); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Voltar(voltar)
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editando == null) "Novo compromisso" else "Mudar compromisso", style = MaterialTheme.typography.headlineLarge)
            if (editando != null && atual == null) {
                Text("Não consegui abrir este compromisso. Tente pelo Google Agenda.",
                    style = MaterialTheme.typography.bodyLarge, color = Lapis)
                return@Column
            }
            if (editando == null && graváveis.isEmpty()) {
                Text("Nenhuma agenda deste celular aceita compromissos novos. Adicione sua conta Google nos Ajustes.",
                    style = MaterialTheme.typography.bodyLarge, color = Lapis)
                return@Column
            }

            Rotulo("O que é?")
            OutlinedTextField(titulo, { titulo = it; erro = null }, Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Ex.: Consulta no cardiologista", style = MaterialTheme.typography.bodyLarge, color = Lapis) },
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Médico", "Dentista", "Exame", "Remédio").forEach { s ->
                    Etiqueta(s, titulo == s) { titulo = s; erro = null }
                }
            }

            Rotulo("Repetir?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                opcoes.forEach { (rotulo, r) -> Etiqueta(rotulo, regra == r) { regra = r } }
            }

            if (serie) {
                Text("A hora nova vale para todos os dias em que ele se repete.",
                    style = MaterialTheme.typography.bodyLarge, color = Lapis)
            } else {
                Rotulo("Que dia?")
                val hoje = LocalDate.now()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etiqueta("Hoje", dia == hoje, Modifier.weight(1f)) { dia = hoje }
                    Etiqueta("Amanhã", dia == hoje.plusDays(1), Modifier.weight(1f)) { dia = hoje.plusDays(1) }
                }
                Etiqueta("Escolher outro dia", dia > hoje.plusDays(1), Modifier.fillMaxWidth()) { escolherDia = true }
                Text(dia.format(fmtDia).replace("-feira", "").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge)
            }

            Rotulo("Que horas?")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(horario.format(fmtHora), fontFamily = Lexend, fontWeight = FontWeight.Bold, fontSize = 48.sp,
                    color = Terracota, modifier = Modifier.weight(1f).clickable(onClickLabel = "Mudar horário") { escolherHora = true })
                OutlinedButton({ escolherHora = true }, Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, Terracota), colors = ButtonDefaults.outlinedButtonColors(contentColor = Terracota)) {
                    Text("Mudar")
                }
            }

            Rotulo("Onde? (se quiser)")
            OutlinedTextField(local, { local = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Ex.: Clínica São Lucas", style = MaterialTheme.typography.bodyLarge, color = Lapis) },
                singleLine = true, keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences))

            if (editando == null && graváveis.size > 1) {
                Rotulo("Em qual agenda?")
                graváveis.forEach { c -> Opcao(c.nome, c.conta, agenda == c.dono) { agenda = c.dono } }
            }

            if (editando == null) Text(if (descrever(pad).isEmpty()) "Não vai tocar: os avisos de sempre estão desligados nos Ajustes."
                else "Vai tocar ${descrever(pad)}. Dá para mudar depois.",
                style = MaterialTheme.typography.bodyMedium, color = Lapis)
            AnimatedVisibility(erro != null) {
                Text(erro.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Terracota, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Botao(if (editando == null) "Salvar compromisso" else "Salvar mudanças") {
                if (tem(ctx, Manifest.permission.WRITE_CALENDAR)) salvar()
                else permissao.launch(Manifest.permission.WRITE_CALENDAR)
            }
        }
    }

    if (escolherDia) {
        val utc = { d: LocalDate -> d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
        val estado = rememberDatePickerState(initialSelectedDateMillis = utc(dia), selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= utc(LocalDate.now())
        })
        DatePickerDialog(onDismissRequest = { escolherDia = false }, confirmButton = {
            TextButton({
                estado.selectedDateMillis?.let { dia = Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                escolherDia = false
            }) { Text("Pronto") }
        }, dismissButton = { TextButton({ escolherDia = false }) { Text("Cancelar") } }) {
            DatePicker(estado, showModeToggle = false)
        }
    }
    if (escolherHora) {
        val estado = rememberTimePickerState(horario.hour, horario.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { escolherHora = false }, containerColor = Creme,
            confirmButton = { TextButton({ horario = LocalTime.of(estado.hour, estado.minute); escolherHora = false }) { Text("Pronto") } },
            dismissButton = { TextButton({ escolherHora = false }) { Text("Cancelar") } },
            text = { TimePicker(estado) })
    }
}

@Composable
private fun Rotulo(texto: String) = Text(texto, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))

/** Escolha rápida em forma de etiqueta de papel: preenchida quando escolhida. */
@Composable
private fun Etiqueta(texto: String, escolhida: Boolean, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    val fundo by animateColorAsState(if (escolhida) Terracota else Creme, label = "etiqueta")
    Box(modifier.heightIn(min = 56.dp).background(fundo, RoundedCornerShape(12.dp))
        .border(2.dp, if (escolhida) Terracota else Fio, RoundedCornerShape(12.dp))
        .selectable(escolhida, role = Role.RadioButton, onClick = aoTocar)
        .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = if (escolhida) Creme else Tinta,
            fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Opcao(texto: String, detalhe: String, escolhida: Boolean, aoTocar: () -> Unit) {
    val miolo by animateDpAsState(if (escolhida) 14.dp else 0.dp, label = "radio")
    Row(Modifier.fillMaxWidth().selectable(escolhida, role = Role.RadioButton, onClick = aoTocar).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).border(3.dp, Terracota, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(miolo).background(Terracota, CircleShape))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge, fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Normal)
            if (detalhe != texto) Text(detalhe, style = MaterialTheme.typography.bodyMedium, color = Lapis)
        }
    }
}

/** Confirmação: o tique se desenha dentro do círculo e volta sozinho para a lista. */
@Composable
private fun Salvo(avisos: String, fim: () -> Unit) {
    val traço = remember { Animatable(0f) }
    val escala = remember { Animatable(0.6f) }
    val haptico = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptico.performHapticFeedback(HapticFeedbackType.Confirm)
        escala.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        traço.animateTo(1f, tween(380))
        delay(1300)
        fim()
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp).graphicsLayer { scaleX = escala.value; scaleY = escala.value }
            .background(Terracota, CircleShape), contentAlignment = Alignment.Center) {
            Tique(Creme, 72.dp, traço.value)
        }
        Spacer(Modifier.height(28.dp))
        Text("Compromisso salvo", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        if (avisos.isNotEmpty()) Text("O Tilim vai tocar $avisos.", style = MaterialTheme.typography.bodyLarge,
            color = Lapis, textAlign = TextAlign.Center)
    }
}

/** Quadradinho de marcar, como num formulário de papel: estado óbvio, área de toque da linha inteira. */
@Composable
private fun Marcar(texto: String, marcado: Boolean, mudar: (Boolean) -> Unit) {
    val haptico = LocalHapticFeedback.current
    val fundo by animateColorAsState(if (marcado) Terracota else Creme, tween(180), label = "caixa")
    val traço by animateFloatAsState(if (marcado) 1f else 0f, tween(if (marcado) 260 else 120), label = "tique")
    Row(Modifier.fillMaxWidth().toggleable(marcado, role = Role.Checkbox) {
        haptico.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        mudar(it)
    }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(fundo, RoundedCornerShape(10.dp))
            .border(3.dp, Terracota, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (traço > 0f) Tique(Creme, 26.dp, traço)
        }
        Spacer(Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, fontWeight = if (marcado) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Tela oficial de login do Google no Android: sem senha passando pelo Tilim, sem projeto OAuth. */
private fun adicionarContaGoogle(ctx: Context) = ctx.startActivity(
    Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(CONTA_GOOGLE)))

@Composable
private fun Botao(texto: String, acao: () -> Unit) =
    Button(acao, Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(14.dp)) { Text(texto) }

@Composable
private fun BotaoContorno(texto: String, acao: () -> Unit) =
    OutlinedButton(acao, Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, Terracota),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Terracota)) { Text(texto) }

@Composable
private fun Sino(tamanho: Dp) = Image(painterResource(R.drawable.ic_sino), null,
    Modifier.size(tamanho), colorFilter = ColorFilter.tint(Terracota))

/** Tique desenhado (o glifo ✓ cai numa fonte de sistema com traço de pincel). progresso desenha o traço aos poucos. */
@Composable
fun Tique(cor: Color, tamanho: Dp, progresso: Float = 1f) = Canvas(Modifier.size(tamanho)) {
    val w = size.width
    val inteiro = Path().apply { moveTo(w * .14f, w * .54f); lineTo(w * .40f, w * .78f); lineTo(w * .86f, w * .24f) }
    val medida = PathMeasure().apply { setPath(inteiro, false) }
    val parte = Path().also { medida.getSegment(0f, medida.length * progresso, it, true) }
    drawPath(parte, cor, style = Stroke(w * .14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
