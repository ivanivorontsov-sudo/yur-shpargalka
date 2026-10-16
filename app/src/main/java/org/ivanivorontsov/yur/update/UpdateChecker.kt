package org.ivanivorontsov.yur.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ivanivorontsov.yur.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList()
)

data class UpdateInfo(val tag: String, val apkUrl: String, val notes: String)

class UpdateChecker(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(BuildConfig.RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "YurShpargalka/1.0")
            }
            try {
                if (conn.responseCode == 404) return@runCatching null
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val release = json.decodeFromString<GhRelease>(conn.inputStream.bufferedReader().readText())
                val remote = release.tagName.removePrefix("v")
                if (compareVersions(remote, BuildConfig.VERSION_NAME) <= 0) return@runCatching null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                    ?: return@runCatching null
                UpdateInfo(release.tagName, apk.browserDownloadUrl, release.body.orEmpty())
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun downloadApk(url: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "apk").also { it.mkdirs() }
            val out = File(dir, "update.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 120000
                setRequestProperty("User-Agent", "YurShpargalka/1.0")
            }
            try {
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out
            } finally {
                conn.disconnect()
            }
        }
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
