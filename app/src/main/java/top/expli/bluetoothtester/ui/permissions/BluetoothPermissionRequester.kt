package top.expli.bluetoothtester.ui.permissions

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun BluetoothPermissionRequester() {
    val context = LocalContext.current
    val activity = context as? Activity
    var showSettingsDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted && activity != null && !BluetoothPermissions.shouldShowRationale(activity)) {
            showSettingsDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!BluetoothPermissions.hasAll(context)) {
            launcher.launch(BluetoothPermissions.required)
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text("蓝牙权限可能已被永久拒绝，请前往系统设置手动开启。") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    openAppSettings(context)
                }) { Text("打开设置") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("取消") }
            }
        )
    }
}
