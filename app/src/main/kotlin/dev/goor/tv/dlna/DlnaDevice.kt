package dev.goor.tv.dlna

import org.jupnp.model.meta.RemoteService

data class DlnaDevice(
    val udn: String,
    val name: String,
    val service: RemoteService,
)
