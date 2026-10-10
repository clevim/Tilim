package com.clevs.tilim

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** Widget "Próximo compromisso". Redesenhado a cada reagendar() e a cada 30 min (vira "Hoje"/"Amanhã" certo). */
class Widget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, am: AppWidgetManager, ids: IntArray) = desenharWidget(ctx)
}

fun desenharWidget(ctx: Context) {
    val am = AppWidgetManager.getInstance(ctx)
    val ids = am.getAppWidgetIds(ComponentName(ctx, Widget::class.java))
    if (ids.isEmpty()) return
    val z = ZoneId.systemDefault()
    val agora = System.currentTimeMillis()
    val prox = ocorrencias(ctx, agora, agora + 14 * 86_400_000L)
        .firstOrNull { it.diaInteiro || it.inicio >= agora }
    val v = RemoteViews(ctx.packageName, R.layout.widget_proximo)
    v.setOnClickPendingIntent(R.id.widget, abrirApp(ctx))
    if (prox == null) {
        v.setViewVisibility(R.id.dia, View.GONE)
        v.setTextViewText(R.id.quando, "Próximos 14 dias")
        v.setTextViewText(R.id.titulo, "Nada marcado")
        v.setViewVisibility(R.id.aviso, View.GONE)
    } else {
        val ini = inicioLocal(prox, z)
        val dia = zdt(ini).toLocalDate()
        val nomeDia = when (dia) {
            LocalDate.now() -> "Hoje"
            LocalDate.now().plusDays(1) -> "Amanhã"
            else -> diaPorExtenso(ini).substringBefore(",")
        }
        v.setViewVisibility(R.id.dia, View.VISIBLE)
        v.setTextViewText(R.id.dia, "${dia.dayOfMonth}")
        v.setTextViewText(R.id.quando, if (prox.diaInteiro) "$nomeDia, o dia todo" else "$nomeDia, às ${hora(prox.inicio)}")
        v.setTextViewText(R.id.titulo, prox.titulo)
        val toca = gatilhosFuturos(ctx, agora).firstOrNull { it.oc.eventoId == prox.eventoId && it.oc.inicio == prox.inicio && it.quando > agora }
        v.setViewVisibility(R.id.aviso, if (toca != null) View.VISIBLE else View.GONE)
        if (toca != null) {
            val diaToque = if (zdt(toca.quando).toLocalDate() == LocalDate.now()) "" else diaPorExtenso(toca.quando).substringBefore(",").lowercase() + " "
            v.setTextViewText(R.id.aviso, "Toca ${diaToque}às ${hora(toca.quando)}")
        }
    }
    am.updateAppWidget(ids, v)
}
