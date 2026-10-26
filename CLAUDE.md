# ControlTv

App Android (Kotlin + Jetpack Compose) que usa el teléfono como control remoto de Smart TVs por WiFi.

## Prioridad número 1: que sea intuitiva, también para gente grande

La app la tiene que poder usar cualquiera sin explicaciones, pensando sobre todo en personas mayores
que no saben qué es una IP ni un "navegador".
 
- Que funcione sola: detectar las TVs automáticamente y reconectar a la última sin pedir nada.
- Letra grande y botones grandes, con el nombre escrito debajo del ícono (no sólo símbolos).
- Pocas opciones por pantalla. Separar en pestañas simples ("Control" e "Internet") en vez de
  amontonar todo.
- Pedirle algo a la persona sólo cuando no queda otra (ej. la clave de Sony), y en ese caso explicarle
  paso a paso dónde encontrarlo en la TV, con la ruta exacta del menú (`Ajustes > Red > …`).
- Los errores se muestran en lenguaje simple y siempre dicen **qué hacer** para arreglarlo,
  nunca un mensaje técnico ("timeout", "403", "socket").
- Si una TV no soporta algo (ej. Roku no tiene navegador), no mostrar el botón o explicarlo con un
  mensaje claro, en vez de fallar en silencio.

## Navegar en internet desde la TV

Es una función central de la app, no un extra. Tiene que ser muy fácil:

- Buscar escribiendo o **hablando** (botón de micrófono).
- Botones grandes y claros: "Buscar en Google", "Buscar en YouTube", "Abrir esta página".
- Accesos directos a páginas favoritas y las últimas búsquedas, para no tener que escribir de nuevo.
- Botones para subir/bajar la página y volver atrás, en la misma pantalla de internet.

## Idioma

Todos los textos de la app en **español de Argentina**: castellano con "vos" ("tocá", "aceptá",
"escribí"). Palabras comunes y claras, sin modismos ni lunfardo, y sin términos técnicos en inglés
cuando hay una palabra en castellano.

## Código

- Los comentarios están en español y explican conceptos de Kotlin/Android para alguien que está
  aprendiendo (qué es una corrutina, un `sealed interface`, etc.). Mantener ese estilo en código nuevo.
- Todo super ordenado y pensado para crecer: sumar una marca o una función nueva no debería obligar
  a tocar código que ya funciona.

### Principios SOLID

- **S (responsabilidad única):** cada clase hace una sola cosa. Ej: el escáner sólo detecta TVs,
  el controlador de cada marca sólo habla con esa TV, la pantalla sólo dibuja.
- **O (abierto/cerrado):** se extiende agregando clases nuevas, no metiendo más `if`/`when` por marca
  en código existente. El único lugar que conoce todas las marcas es la fábrica de controladores
  (y la detección).
- **L (sustitución):** cualquier `TvController` tiene que poder usarse en lugar de otro. Si una marca
  no soporta algo, lanza la excepción declarada para eso (no hace nada en silencio ni se cuelga).
- **I (interfaces chicas):** mejor varias interfaces específicas que una gigante con métodos que
  algunas marcas no pueden cumplir.
- **D (inversión de dependencias):** el ViewModel y la lógica dependen de interfaces, no de clases
  concretas. Las dependencias se reciben por el constructor (no se crean adentro), así se pueden
  reemplazar en tests.

### Clean Architecture

Tres capas, y las dependencias siempre apuntan hacia adentro (`ui` → `domain` ← `data`):

- **`domain`**: el corazón de la app, en Kotlin puro (sin nada de Android ni de red).
  Modelos (`TvDevice`, `TvBrand`, `RemoteKey`), interfaces (`TvController`, repositorios) y
  casos de uso (ej. `BuscarTvsUseCase`, `ConectarTvUseCase`) cuando la lógica no sea trivial.
- **`data`**: implementaciones concretas. Controladores por marca, escáner de red, preferencias
  guardadas, clientes HTTP/WebSocket/ADB/TLS.
- **`ui`**: ViewModels y pantallas de Compose. La pantalla no tiene lógica: recibe un estado y
  avisa eventos. El ViewModel sólo usa `domain`.

Regla: `domain` no importa nada de `data` ni de `ui`. Si hace falta, se define una interfaz en
`domain` y `data` la implementa.

Equilibrio: aplicar esto con sentido común. Nada de capas o clases que sólo pasen datos de un
lado a otro sin agregar nada.

### Librerías antes que código de bajo nivel

Para formatos y protocolos estándar (protobuf, certificados, criptografía, HTTP, WebSockets) usar
una librería conocida en vez de escribir a mano bytes, codificaciones o parsers. Nuestro código se
queda con la lógica de cada TV. Hoy: OkHttp, dadb, protobuf-lite (+ plugin que genera las clases
desde `app/src/main/proto`) y BouncyCastle. Antes de sumar una, elegir una versión publicada hace
al menos dos semanas.

### Asincronía (corrutinas)

- Todo lo que use red o disco es `suspend` y corre en `Dispatchers.IO` (con `ioCall` o `withContext`),
  nunca en el hilo principal. Excepción: SharedPreferences, que lee de memoria y guarda en segundo plano.
- Las corrutinas se lanzan desde el ViewModel con `viewModelScope`, no desde la UI ni con `GlobalScope`.
- Usar `async`/`awaitAll` sólo cuando haya trabajo en paralelo de verdad (ej. probar muchas IPs a la vez).
- Toda operación de red tiene tiempo límite (timeout del cliente o `withTimeoutOrNull`, que devuelve
  null en vez de lanzar una excepción de cancelación).
- Nunca atrapar `CancellationException` sin volver a lanzarla (rompe la cancelación de corrutinas).

### Excepciones

- Todos los errores son `TvError` (`domain/error/TvError.kt`, una `sealed class`): `Unreachable`,
  `ConnectionLost`, `PairingTimeout`, `WrongKey`, `NoBrowser`, `FeatureNotSupported`, etc.
  Si hace falta un caso nuevo, se agrega ahí.
- La capa `data` convierte los errores técnicos (`IOException`, códigos HTTP, errores de ADB, JSON)
  en `TvError`, usando `ioCall`/`call` de `data/network`. Nunca dejar escapar excepciones de librerías.
  Por eso los ViewModels sólo atrapan `TvError`.
- Los errores no llevan texto. La UI los traduce en `ui/messages/UserMessages.kt` a un mensaje
  simple que dice qué hacer (ver arriba).
- No tragarse errores con `runCatching { }.getOrNull()` salvo cuando "no hay resultado" sea lo
  esperado (ej. una IP que no es una TV durante la búsqueda), y dejarlo comentado.

### Tipado

- Todo bien tipado: nada de `Any`, ni `String` sueltos para cosas que tienen valores fijos
  (usar `enum` o `sealed`), ni `!!`.
- Tipos explícitos en funciones y propiedades públicas.
- Datos inmutables: `data class` con `val`, listas de solo lectura (`List`, no `MutableList`) hacia afuera.
- Estado de la UI con `sealed interface` para los casos posibles (ej. `ConnectionState`).

## Estructura del proyecto

```
controltv/
├── domain/                 Kotlin puro: no importa Android, ni data, ni ui
│   ├── model/              TvDevice, KnownTv, TvBrand (+ PairingMethod), RemoteKey, ScrollDirection, TvFeature
│   ├── error/              TvError
│   ├── controller/         TvController (básico) + TextInput, WebBrowser, ContentSearch, PowerOn,
│   │                       HardwareAddress (MAC para Wake-on-LAN); la fábrica
│   ├── repository/         TvDiscovery, CredentialStore, KnownTvs ("Mis TVs"), PairedDevices,
│   │                       SearchHistory, TvWaker
│   ├── session/            TvSession: la TV conectada ahora
│   ├── web/                WebLinks: arma URLs de búsqueda
│   └── usecase/            ConnectToTvUseCase, TurnOnTvUseCase, WebSearchUseCase
├── data/                   implementaciones concretas
│   ├── network/            clientes OkHttp, WebSockets, ioCall (conversión de errores), WakeOnLanSender
│   ├── local/              SharedPreferences (una clase por interfaz)
│   ├── discovery/          NetworkTvScanner (recorre la red) y la interfaz TvDetector
│   └── tv/<marca>/         <Marca>Controller + <Marca>Detector; DefaultTvControllerFactory
│                           (googletv/ además tiene el emparejamiento, la identidad y el canal TLS)
├── di/AppContainer.kt      crea y conecta todo (inyección de dependencias a mano)
└── ui/
    ├── messages/           UserMessage y sus textos
    ├── components/         botones y piezas comunes
    ├── discovery/          DiscoveryViewModel + DiscoveryScreen + KnownTvsList + DebuggingSetupGuide
    └── remote/             RemoteViewModel (+ RemoteActions), RemoteScreen, ControlTab, InternetTab
```

- Las funciones extra de cada TV salen de las interfaces que implementa su controlador
  (`TvController.features`). La UI muestra u oculta botones según eso, nunca según la marca.
- Tests de la lógica en `app/src/test/.../domain`, con versiones falsas en `Fakes.kt`.

### Agregar una marca nueva

1. Valor nuevo en `TvBrand` (`domain/model/TvBrand.kt`) con su `PairingMethod`.
2. En `data/tv/<marca>/`:
   - `<Marca>Controller`: implementa `TvController` y las interfaces extra que pueda cumplir
     (`TextInput`, `WebBrowser`, `ContentSearch`, `PointerInput`). Lanza sólo `TvError`.
     Para que se pueda prender desde el teléfono: `HardwareAddress` (dar su MAC) y, si en espera
     sigue atendiendo la red, `PowerOn`.
   - `<Marca>Detector`: implementa `TvDetector`.
3. Caso en `DefaultTvControllerFactory`.
4. Agregar el detector a la lista de `AppContainer` (el orden importa: gana el primero).
5. Escribir en `powerOnSetting()` (`UserMessages.kt`) dónde se activa en esa TV el encendido por WiFi.
6. Si usa un `PairingMethod` nuevo, escribir su ayuda en `connectingHint()` y sus mensajes de
   error en `UserMessages.kt` (el compilador avisa si falta alguno).

## Compilar

```
./gradlew :app:compileDebugKotlin   # compilar
./gradlew :app:testDebugUnitTest    # tests
```
