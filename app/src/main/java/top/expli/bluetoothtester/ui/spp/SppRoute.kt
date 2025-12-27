package top.expli.bluetoothtester.ui.spp

import kotlinx.serialization.Serializable

@Serializable
sealed interface SppRoute {
    @Serializable
    data object List : SppRoute

    @Serializable
    data object Detail : SppRoute
}
