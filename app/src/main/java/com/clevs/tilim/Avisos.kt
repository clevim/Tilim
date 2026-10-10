package com.clevs.tilim

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Lógica pura (sem Android) — coberta por AvisosTest. */

const val HORA_VESPERA = 20
const val HORA_MANHA = 8

enum class Aviso(val rotulo: String, val minutos: Long) {
    VESPERA("1 dia antes", 0),
    MANHA("No dia, às ${HORA_MANHA}h", 0), // só para dia inteiro: compromisso sem horário
    H1("1 hora antes", 60),
    M30("30 min antes", 30),
    M10("10 min antes", 10),
}

data class Ocorrencia(
    val eventoId: Long,
    val titulo: String,
    val inicio: Long,
    val local: String,
    val diaInteiro: Boolean,
    val agenda: String = "",
)

data class Gatilho(val oc: Ocorrencia, val aviso: Aviso, val quando: Long)

/** Evento de dia inteiro vem do provedor como meia-noite UTC; converte para meia-noite local. */
fun inicioLocal(oc: Ocorrencia, zona: ZoneId): Long =
    if (!oc.diaInteiro) oc.inicio
    else Instant.ofEpochMilli(oc.inicio).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(zona).toInstant().toEpochMilli()

/** Dia inteiro não tem horário: "1 hora antes" não existe, então avisa na véspera ou na manhã do dia. */
fun avisosPermitidos(oc: Ocorrencia) = if (oc.diaInteiro) listOf(Aviso.VESPERA, Aviso.MANHA) else Aviso.entries - Aviso.MANHA

fun quando(aviso: Aviso, oc: Ocorrencia, zona: ZoneId): Long {
    val inicio = inicioLocal(oc, zona)
    return when (aviso) {
        Aviso.VESPERA -> Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate().minusDays(1)
            .atTime(LocalTime.of(HORA_VESPERA, 0)).atZone(zona).toInstant().toEpochMilli()
        Aviso.MANHA -> Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate()
            .atTime(LocalTime.of(HORA_MANHA, 0)).atZone(zona).toInstant().toEpochMilli()
        else -> inicio - aviso.minutos * 60_000
    }
}

/** avisosDe: o personalizado do evento ou, se não houver, o padrão dos Ajustes. */
fun plano(ocs: List<Ocorrencia>, avisosDe: (Long) -> Set<Aviso>, zona: ZoneId): List<Gatilho> =
    ocs.flatMap { oc ->
        avisosDe(oc.eventoId).filter { it in avisosPermitidos(oc) }
            .map { Gatilho(oc, it, quando(it, oc, zona)) }
    }.sortedBy { it.quando }

const val ATRASO_MAX = 15 * 60_000L

/**
 * Gatilhos que devem tocar agora: venceram depois da última varredura (marca) e não estão velhos demais.
 * Celular desligado por horas não deve tocar "10 min antes" de algo que já passou.
 * Pode ser mais de um (dois compromissos no mesmo horário): o alarme mostra todos.
 */
fun devidos(gatilhos: List<Gatilho>, marca: Long, agora: Long): List<Gatilho> =
    gatilhos.filter { it.quando in (marca + 1)..agora && agora - it.quando < ATRASO_MAX }
        .distinctBy { it.oc.eventoId to it.oc.inicio }

fun proximo(gatilhos: List<Gatilho>, agora: Long): Gatilho? = gatilhos.firstOrNull { it.quando > agora }

/** Frase do alarme, sempre relativa ao momento em que toca (vale para adiar e testar também). */
fun falta(inicio: Long, agora: Long, zona: ZoneId): String {
    val min = (inicio - agora + 30_000) / 60_000
    val dias = java.time.temporal.ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(agora).atZone(zona).toLocalDate(), Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate())
    val h = min / 60
    val m = min % 60
    return when {
        min <= 0 -> "Agora"
        dias == 1L -> "Amanhã"
        dias > 1 -> "Daqui a $dias dias"
        h == 0L -> if (m == 1L) "Daqui a 1 minuto" else "Daqui a $m minutos"
        else -> "Daqui a " + (if (h == 1L) "1 hora" else "$h horas") + (if (m > 0) " e $m minutos" else "")
    }
}

enum class Repete(val rotulo: String, val rrule: String?) {
    NAO("Não repete", null),
    DIA("Todo dia", "FREQ=DAILY"),
    SEMANA("Toda semana", "FREQ=WEEKLY"),
    MES("Todo mês", "FREQ=MONTHLY"),
}

/**
 * Hora do jeito que se fala: "às duas e meia da tarde". "14h30" o sintetizador lê mal, e "2" ele lê "dois"
 * (hora é feminino: uma, duas).
 */
fun horaFalada(h: Int, m: Int): String {
    val min = when (m) { 0 -> ""; 30 -> " e meia"; else -> " e $m" }
    return when (h) {
        0 -> "à meia-noite$min"
        12 -> "ao meio-dia$min"
        else -> {
            val h12 = if (h > 12) h - 12 else h
            val periodo = when (h) { in 1..4 -> "da madrugada"; in 5..11 -> "da manhã"; in 13..17 -> "da tarde"; else -> "da noite" }
            when (h12) { 1 -> "à uma"; 2 -> "às duas"; else -> "às $h12" } + "$min $periodo"
        }
    }
}

private val semana = listOf("na segunda-feira", "na terça-feira", "na quarta-feira", "na quinta-feira",
    "na sexta-feira", "no sábado", "no domingo")

/**
 * O que o alarme fala, pensado para ser entendido de primeira:
 * "Consulta no cardiologista. Daqui a 30 minutos, às duas e meia da tarde. Local: Clínica São Lucas."
 */
fun fala(titulo: String, inicio: Long, local: String, agora: Long, zona: ZoneId): String {
    val t = Instant.ofEpochMilli(inicio).atZone(zona)
    val dias = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(agora).atZone(zona).toLocalDate(), t.toLocalDate())
    val f = falta(inicio, agora, zona)
    val quando = when {
        f == "Agora" -> null
        dias in 2..6 -> semana[t.dayOfWeek.value - 1] // "na sexta-feira" diz mais que "daqui a 3 dias"
        else -> f
    }
    val onde = if (local.isBlank()) "" else " Local: $local."
    return if (quando == null) "Está na hora: $titulo.$onde"
    else "$titulo. ${quando.replaceFirstChar { it.uppercase() }}, ${horaFalada(t.hour, t.minute)}.$onde"
}

/** Dia (no fuso do aparelho) de um item de EXDATE: "20261009T190000Z", "20261009" (dia todo) ou "TZID=...:20261009T160000". */
fun diaDoExdate(item: String, zona: ZoneId): java.time.LocalDate {
    val tz = item.substringBefore(':', "").removePrefix("TZID=").ifBlank { null }
    val v = item.substringAfter(':')
    return when {
        v.length == 8 -> java.time.LocalDate.parse(v, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        v.endsWith("Z") -> java.time.LocalDateTime.parse(v.dropLast(1), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(zona).toLocalDate()
        else -> java.time.LocalDateTime.parse(v, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(tz?.let(ZoneId::of) ?: zona).withZoneSameInstant(zona).toLocalDate()
    }
}

/** Série mudou de hora: os dias apagados continuam apagados, agora na hora nova. */
fun moverExdate(exdate: String?, hora: LocalTime, zona: ZoneId): String? =
    exdate?.split(',', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?.joinToString(",") { exdateUtc(diaDoExdate(it, zona).atTime(hora).atZone(zona).toInstant().toEpochMilli()) }
        ?.ifEmpty { null }

/** Formato RFC 5545 em UTC, o que o provedor guarda em EXDATE. */
fun exdateUtc(ms: Long): String = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms))

/** DURATION do provedor em ms. O Google grava "P3600S" (sem o T que o java.time exige). */
fun duracaoMs(d: String?): Long? = d?.let {
    val iso = if (!it.contains('T') && it.last() in "HMS") it.replaceFirst("P", "PT") else it
    runCatching { java.time.Duration.parse(iso).toMillis() }.getOrNull()
}
