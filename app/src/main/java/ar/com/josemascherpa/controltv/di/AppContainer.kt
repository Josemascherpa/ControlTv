package ar.com.josemascherpa.controltv.di

import android.content.Context
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import ar.com.josemascherpa.controltv.ControlTvApplication
import ar.com.josemascherpa.controltv.data.discovery.LocalSubnet
import ar.com.josemascherpa.controltv.data.discovery.NetworkTvScanner
import ar.com.josemascherpa.controltv.data.discovery.SsdpDiscovery
import ar.com.josemascherpa.controltv.data.local.PrefsCredentialStore
import ar.com.josemascherpa.controltv.data.local.PrefsKnownTvs
import ar.com.josemascherpa.controltv.data.local.PrefsPairedDevices
import ar.com.josemascherpa.controltv.data.local.PrefsRemotePreferences
import ar.com.josemascherpa.controltv.data.local.PrefsSearchHistory
import ar.com.josemascherpa.controltv.data.network.NetworkClients
import ar.com.josemascherpa.controltv.data.network.WakeOnLanSender
import ar.com.josemascherpa.controltv.data.tv.android.AndroidTvDetector
import ar.com.josemascherpa.controltv.data.tv.DefaultTvControllerFactory
import ar.com.josemascherpa.controltv.data.tv.googletv.GoogleTvDetector
import ar.com.josemascherpa.controltv.data.tv.lg.LgDetector
import ar.com.josemascherpa.controltv.data.tv.roku.RokuDetector
import ar.com.josemascherpa.controltv.data.tv.samsung.SamsungDetector
import ar.com.josemascherpa.controltv.data.tv.sony.SonyDetector
import ar.com.josemascherpa.controltv.domain.repository.CredentialStore
import ar.com.josemascherpa.controltv.domain.repository.KnownTvs
import ar.com.josemascherpa.controltv.domain.repository.RemotePreferences
import ar.com.josemascherpa.controltv.domain.repository.SearchHistory
import ar.com.josemascherpa.controltv.domain.repository.TvDiscovery
import ar.com.josemascherpa.controltv.domain.session.TvSession
import ar.com.josemascherpa.controltv.domain.usecase.ConnectToTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.TurnOnTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.WebSearchUseCase
import java.io.File

/**
 * "Inyección de dependencias" a mano: acá se crean todas las piezas de la app y se conectan
 * entre sí. Es el único lugar que conoce las clases concretas de "data"; el resto de la app
 * recibe interfaces del dominio. Hay uno solo, creado por [ControlTvApplication].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentials: CredentialStore = PrefsCredentialStore(prefs)
    private val subnet = LocalSubnet(appContext)

    val knownTvs: KnownTvs = PrefsKnownTvs(prefs)
    val searchHistory: SearchHistory = PrefsSearchHistory(prefs)
    val remotePreferences: RemotePreferences =
        PrefsRemotePreferences(prefs)
    val session = TvSession()

    // El orden importa: gana el primer detector que reconoce la TV.
    val discovery: TvDiscovery = NetworkTvScanner(
        subnet = subnet,
        announcedAddresses = SsdpDiscovery()::addresses,
        detectors = listOf(
            SamsungDetector(NetworkClients.http),
            LgDetector(),
            RokuDetector(NetworkClients.http),
            // Google TV antes que ADB y Sony: no necesita modo desarrollador ni clave (las Bravia
            // nuevas son Google TV). ADB queda para Fire TV y Android TV viejas.
            GoogleTvDetector(),
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
            googleTvIdentityFile = File(appContext.filesDir, GOOGLE_TV_IDENTITY_FILE),
        ),
        credentials = credentials,
        knownTvs = knownTvs,
        pairedDevices = PrefsPairedDevices(prefs),
        session = session,
    )

    val turnOnTv = TurnOnTvUseCase(
        waker = WakeOnLanSender(subnet),
        knownTvs = knownTvs,
        connectToTv = connectToTv,
        session = session,
    )

    val webSearch = WebSearchUseCase(session, searchHistory)

    private companion object {
        const val PREFS_NAME = "tv_prefs"
        const val GOOGLE_TV_IDENTITY_FILE = "googletv_identity.p12"
    }
}

/** Para las fábricas de ViewModels: da acceso al contenedor de la app. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as ControlTvApplication).container
