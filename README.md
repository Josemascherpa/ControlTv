# ControlTv 📺

App de Android que convierte el teléfono en el **control remoto de tu Smart TV**, por WiFi.

## La idea

Los controles de las Smart TV son chicos, tienen demasiados botones y escribir con ellos es un
suplicio: buscar algo en YouTube o entrar a una página de internet letra por letra con las flechas
no lo hace nadie. Y menos una persona mayor.

ControlTv busca resolver eso con una app **muy simple e intuitiva**, pensada también para gente
grande:

- **Encuentra la TV sola.** Abrís la app y busca las TVs de tu WiFi. La próxima vez reconecta con un toque.
- **Botones grandes y con nombre.** Nada de íconos para adivinar.
- **Internet fácil.** Buscás en Google o YouTube escribiendo en el teléfono, o directamente
  **hablando** 🎤. Hay páginas favoritas a un toque, las últimas búsquedas guardadas y botones para
  subir y bajar la página.
- **Mensajes claros.** Si algo falla, la app explica qué hacer en castellano simple, sin términos técnicos.

## Marcas compatibles

| Marca | Cómo se conecta | Qué hay que hacer la primera vez |
|---|---|---|
| **Samsung** (Tizen, 2016+) | WebSocket, puertos 8001/8002 | Aceptar el mensaje "¿Permitir?" en la TV |
| **LG** (webOS) | WebSocket SSAP, puertos 3000/3001 | Aceptar el mensaje en la TV |
| **Google TV / Android TV / Fire TV** | ADB por red, puerto 5555 | Activar la depuración y aceptar "¿Permitir depuración?" |
| **Roku** (TCL, Hisense, onn, Philco…) | HTTP ECP, puerto 8060 | Activar *Control por apps móviles* en los ajustes |
| **Sony Bravia** | HTTP IP Control + IRCC, puerto 80 | Elegir una clave en *Ajustes > Red > Control IP* y escribirla en la app |

Las marcas se eligieron según las más vendidas (Samsung, TCL, Hisense, LG). Muchas TCL, Hisense
y otras traen Roku TV o Google TV adentro, así que quedan cubiertas.

> Roku no tiene navegador de internet: en esas TVs la app usa el buscador propio de la TV.

## Cómo está hecho

- **Kotlin + Jetpack Compose**, corrutinas para todo lo que usa red.
- **Clean Architecture** en tres capas, con principios **SOLID**:
  - `domain`: la lógica, en Kotlin puro (modelos, errores, interfaces, casos de uso).
  - `data`: cómo se habla con cada marca, la búsqueda en la red y lo guardado en el teléfono.
  - `ui`: pantallas y ViewModels.
- Cada marca vive en su propia carpeta (`data/tv/<marca>`) con su controlador y su detector,
  así sumar una marca nueva no obliga a tocar el resto.
- Errores tipados (`TvError`) que la UI traduce a mensajes simples.

Las reglas completas del proyecto (estilo, arquitectura, idioma, cómo agregar una marca) están en
[`CLAUDE.md`](CLAUDE.md).

## Compilar y probar

Abrir el proyecto en Android Studio, o desde la terminal:

```bash
./gradlew :app:assembleDebug        # genera el APK
./gradlew :app:testDebugUnitTest    # corre los tests
```

El teléfono y la TV tienen que estar en la **misma red WiFi**.

## Próximos pasos

- Más marcas: Hisense VIDAA, Vizio, Philips.
- Prender la TV cuando está apagada del todo (Wake-on-LAN).
- Elegir uno mismo las páginas favoritas.
