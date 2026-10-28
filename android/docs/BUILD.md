# Compilar la app

## Requisitos

- Android Studio (Narwhal o más reciente), **o** solo JDK 17+ y el Android SDK por línea de comandos.
- Android SDK Platform 36.

Abre la carpeta `android/` en Android Studio y presiona *Run*, o desde una terminal:

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

Instalar en un Fire TV Stick / Android TV por la red:

```bash
adb connect 192.168.1.50:5555    # Fire TV: Configuración > Mi Fire TV > Opciones de desarrollador > Depuración ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Cualquier valor de `brand.properties` se puede cambiar para una sola compilación, por ejemplo un panel de pruebas:

```bash
./gradlew assembleDebug -PPANEL_URL=http://192.168.1.10/panel/
```

## Versión de publicación (Google Play y Downloader)

1. Crea una sola vez la llave de firma y guárdala en un lugar seguro (si se pierde, hay que pedirle a Google que la reinicie):

   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Crea `keystore.properties` junto a `brand.properties` (no se sube a ningún repositorio):

   ```properties
   storeFile=upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

3. Compila:

   ```bash
   ./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab  (Google Play)
   ./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk     (Downloader / Fire TV)
   ```

La versión de publicación se optimiza con R8. `targetSdk` es 36, el que exige Google Play para apps nuevas.
Para cada actualización sube `VERSION_CODE` en 1 en `brand.properties`.

## Estructura del proyecto

```
app/src/main/java/com/player/tv/
├── App.kt, MainActivity.kt, Navigation.kt, AppViewModel.kt   arranque, navegación, monitoreo del panel
├── data/
│   ├── DeviceIdentity.kt   ID estable del dispositivo + clave secreta (no la MAC)
│   ├── PanelApi.kt         conexión con el panel: activación, DNS, credenciales, mensajes, detección de DNS
│   ├── XtreamClient.kt     API Xtream Codes: en vivo, películas, series, guía, URLs de reproducción
│   ├── SessionStore.kt     sesión por ID y sesión manual por separado, favoritos, continuar viendo, preferencias
│   ├── Parental.kt         control parental: categorías bloqueadas con PIN
│   └── Graph.kt            dependencias y sesión actual
└── ui/
    ├── splash/ activation/ login/      inicio, pantalla del ID, ingreso manual
    ├── home/ live/ vod/ series/        inicio y contenido (TV en vivo con vista previa)
    ├── guide/                          guía EPG en cuadrícula
    ├── search/ settings/               búsqueda; ajustes y control parental
    ├── player/                         reproductor ExoPlayer (Media3): controles de película y de TV en vivo, lista de canales, audio, subtítulos, velocidad, formato de pantalla
    ├── components/                     menú lateral, PIN y componentes de TV reutilizables
    └── theme/                          paleta de colores de la marca
```

Tecnologías: Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer (HLS y MPEG-TS),
OkHttp, kotlinx.serialization, Coil. Sin SDK de terceros, sin analítica, sin verificación de licencias.

## Capturas de pantalla automáticas

`app/src/test/.../ScreenshotTest.kt` genera capturas de todas las pantallas a resolución de Fire TV (1920×1080)
sin necesidad de un dispositivo, contra cualquier servidor Xtream:

```bash
SCREENSHOT_DIR=build/screens XTREAM_URL=http://servidor:8080 XTREAM_USER=usuario XTREAM_PASS=clave \
  ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
```
