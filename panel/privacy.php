<?php
declare(strict_types=1);
// Public privacy policy. Its URL ({panel}/privacy.php) goes in Google Play Console.

require_once __DIR__ . '/includes/bootstrap.php';

$app = config('panel_name', 'Premium Plus');
$contact = config('contact_email', '');
$updated = '2026-10-25';
?>
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Política de privacidad · <?= e($app) ?></title>
<link rel="stylesheet" href="assets/panel.css">
<style>.policy { max-width: 820px; } .policy p, .policy li { color: var(--text); line-height: 1.7; } .policy h2 { margin-top: 28px; }</style>
</head>
<body>
<main class="container policy">
  <h1>Política de privacidad de <?= e($app) ?></h1>
  <p class="muted">Última actualización: <?= e($updated) ?></p>

  <p><?= e($app) ?> es un reproductor multimedia para Android TV, Fire TV y dispositivos Android. La aplicación no incluye
  ni vende contenido: reproduce el contenido del servicio que el usuario tiene contratado con su propio proveedor.</p>

  <h2>Datos que tratamos</h2>
  <ul>
    <li><strong>Identificador del dispositivo:</strong> un código generado por la aplicación (no es la dirección MAC ni el IMEI)
      que se muestra en pantalla y permite al proveedor activar el equipo.</li>
    <li><strong>Datos técnicos:</strong> modelo del dispositivo, versión de la aplicación, dirección IP y fecha de la última conexión,
      usados para la activación y el soporte técnico.</li>
    <li><strong>Credenciales del servicio:</strong> usuario y contraseña del proveedor, guardados solo en el dispositivo cuando el usuario
      ingresa manualmente, o asignados por el proveedor en la activación por ID. En el ingreso manual se envían al servidor de
      activación únicamente para identificar el servidor del proveedor correspondiente.</li>
  </ul>

  <h2>Datos que no tratamos</h2>
  <p>No solicitamos nombre, correo, teléfono, ubicación, contactos, fotos ni micrófono. No mostramos publicidad, no usamos
  herramientas de analítica de terceros y no vendemos ni compartimos datos con terceros con fines comerciales.</p>

  <h2>Finalidad y conservación</h2>
  <p>Los datos se usan solo para activar el dispositivo, conectar al usuario con su proveedor, enviar avisos del servicio y dar
  soporte. Se conservan mientras el dispositivo esté registrado y se eliminan cuando el proveedor da de baja el equipo.</p>

  <h2>Seguridad</h2>
  <p>La comunicación con el servidor de activación se realiza mediante HTTPS. El acceso al panel de administración está protegido
  con contraseña.</p>

  <h2>Menores</h2>
  <p>La aplicación no está dirigida a menores de 13 años.</p>

  <h2>Derechos del usuario</h2>
  <p>El usuario puede solicitar la eliminación de los datos de su dispositivo contactando a su proveedor<?php if ($contact): ?>
  o escribiendo a <a href="mailto:<?= e($contact) ?>"><?= e($contact) ?></a><?php endif; ?>. Desinstalar la aplicación elimina
  todos los datos guardados en el dispositivo.</p>

  <h2>Cambios</h2>
  <p>Publicaremos en esta página cualquier cambio de esta política.</p>
</main>
</body>
</html>
