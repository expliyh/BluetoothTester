package top.expli.bluetoothtester.ui.spp

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import top.expli.bluetoothtester.model.ServerTabConfig
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.model.SppUiState
import top.expli.bluetoothtester.model.SppViewModel
import top.expli.bluetoothtester.ui.navigation.AppNavTransitions

@SuppressLint("MissingPermission")
@Composable
fun ServerTabPage(
    serverTab: ServerTabConfig,
    vm: SppViewModel,
    state: SppUiState,
    ensureBluetoothPermissions: (() -> Unit) -> Unit,
    onScrollToLatest: (() -> Unit) -> Unit,
    onIsInDetailChanged: (Boolean) -> Unit,
    onBackHandler: ((() -> Unit)?) -> Unit,
    onDetailInfo: ((address: String?, isHistory: Boolean) -> Unit),
    modifier: Modifier = Modifier
) {
    val latestState by rememberUpdatedState(state)
    val tabId = serverTab.tabId
    val key = "server:$tabId"
    val sessionState = state.sessions[key]

    // Select this server tab when it becomes visible
    LaunchedEffect(tabId) {
        vm.selectServerTab(tabId)
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inDetail = currentRoute != null && currentRoute.contains("SessionDetail")

    // Report detail state changes to parent (matches ClientTabPage pattern)
    LaunchedEffect(inDetail) {
        onIsInDetailChanged(inDetail)
        onBackHandler(if (inDetail) ({ navController.navigateUp() }) else null)
    }

    // Defensive cleanup: reset navigation state when leaving Composition
    DisposableEffect(Unit) {
        onDispose {
            onIsInDetailChanged(false)
            onBackHandler(null)
            onDetailInfo(null, false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ServerRoute.SessionList,
        enterTransition = AppNavTransitions.enter,
        exitTransition = AppNavTransitions.exit,
        popEnterTransition = AppNavTransitions.popEnter,
        popExitTransition = AppNavTransitions.popExit,
        modifier = modifier
    ) {
        composable<ServerRoute.SessionList> {
            ServerSessionListScreen(
                sessionState = sessionState,
                listenerState = sessionState?.serverListenerState
                    ?: top.expli.bluetoothtester.model.ServerListenerState.Idle,
                uuid = serverTab.uuid,
                securityMode = serverTab.securityMode,
                onSecurityModeChange = { mode ->
                    vm.updateServerTabSecurityMode(tabId, mode)
                },
                lastError = sessionState?.lastError,
                onStartListening = {
                    ensureBluetoothPermissions { vm.startListeningForTab(tabId) }
                },
                onStopListening = {
                    ensureBluetoothPermissions { vm.stopListeningForTab(tabId) }
                },
                onOpenActiveSession = {
                    navController.navigate(ServerRoute.SessionDetail("active"))
                },
                onOpenHistorySession = { snapshotId ->
                    navController.navigate(ServerRoute.SessionDetail(snapshotId))
                },
                onDeleteHistorySession = { snapshotId ->
                    val session = latestState.sessions[key] ?: return@ServerSessionListScreen
                    val index = session.sessionHistory.indexOfFirst { it.id == snapshotId }
                    if (index >= 0) {
                        vm.deleteServerSession(tabId, index)
                    }
                },
                onClearSessionHistory = { vm.clearServerSessionHistory(tabId) },
                modifier = Modifier.fillMaxSize()
            )
        }

        composable<ServerRoute.SessionDetail> { entry ->
            val route = entry.toRoute<ServerRoute.SessionDetail>()
            val snapshotId = route.snapshotId
            val currentSession = latestState.sessions[key]

            // Resolve display data based on snapshotId
            val resolved = remember(snapshotId, currentSession) {
                resolveSessionDetail(snapshotId, currentSession)
            }
            val displaySession = resolved.session
            val isHistory = resolved.isHistory

            // Notify parent about detail info
            LaunchedEffect(displaySession?.remoteDeviceAddress, isHistory) {
                onDetailInfo(displaySession?.remoteDeviceAddress, isHistory)
            }

            // Navigate back if snapshot not found
            LaunchedEffect(resolved.notFound) {
                if (resolved.notFound) {
                    navController.navigateUp()
                }
            }

            if (isHistory) {
                // History session: read-only, all callbacks are no-ops
                ServerDetailScreen(
                    modifier = Modifier.fillMaxSize(),
                    sessionState = displaySession,
                    isHistory = true,
                    onScrollToLatest = onScrollToLatest
                )
            } else {
                // Active session: full ViewModel ForTab callback bindings
                ServerDetailScreen(
                    modifier = Modifier.fillMaxSize(),
                    sessionState = displaySession,
                    isHistory = false,
                    onTextChange = { vm.updateSendingTextForTab(tabId, it) },
                    onPayloadChange = { vm.updatePayloadSizeForTab(tabId, it) },
                    onSend = { vm.sendOnceForTab(tabId) },
                    onToggleSpeedTest = { ensureBluetoothPermissions { vm.toggleSpeedTestForTab(tabId) } },
                    onToggleSpeedTestMode = { vm.toggleSpeedTestModeForTab(tabId) },
                    onMuteConsoleDuringTestChange = { vm.setMuteConsoleDuringTestForTab(tabId, it) },
                    onSpeedTestWindowOpenChange = { vm.setSpeedTestWindowOpenForTab(tabId, it) },
                    onSpeedTestPayloadChange = { vm.updateSpeedTestPayloadForTab(tabId, it) },
                    onParseIncomingAsTextChange = { vm.updateParseIncomingAsTextForTab(tabId, it) },
                    onScrollToLatest = onScrollToLatest,
                    onClearChat = { vm.clearChatForTab(tabId) },
                    onDisconnectServerClient = { vm.disconnectServerClientForTab(tabId) },
                    onStartPeriodicTest = { vm.startPeriodicTestForTab(tabId) },
                    onStopPeriodicTest = { vm.stopPeriodicTestForTab(tabId) },
                    onUpdatePeriodicInterval = { vm.updatePeriodicTestIntervalForTab(tabId, it) },
                    onUpdatePeriodicStopCondition = { vm.updatePeriodicTestStopConditionForTab(tabId, it) },
                    onUpdatePeriodicSendPayloadSize = { vm.updatePeriodicTestSendPayloadSizeForTab(tabId, it) },
                    onStartPingTest = { vm.startPingTestForTab(tabId) },
                    onStopPingTest = { vm.stopPingTestForTab(tabId) },
                    onUpdatePingTargetCount = { vm.updatePingTestTargetCountForTab(tabId, it) },
                    onUpdatePingInterval = { vm.updatePingTestIntervalForTab(tabId, it) },
                    onUpdatePingTimeout = { vm.updatePingTestTimeoutForTab(tabId, it) },
                    onUpdatePingPaddingSize = { vm.updatePingTestPaddingSizeForTab(tabId, it) },
                    onDisconnectDuringTest = { vm.disconnectDuringTestForTab(tabId) },
                    onSpeedTestWithCrcChange = { vm.updateSpeedTestWithCrcForTab(tabId, it) },
                    onSpeedTestTargetBytesChange = { vm.updateSpeedTestTargetBytesForTab(tabId, it) },
                    onSendPayloadSizeChange = { vm.updateSendPayloadSizeForTab(tabId, it) }
                )
            }
        }
    }

    // Handle back navigation for Server detail
    BackHandler(enabled = inDetail) {
        navController.navigateUp()
    }
}

/**
 * Resolved session detail data for ServerRoute.SessionDetail.
 */
private data class ResolvedSessionDetail(
    val session: SppSession?,
    val isHistory: Boolean,
    val notFound: Boolean = false
)

/**
 * Resolve which session data to display based on snapshotId.
 *
 * - "active" → use current sessionState (active session)
 *   - Rotation protection: if chat is empty but sessionHistory is non-empty,
 *     fall back to sessionHistory[0] and mark as history
 * - other → find snapshot by id in sessionHistory, construct a temporary SppSession
 */
private fun resolveSessionDetail(
    snapshotId: String,
    sessionState: SppSession?
): ResolvedSessionDetail {
    if (sessionState == null) {
        return ResolvedSessionDetail(session = null, isHistory = false, notFound = true)
    }

    return when (snapshotId) {
        "active" -> {
            val chat = sessionState.chat
            val history = sessionState.sessionHistory
            val isConnected = sessionState.connectionState == SppConnectionState.Connected
            if (!isConnected && chat.isEmpty() && history.isNotEmpty()) {
                // Rotation protection: active points to new empty session (not connected),
                // fall back to most recent history
                val snapshot = history[0]
                val tempSession = buildHistorySession(snapshot, sessionState)
                ResolvedSessionDetail(session = tempSession, isHistory = true)
            } else {
                ResolvedSessionDetail(session = sessionState, isHistory = false)
            }
        }
        else -> {
            val snapshot = sessionState.sessionHistory.find { it.id == snapshotId }
            if (snapshot != null) {
                val tempSession = buildHistorySession(snapshot, sessionState)
                ResolvedSessionDetail(session = tempSession, isHistory = true)
            } else {
                // Snapshot not found → navigate back
                ResolvedSessionDetail(session = null, isHistory = true, notFound = true)
            }
        }
    }
}

/**
 * Build a temporary SppSession from a ServerSessionSnapshot for history display.
 */
private fun buildHistorySession(
    snapshot: top.expli.bluetoothtester.model.ServerSessionSnapshot,
    sessionState: SppSession
): SppSession {
    return SppSession(
        device = sessionState.device.copy(
            address = snapshot.remoteDeviceAddress
        ),
        connectionState = SppConnectionState.Closed,
        remoteDeviceAddress = snapshot.remoteDeviceAddress,
        chat = snapshot.chat,
        speedTestTxAvgBps = snapshot.speedTestTxAvgBps,
        speedTestRxAvgBps = snapshot.speedTestRxAvgBps,
        speedTestSamples = snapshot.speedTestSamples
    )
}
