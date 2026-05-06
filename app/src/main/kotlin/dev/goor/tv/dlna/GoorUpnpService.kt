package dev.goor.tv.dlna

import android.content.Intent
import android.os.IBinder
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl

class GoorUpnpService : AndroidUpnpServiceImpl() {

    inner class ServiceBinder : android.os.Binder() {
        val service: AndroidUpnpService get() = binder as AndroidUpnpService
    }

    private val serviceBinder = ServiceBinder()

    override fun onBind(intent: Intent): IBinder = serviceBinder
}
