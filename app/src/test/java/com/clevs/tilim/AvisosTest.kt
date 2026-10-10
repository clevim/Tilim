package com.clevs.tilim

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AvisosTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private fun t(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), sp).toInstant().toEpochMilli()
    private val consulta = Ocorrencia(1, "Médico", t("2026-10-14T09:00"), "", false)

    @Test fun offsets() {
        assertEquals(t("2026-10-13T20:00"), quando(Aviso.VESPERA, consulta, sp))
        assertEquals(t("2026-10-14T08:00"), quando(Aviso.H1, consulta, sp))
        assertEquals(t("2026-10-14T08:50"), quando(Aviso.M10, consulta, sp))
    }

    @Test fun diaInteiroVesperaEManha() {
        // dia inteiro: provedor guarda meia-noite UTC
        val feriado = Ocorrencia(2, "Aniversário", java.time.LocalDate.parse("2026-10-14")
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), "", true)
        val g = plano(listOf(feriado), { Aviso.entries.toSet() }, sp)
        assertEquals(listOf(Aviso.VESPERA, Aviso.MANHA), g.map { it.aviso })
        assertEquals(t("2026-10-13T20:00"), g[0].quando)
        assertEquals(t("2026-10-14T08:00"), g[1].quando)
        // compromisso com horário nunca usa "no dia, às 8h"
        assertEquals(false, Aviso.MANHA in plano(listOf(consulta), { Aviso.entries.toSet() }, sp).map { it.aviso })
    }

    @Test fun devidoRespeitaMarcaEAtraso() {
        val g = plano(listOf(consulta), { setOf(Aviso.H1, Aviso.M10) }, sp)
        assertEquals(listOf(Aviso.H1), devidos(g, t("2026-10-14T07:59"), t("2026-10-14T08:00")).map { it.aviso })
        assertEquals(emptyList<Gatilho>(), devidos(g, t("2026-10-14T08:00"), t("2026-10-14T08:01"))) // já tocou
        assertEquals(emptyList<Gatilho>(), devidos(g, t("2026-10-14T07:00"), t("2026-10-14T08:30"))) // atrasado demais
        // dois compromissos no mesmo horário tocam juntos
        val outro = Ocorrencia(3, "Remédio", consulta.inicio, "", false)
        assertEquals(2, devidos(plano(listOf(consulta, outro), { setOf(Aviso.H1) }, sp),
            t("2026-10-14T07:59"), t("2026-10-14T08:00")).size)
        assertEquals(Aviso.M10, proximo(g, t("2026-10-14T08:00"))?.aviso)
    }

    @Test fun frases() {
        val ini = t("2026-10-14T09:00")
        assertEquals("Daqui a 10 minutos", falta(ini, t("2026-10-14T08:50"), sp))
        assertEquals("Daqui a 1 hora e 30 minutos", falta(ini, t("2026-10-14T07:30"), sp))
        assertEquals("Amanhã", falta(ini, t("2026-10-13T20:00"), sp))
        assertEquals("Agora", falta(ini, t("2026-10-14T09:02"), sp))
    }

    @Test fun nomeDeAgendaCru() {
        assertEquals("Agenda do celular", nomeDaAgenda("calendar_displayname_local"))
        assertEquals("Agenda do celular", nomeDaAgenda(null))
        assertEquals("Pessoal", nomeDaAgenda("Pessoal"))
        assertEquals("maria.silva@gmail.com", nomeDaAgenda("maria.silva@gmail.com"))
    }

    @Test fun exdateEmUtc() {
        assertEquals("20261014T120000Z", exdateUtc(t("2026-10-14T09:00"))) // São Paulo é UTC-3
    }

    @Test fun falaEmVozAlta() {
        assertEquals("Remédio. Daqui a 30 minutos, às 8 da manhã.",
            fala("Remédio", t("2026-10-14T08:00"), "", t("2026-10-14T07:30"), sp))
        assertEquals("Está na hora: Médico. Local: Clínica São Lucas.",
            fala("Médico", t("2026-10-14T14:30"), "Clínica São Lucas", t("2026-10-14T14:30"), sp))
        assertEquals("Exame. Amanhã, às 7 da manhã.", fala("Exame", t("2026-10-15T07:00"), "", t("2026-10-14T20:00"), sp))
        // 14/10/2026 é quarta: sexta fica a 2 dias
        assertEquals("Dentista. Na sexta-feira, às duas e meia da tarde.",
            fala("Dentista", t("2026-10-16T14:30"), "", t("2026-10-14T09:00"), sp))
        assertEquals("à uma e 5 da tarde", horaFalada(13, 5))
        assertEquals("ao meio-dia e meia", horaFalada(12, 30))
        assertEquals("às 7 da noite", horaFalada(19, 0))
        assertEquals("à meia-noite", horaFalada(0, 0))
    }

    @Test fun exdateAcompanhaHoraNova() {
        val oito = java.time.LocalTime.of(8, 0)
        // 9/10 às 16h (19h UTC) apagado; série vai para 8h: continua apagado, às 8h (11h UTC)
        assertEquals("20261009T110000Z", moverExdate("20261009T190000Z", oito, sp))
        // dia todo e TZID também
        assertEquals("20261009T110000Z,20261010T110000Z",
            moverExdate("20261009,TZID=America/Sao_Paulo:20261010T160000", oito, sp))
        assertEquals(null, moverExdate("", oito, sp))
    }

    @Test fun duracaoDoProvedor() {
        assertEquals(3_600_000L, duracaoMs("P3600S"))
        assertEquals(3_600_000L, duracaoMs("PT1H"))
        assertEquals(86_400_000L, duracaoMs("P1D"))
        assertEquals(null, duracaoMs("lixo"))
    }
}
