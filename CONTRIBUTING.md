# Contribuir a ControlTv

La prioridad es que cualquier persona, también una persona mayor, pueda usar la app sin ayuda.
Mantené los botones grandes, los nombres visibles y los mensajes en español de Argentina.

Las reglas de arquitectura y los pasos para agregar una marca están en [CLAUDE.md](CLAUDE.md).
El código está en `app/src/main/java/ar/com/josemascherpa/controltv`: `domain` contiene la lógica
sin Android; `data` implementa los protocolos y el almacenamiento; `ui` dibuja y recibe eventos.
La UI ofrece funciones según las interfaces del controlador, sin condiciones por marca.

Antes de enviar cambios, corré:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

El último comando necesita un emulador o teléfono. Las pruebas de interfaz usan una TV simulada;
al agregar o modificar un protocolo, anotá también el modelo y firmware de la TV física con la
que lo comprobaste, las funciones probadas y las limitaciones conocidas.

No incluyas claves de TVs, tokens, certificados personales, archivos de firma ni `local.properties`.
Si agregás una función, actualizá el README y quitá del listado de pendientes lo que ya esté hecho.
