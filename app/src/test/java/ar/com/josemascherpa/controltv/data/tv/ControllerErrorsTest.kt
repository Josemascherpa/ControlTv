package ar.com.josemascherpa.controltv.data.tv

import ar.com.josemascherpa.controltv.data.tv.roku.RokuController
import ar.com.josemascherpa.controltv.data.tv.sony.SonyController
import ar.com.josemascherpa.controltv.domain.FakeCredentials
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Intercepta HTTP en memoria: prueba los controladores reales sin necesitar una TV. */
class ControllerErrorsTest {
    private val device = TvDevice("192.168.1.10", "Prueba", TvBrand.ROKU)
    private fun http(code: Int, body: String = ""): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("Prueba").body(body.toResponseBody()).build()
    }.build()

    @Test(expected = TvError.RemoteControlDisabled::class)
    fun `Roku traduce acceso deshabilitado en ayuda de configuracion`(): Unit = runBlocking {
        RokuController(device, http(403)).connect()
    }

    @Test fun `Roku conserva el codigo de error HTTP`() = runBlocking {
        val error = runCatching { RokuController(device, http(500)).connect() }.exceptionOrNull()
        assertTrue(error is TvError.DeviceError)
        assertEquals(500, (error as TvError.DeviceError).code)
    }

    @Test fun `Roku despierta con PowerOn y no con el interruptor de apagado`() = runBlocking {
        var path: String? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            path = chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        RokuController(device, http).turnOn()
        assertEquals("/keypress/PowerOn", path)
    }

    @Test(expected = TvError.MissingKey::class)
    fun `Sony no intenta conectar sin clave`(): Unit = runBlocking {
        SonyController(device.copy(brand = TvBrand.SONY), FakeCredentials(), http(200)).connect()
    }

    @Test(expected = TvError.WrongKey::class)
    fun `Sony traduce rechazo de credencial`(): Unit = runBlocking {
        val credentials = FakeCredentials().apply { save(device.ip, "incorrecta") }
        SonyController(device.copy(brand = TvBrand.SONY), credentials, http(403)).connect()
    }
}
