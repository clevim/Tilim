package com.clevs.tilim

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import java.net.URL
import org.json.JSONObject

/** Atualização direto do GitHub Releases: sem Play Store, a família instala o APK uma vez e o resto vem sozinho. */

private const val ULTIMA = "https://api.github.com/repos/clevim/Tilim/releases/latest"
private const val ORIGEM = "https://github.com/clevim/Tilim/releases/download/"

data class Versao(val nome: String, val apk: String)

/** "v1.10.0" > "1.9.2". Compara número a número; o que faltar conta como zero. */
fun maisNova(tag: String, atual: String): Boolean {
    fun partes(s: String) = s.trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = partes(tag); val b = partes(atual)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

/** Rede: chamar fora da thread principal. null = sem internet, sem release ou já está na última. */
fun versaoNova(ctx: Context): Versao? = runCatching {
    val atual = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: return null
    val j = JSONObject(URL(ULTIMA).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
        .getInputStream().bufferedReader().use { it.readText() })
    val tag = j.getString("tag_name")
    if (!maisNova(tag, atual)) return null
    val assets = j.getJSONArray("assets")
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
        .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
    val url = apk.getString("browser_download_url")
    if (!url.startsWith(ORIGEM)) return null // só baixa do repositório oficial
    Versao(tag.trimStart('v', 'V'), url)
}.getOrNull()

/** Baixa pelo DownloadManager (aparece na barra de notificações) e, ao terminar, abre o instalador do Android. */
fun baixarEInstalar(ctx: Context, v: Versao) {
    val app = ctx.applicationContext
    val dm = app.getSystemService(DownloadManager::class.java)
    val id = dm.enqueue(DownloadManager.Request(Uri.parse(v.apk))
        .setTitle("Tilim ${v.nome}")
        // se a pessoa sair do app no meio, o Android não deixa abrir o instalador sozinho: tocar na notificação resolve
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setMimeType("application/vnd.android.package-archive")
        .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "Tilim-${v.nome}.apk"))
    val fim = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
            app.unregisterReceiver(this)
            val uri = dm.getUriForDownloadedFile(id) ?: return // falhou: a notificação do download já avisa
            // o Android confere a assinatura: só instala por cima se vier da mesma chave
            app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    // o aviso vem do app de downloads do sistema, por isso EXPORTED (constante inline; antes do 33 é ignorada)
    app.registerReceiver(fim, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
}
