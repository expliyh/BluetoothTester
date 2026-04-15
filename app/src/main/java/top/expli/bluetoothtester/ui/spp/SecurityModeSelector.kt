package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SecurityMode

@Composable
fun SecurityModeSelector(
    selected: SecurityMode,
    onSelectionChange: (SecurityMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = selected == SecurityMode.Secure,
            onClick = { onSelectionChange(SecurityMode.Secure) },
            label = { Text("Secure") },
            enabled = enabled
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = selected == SecurityMode.Insecure,
            onClick = { onSelectionChange(SecurityMode.Insecure) },
            label = { Text("Insecure") },
            enabled = enabled
        )
    }
}
