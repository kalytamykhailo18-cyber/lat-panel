# Publicación en Google Play

Guía para publicar Premium Plus desde una cuenta personal de desarrollador, con las respuestas listas para la consola.

## 1. Antes de subir

- [ ] Panel funcionando en `https://panel.premiumplus.lat/` con SSL.
- [ ] Proveedor demo activado en `config.php` → `demo` con una contraseña propia.
- [ ] DNS "Demo" registrado en el panel con la URL `https://panel.premiumplus.lat/demo` (sugerido: Orden 1 durante la
      revisión, para que el revisor ingrese al instante; después puede ir al final).
- [ ] Política de privacidad visible en `https://panel.premiumplus.lat/privacy.php` (agrega `contact_email` en `config.php`).
- [ ] Llave de firma creada y `keystore.properties` configurado (ver `BUILD.md`).
- [ ] `./gradlew bundleRelease` → `app-release.aab`.

## 2. Prueba cerrada obligatoria (cuentas personales)

Google exige para cuentas personales nuevas una prueba cerrada con **al menos 12 testers durante 14 días seguidos**
antes de poder solicitar producción (la publicación original pedía 20; con 20 se cubre de sobra).

1. *Pruebas > Prueba cerrada > Crear segmento*.
2. Sube el `.aab`, agrega la lista de correos de los testers (cuentas de Google) y publica la versión de prueba.
3. Comparte el enlace de participación; cada tester debe aceptar y **mantener la app instalada** los 14 días.
4. Al terminar, *Panel > Solicitar acceso a producción* y responde el cuestionario sobre la prueba.

## 3. Ficha de la tienda

**Nombre:** Premium Plus

**Descripción breve (máx. 80):**
> Reproductor multimedia para TV con activación remota y listas Xtream.

**Descripción completa:**
> Premium Plus es un reproductor multimedia moderno diseñado para Android TV, Fire TV y dispositivos Android.
>
> • Interfaz pensada para el control remoto, rápida y fluida.
> • Activación remota: muestra un ID en pantalla y tu proveedor activa el equipo sin que escribas nada.
> • Ingreso con el usuario y la contraseña de tu servicio.
> • Categorías, favoritos, guía de programación y cambio de canal con el control.
> • Películas y series con fichas, continuar viendo y reanudación automática.
> • Búsqueda en todo el contenido.
>
> Premium Plus no incluye ni vende contenido. Para usar la aplicación necesitas un servicio de contenido propio y
> autorizado. No somos responsables del contenido que reproduzca el usuario.

**Categoría:** Reproductores y editores de video. **Anuncios:** No.

**Recursos gráficos** (carpeta `store/` del proyecto):

| Recurso | Archivo | Tamaño |
|---------|---------|--------|
| Ícono | `store/icon-512.png` | 512×512 |
| Gráfico de funciones | `store/feature-graphic.png` | 1024×500 |
| Banner de Android TV | `store/tv-banner.png` | 1280×720 |
| Capturas de TV | `store/screens/*.png` | 1920×1080 |

Las capturas usan solo el contenido demo libre de derechos.

### Reglas para no ser rechazados

- No mencionar "IPTV", canales, deportes, ligas, estrenos ni marcas de terceros en la ficha ni en las capturas.
- No mostrar logos de canales ni carátulas de películas comerciales.
- No precargar listas ni DNS de contenido en la app (la app no trae ninguno).

## 4. Contenido de la app (cuestionarios)

**Acceso a la app:** *Toda o parte de la funcionalidad está restringida* → agregar instrucciones:
> 1. Abre la app y selecciona "Ingreso manual (Xtream)".
> 2. Usuario: `demo` — Contraseña: (la definida en config.php).
> 3. Selecciona "Ingresar". El contenido es de demostración y libre de derechos (Blender Foundation, CC BY).

**Anuncios:** No contiene anuncios.

**Clasificación de contenido:** categoría "Todas las demás"; responder No a violencia, contenido sexual, apuestas, etc.
La app no muestra contenido propio.

**Público objetivo:** 18 años o más. No dirigida a niños.

**Seguridad de los datos:**

| Pregunta | Respuesta |
|----------|-----------|
| ¿Recopila o comparte datos? | Sí, recopila. No comparte. |
| Identificadores de dispositivo | Sí: ID de la app. Finalidad: funcionalidad de la app. Obligatorio. |
| Información de la app y rendimiento | No. |
| Ubicación, contactos, fotos, audio, datos personales | No. |
| ¿Datos cifrados en tránsito? | Sí (HTTPS con el panel). |
| ¿El usuario puede pedir la eliminación? | Sí, a través de su proveedor / correo de contacto. |

**Política de privacidad:** `https://panel.premiumplus.lat/privacy.php`

**App de noticias / salud / préstamos / VPN:** No.

**Android TV:** en *Configuración avanzada > Factores de forma* agrega **Android TV** y sube el banner y las capturas de TV.
Google revisa por separado la versión de TV (navegación con control remoto, banner, sin pantalla táctil obligatoria);
la app ya cumple esos requisitos.

## 5. Si Google rechaza la app

Llega un correo con la política citada. Los motivos habituales y su corrección:

| Motivo | Corrección |
|--------|------------|
| Acceso a la app (no pudieron ingresar) | Verificar que el DNS demo esté registrado y las credenciales sean correctas. |
| Propiedad intelectual / contenido no autorizado | Revisar ficha y capturas según las reglas de la sección 3. |
| Metadatos engañosos | Quitar palabras como "gratis", "todos los canales", nombres de marcas. |
| Calidad de Android TV | Revisar que el banner y las capturas sean de TV. |

Corregir, subir una nueva versión (subir `VERSION_CODE`) y responder desde la consola.
