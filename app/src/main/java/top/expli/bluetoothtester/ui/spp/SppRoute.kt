package top.expli.bluetoothtester.ui.spp

import kotlinx.serialization.Serializable

@Serializable
sealed interface SppRoute {
    @Serializable
    data object List : SppRoute

    @Serializable
    data object ClientDetail : SppRoute

    @Serializable
    data object ServerDetail : SppRoute
}

sealed interface ServerRoute {
    @Serializable
    data object SessionList : ServerRoute

    @Serializable
    data class SessionDetail(val snapshotId: String) : ServerRoute
}
