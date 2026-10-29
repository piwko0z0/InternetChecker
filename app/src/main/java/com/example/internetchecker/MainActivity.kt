package com.example.internetchecker

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.Intent
import android.net.Uri
import java.util.concurrent.TimeUnit

data class SiteResult(val url: String, val success: Boolean)

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnCheck: Button
    private lateinit var btnLogs: Button

    private val allResults = mutableListOf<SiteResult>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // Группа A — белый список (эталон доступности)
    private val whitelistDomains = listOf(
        "https://ya.ru",
        "https://gosuslugi.ru",
        "https://mail.ru",
        "https://rutube.ru",
        "https://dzen.ru",
        "https://sber.ru"
    )

    // Группа B — обычный интернет
    private val normalDomains = listOf(
        "https://habr.com",
        "https://github.com",
        "https://www.google.com",
        "https://www.bing.com",
        "https://www.microsoft.com",
        "https://www.apple.com"
    )

    // Группа C — заблокированные в РФ
    private val blockedDomains = listOf(
        "https://www.instagram.com",
        "https://x.com",
        "https://rutracker.org",
        "https://discord.com",
        "https://www.linkedin.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnCheck = findViewById(R.id.btnCheck)
        btnLogs = findViewById(R.id.btnLogs)

        // Находим нашу новую подпись автора
        val tvAuthor = findViewById<TextView>(R.id.tvAuthor)

        // Обработка нажатия на кнопку сканера
        btnCheck.setOnClickListener {
            btnCheck.isEnabled = false
            allResults.clear()
            tvStatus.text = "[ЗАПУСК СКАНИРОВАНИЯ СЕТИ...]\n[ГЕНЕРАЦИЯ ПАКЕТОВ...]\n[ОЖИДАНИЕ ОТВЕТА...]"
            performCheck()
        }

        // Обработка нажатия на логи
        btnLogs.setOnClickListener {
            showLogsDialog()
        }

        // Обработка нажатия на ссылку GitHub
        tvAuthor.setOnClickListener {
            // ВАЖНО: Замените ссылку ниже на ссылку вашего профиля GitHub!
            val url = "https://github.com/piwko0z0"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun performCheck() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val a = checkGroup(whitelistDomains)
                val b = checkGroup(normalDomains)
                val c = checkGroup(blockedDomains)

                buildVerdict(a, b, c)
            }

            tvStatus.text = result
            btnCheck.isEnabled = true
        }
    }

    private suspend fun checkGroup(domains: List<String>): Pair<Int, Int> = coroutineScope {
        val results = domains.map { url ->
            async(Dispatchers.IO) {
                val isOk = isReachable(url)
                SiteResult(url, isOk)
            }
        }.awaitAll()

        synchronized(allResults) {
            allResults.addAll(results)
        }

        val successCount = results.count { it.success }
        successCount to domains.size
    }

    private fun isReachable(url: String): Boolean {
        return try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 300..399
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildVerdict(a: Pair<Int, Int>, b: Pair<Int, Int>, c: Pair<Int, Int>): String {
        val (aOk, aTotal) = a
        val (bOk, bTotal) = b
        val (cOk, cTotal) = c

        val aWorking = aOk > 0
        val bWorking = bOk > 0
        val cWorking = cOk > 0

        val details = """
            |> БЕЛЫЙ СПИСОК (A): $aOk/$aTotal
            |> ОБЫЧНЫЙ НЕТ  (B): $bOk/$bTotal
            |> ЗАБЛОКИРОВАНО (C): $cOk/$cTotal
        """.trimMargin()

        return when {
            cWorking -> "🔵 [VPN DETECTED]\nОбнаружен обход блокировок!\nСнимите VPN для чистоты теста.\n\n$details"
            aWorking && bWorking && !cWorking -> "🟢 [SYSTEM NORMAL]\nОграничений не обнаружено.\n\n$details"
            aWorking && !bWorking && !cWorking -> "🟡 [WHITELIST WARNING]\nВероятно, включен «белый список».\n\n$details"
            !aWorking && !bWorking && !cWorking -> "🔴 [OFFLINE]\nНет доступа к сети.\n\n$details"
            else -> "🟠 [UNSTABLE NETWORK]\nСтранное поведение сети.\n\n$details"
        }
    }

    private fun showLogsDialog() {
        if (allResults.isEmpty()) {
            tvStatus.text = "[ОШИБКА] Логи пусты. Сначала запустите сканер."
            return
        }

        val logBuilder = StringBuilder()
        val sortedLogs = allResults.sortedByDescending { it.success }

        for (result in sortedLogs) {
            val status = if (result.success) "  [ OK ]" else "[ FAIL ]"
            val cleanUrl = result.url.replace("https://", "").take(22)
            logBuilder.append("$status $cleanUrl\n")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("📡 ТЕРМИНАЛ СЕТИ")
            .setMessage(logBuilder.toString())
            .setPositiveButton("ЗАКРЫТЬ") { _, _ -> }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFCEE0A.toInt())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).textSize = 14f
        }
        dialog.show()
    }
}