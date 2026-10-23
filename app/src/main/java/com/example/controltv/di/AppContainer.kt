package com.example.controltv.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.controltv.ControlTvApplication
import com.example.controltv.data.discovery.LocalSubnet
import com.example.controltv.data.discovery.NetworkTvScanner
import com.example.controltv.data.local.PrefsCredentialStore
import com.example.controltv.data.local.PrefsLastDeviceStore
import com.example.controltv.data.local.PrefsSearchHistory
import com.example.controltv.data.network.NetworkClients
import com.example.controltv.data.tv.DefaultTvControllerFactory
import com.example.controltv.data.tv.android.AndroidTvDetector
import com.example.controltv.data.tv.lg.LgDetector
import com.example.controltv.data.tv.roku.RokuDetector
import com.example.controltv.data.tv.samsung.SamsungDetector
import com.example.controltv.data.tv.sony.SonyDetector
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.repository.SearchHistory
import com.example.controltv.domain.repository.TvDiscovery
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.ConnectToTvUseCase
import com.example.controltv.domain.usecase.WebSearchUseCase

/**
 * "Inyección de dependencias" a mano: acá se crean todas las piezas de la app y se conectan
 * entre sí. Es el único lugar que conoce las clases concretas de "data"; el resto de la app
 * recibe interfaces del dominio. Hay uno solo, creado por [ControlTvApplication].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentials: CredentialStore = PrefsCredentialStore(prefs)

    val lastDevice: LastDeviceStore = PrefsLastDeviceStore(prefs)
    val searchHistory: SearchHistory = PrefsSearchHistory(prefs)
    val session = TvSession()

    // El orden importa: gana el primer detector que reconoce la TV.
    val discovery: TvDiscovery = NetworkTvScanner(
        subnet = LocalSubnet(appContext),
        detectors = listOf(
            SamsungDetector(NetworkClients.http),
            LgDetector(),
            RokuDetector(NetworkClients.http),
            // Antes que Sony: si una Bravia (Google TV) tiene ADB activado, usamos ADB y no pide clave.
            AndroidTvDetector(),
            SonyDetector(NetworkClients.http),
        ),
    )

    val connectToTv = ConnectToTvUseCase(
        controllerFactory = DefaultTvControllerFactory(
            credentials = credentials,
            http = NetworkClients.http,
            webSocketClient = NetworkClients.webSocket,
            adbKeyDir = appContext.filesDir,
        ),
        credentials = credentials,
        lastDevice = lastDevice,
        session = session,
    )

    val webSearch = WebSearchUseCase(session, searchHistory)

    private companion object {
        const val PREFS_NAME = "tv_prefs"
    }
}

/** Para las fábricas de ViewModels: da acceso al contenedor de la app. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as ControlTvApplication).container
