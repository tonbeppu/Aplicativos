package com.movedados.witon.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Diz apenas se existe uma conexao Wi-Fi valida. Serve para o gate da tela
 * "Nova leitura" e para abortar a captura se a rede cair no meio do caminho.
 */
class WifiStateMonitor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isConnectedToWifi(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun wifiConnected(): Flow<Boolean> = callbackFlow {
        trySend(isConnectedToWifi())

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(isConnectedToWifi()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            }
        }

        cm.registerNetworkCallback(request, callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
