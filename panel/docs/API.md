# API App ↔ Panel

Versión 1. Endpoints JSON que usa la app; quedan documentados para automatizaciones futuras.

| Endpoint | Uso |
|----------|-----|
| `POST /api/v1/device.php` | Registro del equipo, estado de activación, credenciales asignadas y mensajes. |
| `POST /api/v1/dns.php` | Ingreso manual: lista ordenada de DNS que la app prueba uno por uno. |
| `/demo/player_api.php` | Proveedor demo compatible con Xtream Codes, con contenido libre de derechos. |

Los cuerpos se envían como JSON (`Content-Type: application/json`) o como formulario normal
(`application/x-www-form-urlencoded`). Todas las fechas son UTC en formato ISO 8601.

---

## 1. `POST /api/v1/device.php`

### Petición

| Campo         | Obligatorio | Descripción |
|---------------|-------------|-------------|
| `device_id`   | sí | 12 caracteres hexadecimales. Se ignoran `:` y `-`: `A1:B2:C3:D4:E5:F6` = `a1b2c3d4e5f6`. |
| `device_key`  | sí | Clave secreta generada por la app (16 a 128 caracteres). Se vincula al ID en el primer contacto. |
| `model`       | no | Modelo del equipo, por ejemplo `Amazon AFTMM`. Se muestra en el panel. |
| `app_version` | no | Versión de la app, por ejemplo `1.0.0`. |

```json
{ "device_id": "A1:B2:C3:D4:E5:F6", "device_key": "9f2c...e81a", "model": "Amazon AFTMM", "app_version": "1.0.0" }
```

### ID y clave del dispositivo

- La app deriva ambos valores del `ANDROID_ID` del sistema (ver `DeviceIdentity.kt`). No cambian al reiniciar,
  actualizar ni reinstalar la app; solo un restablecimiento de fábrica genera un ID nuevo.
- **No se usa la MAC**: desde Android 6 el sistema la oculta a las apps y muchas TVs la aleatorizan.
- La primera petición de un ID desconocido crea el equipo como `pending` y guarda el hash SHA-256 de `device_key`.
  Si luego llega el mismo ID con otra clave, responde `403 key_mismatch`: conocer el ID de un cliente no alcanza
  para obtener sus credenciales.
- **Reiniciar vinculación** (en la ficha del equipo) borra la clave guardada; el próximo equipo que use ese ID queda vinculado.

### Respuesta `200 OK`

```json
{
  "ok": true,
  "device_id": "A1:B2:C3:D4:E5:F6",
  "status": "active",
  "server_time": "2026-10-25T00:22:20+00:00",
  "poll_seconds": 900,
  "account": {
    "dns_name": "Proveedor Norte",
    "server_url": "http://line.proveedor.com:8080",
    "username": "cliente1",
    "password": "secreto",
    "expires_at": "2027-02-01T04:59:59+00:00"
  },
  "messages": [
    { "id": 12, "title": "Mantenimiento", "body": "El servicio se pausará de 02:00 a 03:00.", "created_at": "2026-10-25T00:22:20+00:00" }
  ]
}
```

| Campo          | Descripción |
|----------------|-------------|
| `status`       | `pending` (esperando activación), `active`, `disabled`, `expired` (activo pero con `expires_at` vencido). |
| `poll_seconds` | Cuándo volver a consultar. En `config.php`: `poll_pending_seconds` (10 por defecto) mientras no está activo y `poll_active_seconds` (900) cuando está activo. |
| `account`      | Solo cuando `status` es `active`; si no, `null`. `server_url` es el DNS asignado, que la app usa con la API estándar de Xtream Codes. `expires_at` es `null` si no vence. |
| `messages`     | Mensajes para este equipo y avisos generales de los últimos 30 días que no vencieron. La app muestra cada `id` una sola vez. |

### Errores

Siempre JSON: `{"ok": false, "error": "<código>", "message": "<texto>"}`

| HTTP | `error`              | Causa |
|------|----------------------|-------|
| 400  | `invalid_device_id`  | `device_id` no tiene 12 caracteres hexadecimales. |
| 400  | `invalid_device_key` | Falta `device_key` o su longitud no es válida. |
| 400  | `bad_request`        | Se declaró JSON pero el cuerpo no es JSON válido. |
| 403  | `key_mismatch`       | El ID está vinculado a otro equipo. Se soluciona con "Reiniciar vinculación". |
| 405  | `method_not_allowed` | No es POST. |
| 500  | `server_error`       | Error de base de datos o del servidor (detalle en el log de PHP). |

### Cómo lo usa la app

1. **Pantalla de activación:** consulta cada `poll_seconds` (10 s) mientras muestra el ID. Cuando `status` pasa a
   `active`, ingresa a `account.server_url` con `account.username` y `account.password`, sin que el usuario haga nada.
2. **Mientras el usuario mira:** consulta cada `poll_seconds` (15 min). Si el estado deja de ser `active`, vuelve a la
   pantalla de activación. Si cambiaste el DNS o las credenciales, la app pasa a los nuevos datos sola. Los mensajes
   nuevos aparecen en pantalla.
3. **Ingreso manual:** independiente de la activación. La app consulta solo para mostrar mensajes; el estado del ID no
   afecta a una sesión manual.

---

## 2. `POST /api/v1/dns.php`

Lista de DNS para el **ingreso manual**. El cliente escribe solo usuario y contraseña (la app no tiene campo de URL)
y **la app** prueba los DNS **uno por uno, en orden, con un timeout corto**, hasta que uno acepta la cuenta.
Es validación en el login, no failover: una vez conectada, la app se queda en ese DNS.

Petición: `device_id` y `device_key` (los mismos de `device.php`; un equipo desconocido se registra como pendiente).

Respuesta `200 OK`:

```json
{ "ok": true, "servers": ["http://line.proveedor-a.com:8080", "http://sur.proveedor-b.com:2095", "https://panel.premiumplus.lat/demo"], "timeout_ms": 1000 }
```

El orden es el campo **Orden** de cada DNS en el panel. `timeout_ms` es el tiempo máximo por DNS, configurable en
`config.php` → `manual_login_timeout_ms` (1000 = 1 segundo por defecto) sin necesidad de actualizar la app. Errores: `400` / `403 key_mismatch` / `500`, igual que `device.php`.

Cómo busca la app:

1. Primero prueba el DNS donde ese usuario ingresó la última vez (guardado en el equipo).
2. Luego el resto de la lista, **secuencialmente**: nunca hay dos consultas al mismo tiempo, para no activar los
   firewalls de los paneles Xtream.
3. Cada intento tiene un límite estricto de **1 segundo** (configurable): un servidor caído o colgado se descarta al
   instante. Medido con 4 DNS, uno caído y uno sin respuesta: la búsqueda completa tarda alrededor de 1 segundo.
4. Se queda con el primero que responde `user_info.auth = 1` y lo guarda para el próximo inicio.
5. En los inicios siguientes la app entra directo al DNS guardado, sin búsqueda. Si ese DNS deja de aceptar la cuenta (por ejemplo, el cliente pasó a otro proveedor), repite la búsqueda sola al iniciar.

Mientras busca, la pantalla muestra "Buscando tu servidor… (2 de 5)".

Las consultas a los proveedores salen **desde la conexión de cada cliente**, no desde el VPS: así la IP del panel
nunca acumula intentos de ingreso ante los proveedores.

---

## 3. Proveedor demo (contenido libre de derechos)

`/demo` es un pequeño servidor compatible con Xtream Codes incluido en el panel, con contenido de licencia abierta:
películas de la Blender Foundation (Creative Commons BY) y señales de prueba públicas. Sirve para la revisión de
Google Play y para demostraciones sin cuenta de proveedor.

- Se activa y se le pone contraseña en `config.php` → `demo`.
- Registra `https://panel.premiumplus.lat/demo` como DNS en el panel: el revisor ingresa por *Ingreso manual* solo con
  el usuario y la contraseña demo; la app lo encuentra en la lista como a cualquier otro DNS.
- Catálogo editable en `demo/content.php`. Las URLs de reproducción (`/demo/live|movie|series/{usuario}/{clave}/{id}.{ext}`)
  se reescriben a `demo/stream.php` (vía `demo/.htaccess`), que redirige al origen del video.

---

## Ejemplos con curl

```bash
curl -X POST https://panel.premiumplus.lat/api/v1/device.php \
  -H 'Content-Type: application/json' \
  -d '{"device_id":"A1B2C3D4E5F6","device_key":"0123456789abcdef0123","model":"prueba"}'

curl -X POST https://panel.premiumplus.lat/api/v1/dns.php -d 'device_id=A1B2C3D4E5F6&device_key=0123456789abcdef0123'
```

## Base de datos (para automatizaciones)

Las tablas están en `install/schema.sql`. Columnas útiles para scripts que activan equipos directamente:

- `dns_servers.sort_order`: orden en que la app prueba los DNS en el ingreso manual
- `devices.device_id`: 12 caracteres hexadecimales en mayúsculas, sin `:`
- `devices.status`: `pending` | `active` | `disabled`
- `devices.dns_id`: referencia a `dns_servers.id`
- `devices.xtream_username`, `devices.xtream_password`
- `devices.expires_at`: `DATETIME` en UTC, `NULL` = no vence
- `messages.device_id`: referencia a `devices.id`, `NULL` = aviso para todos

Activar un equipo = poner `status='active'`, `dns_id`, `xtream_username` y `xtream_password` (y opcionalmente
`expires_at`). La app lo detecta en su próxima consulta.
