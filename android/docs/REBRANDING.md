# Rebranding

Todo lo que identifica a la app está en pocos archivos. No hace falta tocar código.

## 1. `brand.properties` (raíz del proyecto)

| Clave            | Valor actual                   | Notas |
|------------------|--------------------------------|-------|
| `APP_NAME`       | `Premium Plus`                 | Nombre bajo el ícono y dentro de la app. |
| `APPLICATION_ID` | `lat.premiumplus.player`       | Package name de Google Play. **No se puede cambiar después de publicar.** |
| `PANEL_URL`      | `https://panel.premiumplus.lat/` | Tu panel, terminado en `/`. |
| `USER_AGENT`     | `PremiumPlus/1.0`              | Se envía a los servidores de los proveedores. |
| `VERSION_CODE`   | `1`                            | Súbelo en 1 en cada actualización. |
| `VERSION_NAME`   | `1.0.0`                        | Versión que ve el usuario. |

El paquete interno del código (`com.player.tv`) no se muestra en ningún lado; Google Play solo ve `APPLICATION_ID`.

## 2. Colores

- `app/src/main/java/com/player/tv/ui/theme/Brand.kt`: paleta completa (principal, secundario, fondos y los
  degradados de los accesos del inicio).
- `app/src/main/res/values/colors.xml`: los mismos colores principal, secundario y fondo para el ícono.

## 3. Logo, ícono y banner (`app/src/main/res/`)

| Archivo                               | Uso | Reemplazar por |
|---------------------------------------|-----|----------------|
| `drawable/logo.xml`                   | Logo dentro de la app (inicio, activación) | Tu logo en vector (Android Studio: *New > Vector Asset* desde un SVG) o bórralo y agrega `drawable/logo.png` (512×512). |
| `drawable-xhdpi/banner.png`           | Banner del launcher de Android TV / Fire TV | PNG de **640×360 px** con logo y nombre. |
| `drawable/ic_launcher_foreground.xml` | Ícono adaptativo (Android 8+) | Tu símbolo, centrado en el 66 % interior de un lienzo de 108×108. |
| `drawable-anydpi-v26/ic_launcher.xml` | Color de fondo del ícono | Cambia `@color/brand_primary` si hace falta. |

El logo y el banner actuales son provisorios hasta recibir el logo oficial.
En una misma carpeta no pueden convivir un `.png` y un `.xml` con el mismo nombre: borra uno.

## 4. Textos

Español (predeterminado) en `res/values/strings.xml`, inglés en `res/values-en/strings.xml`.

## 5. Compilar

```
./gradlew bundleRelease
```

Ver [BUILD.md](BUILD.md).
