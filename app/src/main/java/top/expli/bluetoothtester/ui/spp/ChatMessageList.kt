package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppChatDirection
import top.expli.bluetoothtester.model.SppChatItem

@Composable
fun ChatMessageList(
    chat: List<SppChatItem>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(12.dp)
) {
    if (chat.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chat, key = { it.id }) { item ->
                ChatLine(item)
            }
        }
    }
}

@Composable
internal fun ChatLine(item: SppChatItem) {
    val arrangement = when (item.direction) {
        SppChatDirection.In -> Arrangement.Start
        SppChatDirection.Out -> Arrangement.End
        SppChatDirection.System -> Arrangement.Center
    }
    val color = when (item.direction) {
        SppChatDirection.In -> MaterialTheme.colorScheme.onSurface
        SppChatDirection.Out -> MaterialTheme.colorScheme.primary
        SppChatDirection.System -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        Text(
            text = item.text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = when (item.direction) {
                SppChatDirection.In -> TextAlign.Start
                SppChatDirection.Out -> TextAlign.End
                SppChatDirection.System -> TextAlign.Center
            },
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}
