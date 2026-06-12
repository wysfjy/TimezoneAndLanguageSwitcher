package com.time.set.utils

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Semaphore

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    enum class Mode {
        ROOT, SHIZUKU, NONE
    }

    var currentMode = Mode.NONE

    // 持久 shell 会话
    private var shellProcess: Process? = null
    private var shellStdin: OutputStream? = null
    private var shellStdout: BufferedReader? = null
    private var shellStderr: BufferedReader? = null
    private val marker = UUID.randomUUID().toString().replace("-", "").take(12)
    // 保证同一时间只有一个命令在执行
    private val mutex = Semaphore(1)

    fun exec(command: String): String {
        return when (currentMode) {
            Mode.ROOT -> execPersistent(command)
            Mode.SHIZUKU -> execPersistent(command)
            Mode.NONE -> "Error: Not activated"
        }
    }

    /**
     * 通过持久 shell 执行命令。每次调用复用同一个 shell 进程，
     * 避免每次 exec 都启动新进程的开销。
     *
     * 协议：发送 `command; echo MARKER_XXXXXX_$?`
     * 读取输出直到遇到 marker 行，从中提取 exit code。
     */
    private fun execPersistent(command: String): String {
        mutex.acquire()
        try {
            ensureShell()

            val stdin = shellStdin ?: return "Error: Shell not available"
            val stdout = shellStdout ?: return "Error: Shell not available"

            // 发送命令 + marker（marker 包含 exit code）
            if (Prefs.isDetailedLogEnabled) {
                Log.d(TAG, "Executing: $command")
            }
            stdin.write((command + "; echo ${marker}_\$?\n").toByteArray())
            stdin.flush()

            // 读取输出，直到遇到 marker 行
            val output = StringBuilder()
            while (true) {
                val line = stdout.readLine() ?: break
                if (line.startsWith(marker + "_")) {
                    val exitCodeStr = line.removePrefix(marker + "_")
                    val exitCode = exitCodeStr.toIntOrNull() ?: -1
                    val result = formatResult(output.toString().trim(), "", exitCode)
                    if (Prefs.isDetailedLogEnabled) {
                        Log.d(TAG, "Result ($exitCode): $result")
                    }
                    return result
                }
                output.append(line).append("\n")
            }

            // 如果走到这里说明 shell 挂了，重置并重试一次
            destroyShell()
            return "Error: Shell disconnected"
        } catch (e: Exception) {
            destroyShell()
            return "Error: ${e.message}"
        } finally {
            mutex.release()
        }
    }

    @Synchronized
    private fun ensureShell() {
        if (shellProcess != null) return

        try {
            when (currentMode) {
                Mode.ROOT -> {
                    val process = Runtime.getRuntime().exec(arrayOf("su"))
                    shellProcess = process
                    shellStdin = process.outputStream
                    shellStdout = BufferedReader(InputStreamReader(process.inputStream))
                    shellStderr = BufferedReader(InputStreamReader(process.errorStream))
                }
                Mode.SHIZUKU -> {
                    val method = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.isAccessible = true
                    val process = method.invoke(null, arrayOf("sh"), null, null) as Process
                    shellProcess = process
                    shellStdin = process.outputStream
                    shellStdout = BufferedReader(InputStreamReader(process.inputStream))
                    shellStderr = BufferedReader(InputStreamReader(process.errorStream))
                }
                Mode.NONE -> {}
            }
        } catch (e: Exception) {
            destroyShell()
        }
    }

    @Synchronized
    private fun destroyShell() {
        try { shellStdin?.close() } catch (_: Exception) {}
        try { shellStdout?.close() } catch (_: Exception) {}
        try { shellStderr?.close() } catch (_: Exception) {}
        try { shellProcess?.destroy() } catch (_: Exception) {}
        shellStdin = null
        shellStdout = null
        shellStderr = null
        shellProcess = null
    }

    /** 切换模式或退出时调用，销毁持久 shell */
    fun destroy() {
        destroyShell()
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

    private fun formatResult(output: String, error: String, exitCode: Int): String {
        return if (exitCode == 0) {
            if (output.isEmpty() && error.isEmpty()) "成功" else output.ifEmpty { error }
        } else {
            "错误 (代码 $exitCode): $error $output".trim()
        }
    }
}
