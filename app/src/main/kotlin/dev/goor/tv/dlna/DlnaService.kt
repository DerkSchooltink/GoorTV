package dev.goor.tv.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jupnp.android.AndroidUpnpService
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry

private const val TAG = "DlnaService"

class DlnaService(private val context: Context) {

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private var upnpService: AndroidUpnpService? = null

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            val service = device.findService(UDAServiceType("AVTransport")) ?: return
            val entry = DlnaDevice(
                udn = device.identity.udn.identifierString,
                name = device.details?.friendlyName ?: device.displayString,
                service = service,
            )
            _devices.value = (_devices.value + entry).distinctBy { it.udn }
            Log.d(TAG, "Renderer found: ${entry.name}")
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            _devices.value = _devices.value.filter {
                it.udn != device.identity.udn.identifierString
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val upnp = (service as GoorUpnpService.ServiceBinder).service
            upnpService = upnp
            upnp.registry?.addListener(registryListener)
            upnp.controlPoint?.search()
            Log.d(TAG, "UPnP service connected, searching…")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            upnpService = null
        }
    }

    fun startDiscovery() {
        context.bindService(
            Intent(context, GoorUpnpService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun stopDiscovery() {
        upnpService?.registry?.removeListener(registryListener)
        runCatching { context.unbindService(serviceConnection) }
        upnpService = null
        _devices.value = emptyList()
    }

    fun castTo(device: DlnaDevice, url: String, title: String) {
        val upnp = upnpService ?: run {
            Log.w(TAG, "UPnP service not connected")
            return
        }
        val setUriAction = device.service.getAction("SetAVTransportURI") ?: run {
            Log.w(TAG, "SetAVTransportURI action not found on ${device.name}")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val setInvocation = ActionInvocation(setUriAction).apply {
            setInput("InstanceID", "0")
            setInput("CurrentURI", url)
            setInput("CurrentURIMetaData", buildDidlMetadata(url, title))
        }

        upnp.controlPoint.execute(object : ActionCallback(setInvocation) {
            override fun success(invocation: ActionInvocation<*>) {
                Log.d(TAG, "SetAVTransportURI succeeded, sending Play")
                val playAction = device.service.getAction("Play") ?: return
                @Suppress("UNCHECKED_CAST")
                val playInvocation = ActionInvocation(playAction).apply {
                    setInput("InstanceID", "0")
                    setInput("Speed", "1")
                }
                upnp.controlPoint.execute(object : ActionCallback(playInvocation) {
                    override fun success(invocation: ActionInvocation<*>) {
                        Log.d(TAG, "Playing on ${device.name}")
                    }
                    override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                        Log.e(TAG, "Play failed on ${device.name}: $defaultMsg")
                    }
                })
            }

            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e(TAG, "SetAVTransportURI failed on ${device.name}: $defaultMsg")
            }
        })
    }

    private fun buildDidlMetadata(url: String, title: String): String {
        val safeTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1"><dc:title>$safeTitle</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mpeg:*">$url</res></item></DIDL-Lite>"""
    }
}
