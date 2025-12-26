package top.expli.bluetoothtester.privilege

import android.content.Context
import top.expli.bluetoothtester.privilege.shizuku.CommandResult

interface PrivilegeHelper {
    suspend fun runCmd(context: Context, command: String): CommandResult
}