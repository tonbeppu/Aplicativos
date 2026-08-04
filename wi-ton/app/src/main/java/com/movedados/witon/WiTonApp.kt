package com.movedados.witon

import android.app.Application
import com.movedados.witon.core.ServiceLocator
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WiTonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        installCrashLogger()
    }

    /**
     * Apps de terceiros nao conseguem ler o logcat de outro app desde as
     * versoes recentes do Android — restricao de seguranca do sistema, nao
     * falta de permissao no app leitor. Por isso o proprio Wi Ton grava o
     * crash num arquivo de texto simples, em pasta acessivel por qualquer
     * gerenciador de arquivos: Android/data/<pacote>/files/crashes/.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(getExternalFilesDir(null), "crashes").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val file = File(dir, "crash_$stamp.txt")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.writeText(
                    "Wi Ton crash — $stamp\n" +
                    "Thread: ${thread.name}\n\n" +
                    sw.toString()
                )
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
