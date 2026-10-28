# Premium Plus · Fase 1

Dos entregables, ambos con código fuente 100 % propio: sin licencias de terceros, sin SDK externos, sin paneles cifrados.

| Carpeta    | Contenido |
|------------|-----------|
| `android/` | App para Android TV / Fire TV. Kotlin, Jetpack Compose for TV, Media3 ExoPlayer. Se abre y compila en Android Studio. |
| `panel/`   | Panel de administración + API. PHP 8 + MySQL, para tu VPS de Hostinger. |

## Requerimientos cubiertos

1. **Activación remota por ID de dispositivo.** Al abrir la app se muestra un ID estable (`AA:BB:CC:DD:EE:FF`).
   Cuando lo activas en el panel, la app ingresa sola en segundos. Sin PIN, sin códigos de activación, sin registro
   del cliente. El ID no es la MAC (Android oculta la MAC real a las apps): se genera a partir del identificador
   seguro del sistema y no cambia al reiniciar ni al reinstalar la app.
2. **Multi-DNS sin failover.** Registras todos los DNS de tus proveedores en el panel. En la activación por ID asignas
   el DNS de cada equipo; si editas un DNS, se actualiza en todos sus equipos, y puedes mover equipos entre DNS en bloque.
3. **Ingreso manual Xtream independiente.** El cliente ve solo Usuario y Contraseña (sin campo de URL). La app prueba
   tus DNS uno por uno, en el orden que definas en el panel y con un timeout corto, y lo conecta al que acepta la cuenta,
   sin asignación manual. Recuerda el DNS para el próximo inicio. Es validación en el login, no failover. La sesión
   manual se guarda aparte de la activación por ID y nunca se mezclan.
4. **Panel y API documentada.** Activar, desactivar y poner vencimiento a equipos; DNS; mensajes a un equipo o a todos;
   varios administradores. Referencia de la API: `panel/docs/API.md`.
5. **Rebranding y UI moderna para TV.** Nombre, package, panel, colores, logo, ícono y banner en pocos archivos
   (`android/docs/REBRANDING.md`). Interfaz pensada para control remoto, siempre en horizontal: menú lateral (Inicio,
   Buscar, TV en vivo, Guía TV, Películas, Series, Ajustes); inicio con portada destacada (con vista previa en vivo, sin sonido, del canal enfocado) y filas de continuar viendo,
   canales, películas y series recientes; TV en vivo en tres columnas con vista previa del canal y programación;
   guía EPG en cuadrícula por horas; películas y series con fichas; reproductor de películas y series con barra
   de progreso, audio, subtítulos, velocidad, formato de pantalla y episodio siguiente; reproductor de TV en vivo
   con banner de programación, lista de canales sobre la imagen, audio, subtítulos (también CC), formato de
   pantalla y favoritos; cambio de canal con el control (flechas, CH+/CH- y números);
   ajustes (cuenta, reproducción, tamaño e idioma de subtítulos, contenido, equipo) y control parental con PIN por
   categoría; búsqueda; español e inglés.

Además, para la revisión de Google Play: proveedor demo integrado en el panel con contenido libre de derechos
(`panel.premiumplus.lat/demo`) y página de política de privacidad (`panel.premiumplus.lat/privacy.php`).

## Capturas

Pantallas de la app (resolución de Fire TV, 1920×1080) y del panel en `docs/capturas/`.
Recursos para la ficha de Google Play en `store/`.

## Guías

- Panel: `panel/README.md`
- Compilar la app y publicar: `android/docs/BUILD.md`
- Rebranding: `android/docs/REBRANDING.md`
- Google Play: `android/docs/GOOGLE_PLAY.md`
