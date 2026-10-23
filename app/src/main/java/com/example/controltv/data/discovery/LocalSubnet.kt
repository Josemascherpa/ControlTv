package com.example.controltv.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/** Averigua en qué red WiFi está el teléfono. */
class LocalSubnet(private val context: Context) {

    /** Devuelve "192.168.1." a partir de la IP del teléfono, o null si no hay red. */
    fun prefix(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val address = cm.getLinkProperties(cm.activeNetwork)
            ?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: return null
        return address.substringBeforeLast('.') + "."
    }
}
