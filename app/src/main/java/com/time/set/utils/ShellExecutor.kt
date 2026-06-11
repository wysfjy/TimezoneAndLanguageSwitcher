package com.time.set.utils

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {

    enum class Mode {
        ROOT, SHIZUKU, NONE
    }

    var currentMode = Mode.NONE

    fun exec(command: String): String {
        return when (currentMode) {
            Mode.ROOT -> execRoot(command)
            Mode.SHIZUKU -> execShizuku(command)
            Mode.NONE -> "Error: Not activated"
        }
    }

    fun isActivated(): Boolean {
        return when (currentMode) {
            Mode.ROOT -> checkRoot()
            Mode.SHIZUKU -> checkShizuku()
            Mode.NONE -> false
        }
    }

    fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun checkShizuku(): Boolean {
        return try {
            rikka.shizuku.Shizuku.pingBinder() && 
            rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun execRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = readStream(process.inputStream)
            val error = readStream(process.errorStream)
            val exitCode = process.waitFor()
            
            formatResult(output, error, exitCode)
        } catch (e: Exception) {
            "Root Error: ${e.message}"
        }
    }

    private fun execShizuku(command: String): String {
        return try {
            // Shizuku.newProcess is private in Shizuku v13, using reflection as a workaround
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess

            val output = readStream(process.inputStream)
            val error = readStream(process.errorStream)
            val exitCode = process.waitFor()
            
            formatResult(output, error, exitCode)
        } catch (e: Exception) {
            "Shizuku Error: ${e.message}"
        }
    }

    private fun readStream(stream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        return output.toString().trim()
    }

    private fun formatResult(output: String, error: String, exitCode: Int): String {
        return if (exitCode == 0) {
            if (output.isEmpty() && error.isEmpty()) "成功" else output.ifEmpty { error }
        } else {
            "错误 (代码 $exitCode): $error $output".trim()
        }
    }
}
