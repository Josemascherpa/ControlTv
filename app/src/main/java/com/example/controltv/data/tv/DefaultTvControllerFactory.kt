package com.example.controltv.data.tv

import com.example.controltv.data.tv.android.AndroidTvController
import com.example.controltv.data.tv.googletv.ClientIdentity
import com.example.controltv.data.tv.googletv.GoogleTvController
import com.example.controltv.data.tv.lg.LgController
import com.example.controltv.data.tv.roku.RokuController
import com.example.controltv.data.tv.samsung.SamsungController
import com.example.controltv.data.tv.sony.SonyController
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.TvControllerFactory
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import okhttp3.OkHttpClient
import java.io.File

/**
 * Acá se "decide qué usar": según la marca, creamos el controlador correcto.
 * Es el único lugar de la app que conoce todos los controladores; quien lo usa sólo ve un TvController.
 */
class DefaultTvControllerFactory(
    private val credentials: CredentialStore,
    private val http: OkHttpClient,
    private val webSocketClient: OkHttpClient,
    private val adbKeyDir: File,
    googleTvIdentityFile: File,
) : TvControllerFactory {

    // Una sola identidad para todas las Google TV (así cada TV recuerda a este teléfono).
    private val googleTvIdentity = ClientIdentity(googleTvIdentityFile)

    override fun create(device: TvDevice): TvController = when (device.brand) {
        TvBrand.SAMSUNG -> SamsungController(device, credentials, webSocketClient)
        TvBrand.LG -> LgController(device, credentials, webSocketClient)
        TvBrand.GOOGLE_TV -> GoogleTvController(device, googleTvIdentity)
        TvBrand.ANDROID -> AndroidTvController(device, adbKeyDir)
        TvBrand.ROKU -> RokuController(device, http)
        TvBrand.SONY -> SonyController(device, credentials, http)
    }
}
