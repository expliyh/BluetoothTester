package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ActionBarItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isRunning: Boolean = false,
    val isLoading: Boolean = false
)

@Composable
fun ActionBar(
    items: List<ActionBarItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            ActionBarButton(item)
        }
    }
}

@Composable
private fun ActionBarButton(item: ActionBarItem) {
    val clickEnabled = item.enabled && !item.isLoading
    val containerColor = when {
        item.isRunning -> MaterialTheme.colorScheme.primaryContainer
        else -> IconButtonDefaults.filledTonalIconButtonColors().containerColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FilledTonalIconButton(
            onClick = item.onClick,
            enabled = clickEnabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor
            )
        ) {
            if (item.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
