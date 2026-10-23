package com.example.controltv

import android.app.Application
import com.example.controltv.di.AppContainer

/**
 * La clase Application se crea una sola vez, antes que cualquier pantalla, y vive mientras
 * la app esté abierta. Por eso es el lugar ideal para guardar el [AppContainer].
 * (Está registrada en AndroidManifest.xml con android:name.)
 */
class ControlTvApplication : Application() {

    // "lateinit" = se asigna después (en onCreate), pero antes de que alguien la use.
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
