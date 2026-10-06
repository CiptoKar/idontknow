package com.example.ytmp3

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var etUrl: EditText
    private lateinit var btn: Button
    private lateinit var bar: ProgressBar
    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView

    private val workDir by lazy { File(filesDir, "download").apply { mkdirs() } }
    private val archive by lazy { File(filesDir, "downloaded_history.txt") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etUrl = findViewById(R.id.etUrl)
        btn = findViewById(R.id.btnDownload)
        bar = findViewById(R.id.bar)
        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.scroll)

        btn.isEnabled = false
        say("Menyiapkan yt-dlp & ffmpeg...")
        thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                say("Siap.")
                runOnUiThread { btn.isEnabled = true }
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(application)
                    say("yt-dlp diperiksa/diperbarui.")
                } catch (e: Exception) {
                    say("Update yt-dlp dilewati: ${e.message}")
                }
            } catch (e: Exception) {
                say("Gagal inisialisasi: ${e.message}")
            }
        }

        btn.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) download(url)
        }
    }

    private fun download(url: String) {
        btn.isEnabled = false
        bar.progress = 0
        thread {
            try {
                val req = YoutubeDLRequest(url).apply {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "320K")
                    addOption("--embed-metadata")
                    addOption("--ppa", "Metadata:-metadata album=")
                    addOption("--download-archive", archive.absolutePath)
                    addOption("--no-overwrites")
                    addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
                }
                YoutubeDL.getInstance().execute(req) { progress, _, line ->
                    runOnUiThread { bar.progress = progress.toInt().coerceIn(0, 100) }
                    say(line)
                }
                val saved = saveToMusic()
                say("\nSelesai! $saved file disimpan di Music/YT-MP3")
            } catch (e: Exception) {
                say("\nTerjadi error: ${e.message}")
            } finally {
                runOnUiThread { btn.isEnabled = true }
            }
        }
    }

    /** Pindahkan MP3 ke folder Music publik lewat MediaStore (tanpa izin storage). */
    private fun saveToMusic(): Int {
        var n = 0
        workDir.listFiles { f -> f.extension == "mp3" }?.forEach { f ->
            val v = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YT-MP3")
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { o ->
                    f.inputStream().use { it.copyTo(o) }
                }
                f.delete()
                n++
            }
        }
        return n
    }

    private fun say(msg: String) = runOnUiThread {
        tvLog.append(msg + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
