# Panel Premium Plus (PHP + MySQL)

Centro de control de la app: activar IDs de dispositivo, registrar los DNS de tus proveedores, asignar DNS y
credenciales a cada cliente y enviar mensajes a las TVs.

PHP 8.0+ y MySQL/MariaDB con PDO. Sin frameworks, sin Composer, sin servicios externos.

## Instalación en Hostinger (VPS o hosting)

1. **Base de datos:** hPanel > Bases de datos > MySQL. Crea una base de datos y un usuario.
2. **Subir archivos:** sube el contenido de esta carpeta `panel/` a la raíz del subdominio `panel.premiumplus.lat`.
3. **Configuración:** copia `config.sample.php` como `config.php` y completa base de datos, usuario, contraseña,
   zona horaria, correo de contacto y la contraseña del proveedor demo.
4. **Instalar:** abre `https://panel.premiumplus.lat/install/install.php` y crea el primer administrador.
   Esto crea las tablas.
5. **Borra la carpeta `install/`** del servidor.
6. **SSL:** activa el certificado gratuito en hPanel. La app se comunica con el panel por HTTPS.

En un VPS con nginx en lugar de Apache, bloquea los archivos internos y agrega la regla del demo
(con Apache lo hace el `.htaccess` incluido):

```nginx
location ~ ^/(config\.php|config\.sample\.php|includes/|install/schema\.sql|demo/content\.php|demo/demo\.php) { deny all; }
rewrite ^/demo/(live|movie|series)/([^/]+)/([^/]+)/([0-9]+)\.[A-Za-z0-9]+$ /demo/stream.php?type=$1&u=$2&p=$3&id=$4 last;
```

## Uso diario

| Sección       | Qué hace |
|---------------|----------|
| Inicio        | Resumen y equipos esperando activación (los más recientes primero). |
| Dispositivos  | Buscar por ID, cliente o usuario; filtrar por estado. **Activar:** elige DNS, usuario, contraseña y vencimiento opcional, y guarda. La TV ingresa sola en unos 10 segundos. |
| DNS           | Registra la URL de cada proveedor y su **Orden** para el ingreso manual. Editar una URL la actualiza en todos sus equipos. "Mover equipos" pasa todos los clientes de un DNS a otro. |
| Mensajes      | Envía un aviso a un ID o a todos los equipos, con fecha de fin opcional. |
| Cuenta        | Cambiar tu contraseña y administrar otros administradores. |

Un cliente puede dictarte su ID antes de abrir la app por primera vez: **Dispositivos > Agregar ID** lo deja
registrado y activado de antemano.

## Ingreso manual y proveedor demo

- En el ingreso manual de la app el cliente escribe solo usuario y contraseña (no hay campo de URL). La app prueba
  los DNS registrados aquí uno por uno, según el campo **Orden**, con un máximo de 1 segundo por DNS
  (`config.php` → `manual_login_timeout_ms`), y se queda con el que acepta al usuario. No hace falta asignar nada.
- Para la revisión de Google Play: define la contraseña en `config.php` → `demo` y agrega un DNS llamado "Demo" con
  la URL `https://panel.premiumplus.lat/demo`. A Google se le dan el usuario y la contraseña demo en
  Play Console > Contenido de la app > Acceso a la app.
- Política de privacidad pública: `https://panel.premiumplus.lat/privacy.php`.

## Seguridad

- Contraseñas de administradores cifradas (`password_hash`), sesiones HTTP-only y protección CSRF en todos los formularios.
- Bloqueo de 15 minutos tras 8 intentos fallidos de ingreso desde la misma IP.
- La búsqueda de DNS del ingreso manual la hace cada app desde la conexión del cliente, una consulta a la vez:
  la IP del VPS nunca acumula intentos ante los proveedores.
- Todas las consultas SQL usan sentencias preparadas.
- Cada ID queda vinculado a la clave secreta del primer equipo que lo usa: conocer el ID de un cliente no permite
  obtener sus credenciales.
- Las contraseñas Xtream se guardan en texto plano porque la app las necesita para conectarse al proveedor.

## API

Ver [docs/API.md](docs/API.md).
