package com.clevs.tilim

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Events as E
import java.time.ZoneId

/*
 * Arquitetura inspirada no Calendula (codeberg.org/jlmakiola/calendula):
 * uma única varredura idempotente (reagendar) + um único alarme exato para o próximo gatilho.
 * A varredura roda quando: o alarme dispara, o app abre, o usuário muda um aviso,
 * o celular reinicia / muda a hora, ou o Google Agenda muda (CalendarioJob).
 */

private const val DIA = 86_400_000L

/**
 * dono = ownerAccount: identifica a agenda em si. A mesma agenda compartilhada aparece uma vez por conta
 * do celular (ids diferentes, mesmo dono) — a escolha "usar" vale para todas as cópias.
 */
data class Calendario(val id: Long, val nome: String, val conta: String, val tipo: String, val dono: String,
                      val usado: Boolean, val podeEscrever: Boolean)

/** Feriados, aniversários e afins do Google: agendas automáticas, não compromissos da pessoa. */
private fun automatica(dono: String) = dono.endsWith("group.v.calendar.google.com")

/**
 * Alguns fabricantes gravam a chave de tradução em vez do nome ("calendar_displayname_local").
 * Nome sem espaço e com "_" é chave, não nome de gente.
 */
fun nomeDaAgenda(nome: String?): String =
    if (nome.isNullOrBlank() || Regex("[a-z0-9]+(_[a-z0-9]+)+").matches(nome)) "Agenda do celular" else nome

/** Todas as agendas sincronizadas no aparelho, de todas as contas. */
fun calendarios(ctx: Context): List<Calendario> {
    val p = prefs(ctx)
    val lista = mutableListOf<Calendario>()
    try {
        ctx.contentResolver.query(Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
                Calendars.OWNER_ACCOUNT, Calendars.CALENDAR_ACCESS_LEVEL),
            "${Calendars.SYNC_EVENTS} = 1", null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dono = c.getString(4)?.ifBlank { null } ?: "id:$id"
                // "cal:<id>" é a chave antiga (por cópia): respeitada até a pessoa mexer de novo
                val usado = p.getBoolean("cal:$dono", p.getBoolean("cal:$id", !automatica(dono)))
                val tipo = c.getString(3).orEmpty()
                val conta = if (tipo == CalendarContract.ACCOUNT_TYPE_LOCAL) "Neste celular" else c.getString(2).orEmpty()
                lista += Calendario(id, nomeDaAgenda(c.getString(1)), conta, tipo,
                    dono, usado, c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR)
            }
        }
    } catch (_: SecurityException) { }
    return lista
}

fun usarCalendario(ctx: Context, cal: Calendario, usar: Boolean) {
    val copias = calendarios(ctx).filter { it.dono == cal.dono }
    prefs(ctx).edit().apply {
        putBoolean("cal:${cal.dono}", usar)
        copias.forEach { remove("cal:${it.id}") }
    }.apply()
    reagendar(ctx)
}

/** Grava no provedor do Android; a sincronização leva para o Google Agenda. */
fun criarCompromisso(ctx: Context, cal: Calendario, titulo: String, inicio: Long, local: String, rrule: String?): Boolean = try {
    ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, android.content.ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, cal.id)
        put(CalendarContract.Events.TITLE, titulo)
        put(CalendarContract.Events.EVENT_LOCATION, local)
        put(CalendarContract.Events.DTSTART, inicio)
        // ponytail: 1 hora fixa; duração não importa para o alarme. Repetição exige DURATION no lugar de DTEND.
        if (rrule == null) put(CalendarContract.Events.DTEND, inicio + 3_600_000)
        else { put(CalendarContract.Events.RRULE, rrule); put(CalendarContract.Events.DURATION, "PT1H") }
        put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
    }) != null
} catch (_: Exception) { false } finally {
    reagendar(ctx)
    sincronizar(ctx)
}

/** null = não dá para apagar (agenda só de leitura, ex.: feriados); senão, se o compromisso se repete. */
fun podeApagar(ctx: Context, eventoId: Long): Boolean? = try {
    ctx.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventoId),
        arrayOf(CalendarContract.Events.CALENDAR_ACCESS_LEVEL, CalendarContract.Events.RRULE, CalendarContract.Events.RDATE),
        null, null, null)?.use { c ->
        if (!c.moveToFirst() || c.getInt(0) < Calendars.CAL_ACCESS_CONTRIBUTOR) null
        else !c.getString(1).isNullOrBlank() || !c.getString(2).isNullOrBlank()
    }
} catch (_: SecurityException) { null }

/**
 * Apaga no provedor do Android; a sincronização leva para o Google Agenda.
 * soEste: num compromisso que se repete, cancela só esta ocorrência (exceção), como o Google Agenda faz.
 */
fun apagarCompromisso(ctx: Context, oc: Ocorrencia, soEste: Boolean): Boolean = try {
    val r = ctx.contentResolver
    val uri = ContentUris.withAppendedId(E.CONTENT_URI, oc.eventoId)
    if (!soEste) (r.delete(uri, null, null) > 0).also { if (it) prefs(ctx).edit().remove("ev:${oc.eventoId}").apply() }
    else {
        // regra completa da série: num update, o provedor recalcula as repetições só com o que vier nele
        val campos = arrayOf(E.DTSTART, E.DURATION, E.RRULE, E.RDATE, E.EVENT_TIMEZONE, E.EXDATE)
        val (syncId, serie) = r.query(uri, arrayOf(E._SYNC_ID) + campos, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else c.getString(0) to android.content.ContentValues().apply {
                put(E.DTSTART, c.getLong(1))
                for (i in 1 until campos.size) put(campos[i], c.getString(i + 1))
            }
        } ?: return false
        if (syncId != null) r.insert(ContentUris.withAppendedId(E.CONTENT_EXCEPTION_URI, oc.eventoId),
            android.content.ContentValues().apply {
                put(E.ORIGINAL_INSTANCE_TIME, oc.inicio) // BEGIN cru da ocorrência
                put(E.STATUS, E.STATUS_CANCELED)
            }) != null
        // agenda local (sem _SYNC_ID): exceção some com a série inteira; tira o dia da regra (EXDATE).
        // Update parcial também perde a série (só sobra a 1ª ocorrência), por isso regrava a regra inteira.
        else r.update(uri, serie.apply {
            put(E.EXDATE, listOfNotNull(getAsString(E.EXDATE)?.ifBlank { null }, exdateUtc(oc.inicio)).joinToString(","))
        }, null, null) > 0
    }
} catch (_: Exception) { false } finally {
    reagendar(ctx)
    sincronizar(ctx)
}

/** O que dá para mudar num compromisso. rrule: regra de repetição como está na agenda (null = não repete). */
data class Editavel(val titulo: String, val local: String, val diaInteiro: Boolean, val rrule: String?)

fun lerCompromisso(ctx: Context, eventoId: Long): Editavel? = try {
    ctx.contentResolver.query(ContentUris.withAppendedId(E.CONTENT_URI, eventoId),
        arrayOf(E.TITLE, E.EVENT_LOCATION, E.ALL_DAY, E.RRULE), null, null, null)?.use { c ->
        if (!c.moveToFirst()) null
        else Editavel(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getInt(2) == 1, c.getString(3)?.ifBlank { null })
    }
} catch (_: SecurityException) { null }

/**
 * Muda nome, local, horário e repetição. novoInicio: dia e hora escolhidos.
 * Série que continua série: vale só a hora (a série segue começando no mesmo dia) e os dias apagados vão junto.
 * Dia todo vira compromisso com horário.
 */
fun editarCompromisso(ctx: Context, eventoId: Long, titulo: String, local: String, novoInicio: Long, rrule: String?): Boolean = try {
    val r = ctx.contentResolver
    val uri = ContentUris.withAppendedId(E.CONTENT_URI, eventoId)
    val zona = ZoneId.systemDefault()
    val hora = java.time.Instant.ofEpochMilli(novoInicio).atZone(zona).toLocalTime()
    var serieContinua = false
    val v = r.query(uri, arrayOf(E.DTSTART, E.DTEND, E.DURATION, E.ALL_DAY, E.RRULE, E.RDATE, E.EXDATE), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return false
        val ini = c.getLong(0)
        val diaInteiro = c.getInt(3) == 1
        val eraSerie = !c.getString(4).isNullOrBlank() || !c.getString(5).isNullOrBlank()
        serieContinua = eraSerie && rrule != null
        val dur = if (diaInteiro) 3_600_000L
            else duracaoMs(c.getString(2)) ?: (if (c.isNull(1)) null else c.getLong(1) - ini)?.takeIf { it > 0 } ?: 3_600_000L
        // dia todo vem como meia-noite UTC; o resto, no fuso do aparelho
        val diaSerie = java.time.Instant.ofEpochMilli(ini).atZone(if (diaInteiro) java.time.ZoneOffset.UTC else zona).toLocalDate()
        val inicio = if (serieContinua) diaSerie.atTime(hora).atZone(zona).toInstant().toEpochMilli() else novoInicio
        android.content.ContentValues().apply {
            put(E.TITLE, titulo); put(E.EVENT_LOCATION, local)
            put(E.DTSTART, inicio); put(E.ALL_DAY, 0); put(E.EVENT_TIMEZONE, zona.id)
            if (rrule != null) {
                // update parcial faz o provedor perder a série: sempre regrava a regra inteira
                put(E.RRULE, rrule); put(E.DURATION, "PT${dur / 60_000}M"); putNull(E.DTEND)
                put(E.RDATE, if (serieContinua) c.getString(5) else null)
                put(E.EXDATE, if (serieContinua) moverExdate(c.getString(6), hora, zona) else null)
            } else {
                putNull(E.RRULE); putNull(E.RDATE); putNull(E.EXDATE); putNull(E.DURATION)
                put(E.DTEND, inicio + dur)
            }
        }
    } ?: return false
    // exceções antes: o provedor recalcula as ocorrências no update da série e precisa já achá-las no horário novo
    acertarExcecoes(ctx, eventoId, if (serieContinua) hora else null)
    r.update(uri, v, null, null) > 0
} catch (_: Exception) { false } finally {
    reagendar(ctx)
    sincronizar(ctx)
}

/**
 * Exceções do Google (dias apagados de uma série sincronizada) apontam para o horário antigo.
 * hora != null: série mudou de hora, leva cada exceção para a hora nova. null: deixou de ser série, apaga.
 */
private fun acertarExcecoes(ctx: Context, eventoId: Long, hora: java.time.LocalTime?) {
    val r = ctx.contentResolver
    val zona = ZoneId.systemDefault()
    r.query(E.CONTENT_URI, arrayOf(E._ID, E.ORIGINAL_INSTANCE_TIME, E.ORIGINAL_ALL_DAY), "${E.ORIGINAL_ID} = ?",
        arrayOf(eventoId.toString()), null)?.use { c ->
        while (c.moveToNext()) {
            val ex = ContentUris.withAppendedId(E.CONTENT_URI, c.getLong(0))
            if (hora == null) { r.delete(ex, null, null); continue }
            val dia = java.time.Instant.ofEpochMilli(c.getLong(1))
                .atZone(if (c.getInt(2) == 1) java.time.ZoneOffset.UTC else zona).toLocalDate()
            r.update(ex, android.content.ContentValues().apply {
                put(E.ORIGINAL_INSTANCE_TIME, dia.atTime(hora).atZone(zona).toInstant().toEpochMilli())
                put(E.ORIGINAL_ALL_DAY, 0)
            }, null, null)
        }
    }
}

fun falar(ctx: Context) = prefs(ctx).getBoolean("falar", true)
fun salvarFalar(ctx: Context, sim: Boolean) = prefs(ctx).edit().putBoolean("falar", sim).apply()


/** Pede ao Google para sincronizar já (conta recém-adicionada ou compromisso criado no computador). */
fun sincronizar(ctx: Context) = calendarios(ctx).filter { it.tipo == CONTA_GOOGLE }.map { it.conta }.distinct().forEach {
    try {
        ContentResolver.requestSync(Account(it, CONTA_GOOGLE), CalendarContract.AUTHORITY,
            Bundle().apply { putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true); putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true) })
    } catch (_: Exception) { /* alguns fabricantes bloqueiam: o Google sincroniza sozinho de qualquer jeito */ }
}

const val CONTA_GOOGLE = "com.google"

fun ocorrencias(ctx: Context, de: Long, ate: Long, ids: Set<Long>? = null): List<Ocorrencia> {
    val agendas = calendarios(ctx).filter { it.usado }.map { it.id }
    if (agendas.isEmpty()) return emptyList()
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, de); ContentUris.appendId(it, ate)
    }.build()
    val proj = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.EVENT_LOCATION, Instances.ALL_DAY,
        Instances.CALENDAR_DISPLAY_NAME)
    val sel = "${Instances.CALENDAR_ID} IN (${agendas.joinToString()}) AND " +
        "(${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Instances.STATUS_CANCELED}) AND " +
        // convite recusado não deve tocar
        "(${Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
    val lista = mutableListOf<Ocorrencia>()
    try {
        ctx.contentResolver.query(uri, proj, sel, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (ids != null && id !in ids) continue
                lista += Ocorrencia(id, c.getString(1)?.ifBlank { null } ?: "(sem título)",
                    c.getLong(2), c.getString(3).orEmpty(), c.getInt(4) == 1, nomeDaAgenda(c.getString(5)))
            }
        }
    } catch (_: SecurityException) { /* permissão de agenda revogada: lista vazia */ }
    // o mesmo compromisso em duas contas (convite entre contas do celular): mostra e toca uma vez só
    return lista.distinctBy { it.titulo to it.inicio }
}

// ponytail: SharedPreferences basta para "evento -> avisos"; Room só se surgir consulta de verdade
private fun prefs(ctx: Context) = ctx.getSharedPreferences("tilim", Context.MODE_PRIVATE)

// Todo compromisso do Google Agenda já chega com o padrão dos Ajustes; o usuário pode personalizar um evento.
val PADRAO_INICIAL = setOf(Aviso.H1, Aviso.M30, Aviso.MANHA)

private fun Set<String>.avisos() = mapNotNull { n -> Aviso.entries.find { it.name == n } }.toSet()

fun padrao(ctx: Context): Set<Aviso> {
    val p = prefs(ctx)
    val salvo = p.getStringSet("padrao", null)?.avisos() ?: return PADRAO_INICIAL
    // 1.1 -> 1.2: dia todo não tocava com o padrão antigo; liga "no dia, às 8h" uma vez para quem já tinha o app
    if (!p.getBoolean("migrou_manha", false)) {
        p.edit().putBoolean("migrou_manha", true).putStringSet("padrao", (salvo + Aviso.MANHA).map { it.name }.toSet()).apply()
        return salvo + Aviso.MANHA
    }
    return salvo
}

fun salvarPadrao(ctx: Context, avisos: Set<Aviso>) {
    prefs(ctx).edit().putStringSet("padrao", avisos.map { it.name }.toSet()).apply()
    reagendar(ctx)
}

/** Toques do Tilim em res/raw (tools/gerar_sons.py e tools/baixar_sons.sh; créditos em assets/licencas_sons.txt). */
val SONS_FORTES = listOf("sirene" to "Sirene", "sirene_rapida" to "Sirene rápida", "bipe" to "Bipe de despertador",
    "despertador_antigo" to "Despertador antigo", "telefone_antigo" to "Telefone antigo", "galo" to "Galo")
val SONS_SUAVES = listOf("campainha" to "Campainha", "relogio_pendulo" to "Relógio de pêndulo",
    "sino_igreja" to "Sino de igreja", "caixinha_musica" to "Caixinha de música",
    "melodia_calma" to "Melodia calma", "melodia_alegre" to "Melodia alegre")

fun uriDoSom(ctx: Context, som: String): android.net.Uri =
    if (som.contains(':')) android.net.Uri.parse(som) // escolhido no seletor do Android
    else android.net.Uri.parse("android.resource://${ctx.packageName}/raw/$som")

/** Nome de um toque nosso, uri de um toque do celular, ou null para o alarme padrão do celular. */
fun som(ctx: Context): String? = prefs(ctx).getString("som", null)

fun salvarSom(ctx: Context, som: String?) = prefs(ctx).edit().putString("som", som).apply()

/** Eventos personalizados (vale para todas as repetições). Conjunto vazio = "não tocar neste". */
fun personalizados(ctx: Context): Map<Long, Set<Aviso>> =
    prefs(ctx).all.filterKeys { it.startsWith("ev:") }.mapNotNull { (k, v) ->
        @Suppress("UNCHECKED_CAST") (v as? Set<String>)?.let { k.removePrefix("ev:").toLong() to it.avisos() }
    }.toMap()

fun salvarEscolha(ctx: Context, eventoId: Long, avisos: Set<Aviso>?) {
    prefs(ctx).edit().apply {
        // igual ao padrão = volta a seguir o padrão (e acompanha se o padrão mudar)
        if (avisos == null || avisos == padrao(ctx)) remove("ev:$eventoId")
        else putStringSet("ev:$eventoId", avisos.map { it.name }.toSet())
    }.apply()
    reagendar(ctx)
}

fun gatilhosFuturos(ctx: Context, agora: Long = System.currentTimeMillis()): List<Gatilho> {
    val pad = padrao(ctx)
    val per = personalizados(ctx)
    val ids = if (pad.isEmpty()) per.filterValues { it.isNotEmpty() }.keys.ifEmpty { return emptyList() } else null
    // véspera às 20h fica no máximo ~28h antes; 9 dias de janela cobre a semana com folga
    return plano(ocorrencias(ctx, agora - DIA, agora + 9 * DIA, ids), { per[it] ?: pad }, ZoneId.systemDefault())
}

fun reagendar(ctx: Context) {
    val agora = System.currentTimeMillis()
    val p = prefs(ctx)
    val marca = p.getLong("marca", agora).coerceAtMost(agora) // hora voltou para trás: não confia na marca
    val gatilhos = gatilhosFuturos(ctx, agora)

    val agoraTocam = devidos(gatilhos, marca, agora)
    if (agoraTocam.isNotEmpty()) {
        val primeiro = agoraTocam.minBy { it.oc.inicio }.oc
        Toque.tocar(ctx, agoraTocam.map { it.oc.titulo }.distinct().joinToString(" e "), primeiro.inicio,
            if (agoraTocam.size == 1) primeiro.local else "")
    }
    p.edit().putLong("marca", agora).apply()

    val am = ctx.getSystemService(AlarmManager::class.java)
    val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, Despertador::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val prox = proximo(gatilhos, agora)
    if (prox == null) am.cancel(pi)
    else am.setAlarmClock(AlarmManager.AlarmClockInfo(prox.quando, abrirApp(ctx)), pi)

    vigiarAgenda(ctx)
    desenharWidget(ctx)
}

fun abrirApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
    Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

/** Alarme exato, boot, mudança de hora/fuso, app atualizado e "lembrar de novo" caem aqui. */
class Despertador : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == ADIADO) Toque.tocar(ctx, i.getStringExtra("titulo").orEmpty(), i.getLongExtra("inicio", 0),
            i.getStringExtra("local").orEmpty())
        else reagendar(ctx)
    }

    companion object { const val ADIADO = "com.clevs.tilim.ADIADO" }
}

/** Dispara quando qualquer coisa muda no provedor de agenda (sync do Google). Gatilho de conteúdo é de uso único: reagendar() rearma. */
class CalendarioJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        reagendar(this)
        vigiarAgenda(this, forcar = true)
        return false
    }
    override fun onStopJob(params: JobParameters?) = false
}

private fun vigiarAgenda(ctx: Context, forcar: Boolean = false) {
    val js = ctx.getSystemService(JobScheduler::class.java)
    if (!forcar && js.getPendingJob(1) != null) return
    js.schedule(JobInfo.Builder(1, ComponentName(ctx, CalendarioJob::class.java))
        .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
        .setTriggerContentUpdateDelay(5_000)
        .build())
}
