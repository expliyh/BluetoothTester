package top.expli.bluetoothtester.privilege.shizuku

import android.os.RemoteException
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.system.exitProcess

class UserService : IUserService.Stub() {
    @Throws(RemoteException::class)
    override fun destroy() {
        exitProcess(0)
    }

    @Throws(RemoteException::class)
    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun runShellCommand(command: String?): String {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(command)
            val output = readStream(process.inputStream)
            val exitCode = process.waitFor()
            return if (exitCode == 0) output else "exit=$exitCode\n$output"
        } catch (e: IOException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "runShellCommand failed", e)
            throw RemoteException(e.message)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "runShellCommand failed", e)
            throw RemoteException(e.message)
        } finally {
            process?.destroy()
        }
    }

    @Throws(IOException::class)
    private fun readStream(input: InputStream?): String {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(input)).use { reader ->
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                sb.append(line).append('\n')
            }
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "UserService"
    }
}