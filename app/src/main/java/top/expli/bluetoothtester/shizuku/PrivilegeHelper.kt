package top.expli.bluetoothtester.shizuku

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PrivilegeHelper {
    suspend fun runCmd(context: Context, command: String): CommandResult
}

