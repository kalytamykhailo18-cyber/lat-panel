<?php
declare(strict_types=1);
// One-time installer: creates the tables and the first administrator.
// Delete the install/ folder from the server once it has run.

require_once __DIR__ . '/../includes/auth.php';

$hasTables = true;
try {
    $adminCount = (int)q('SELECT COUNT(*) FROM admins')->fetchColumn();
} catch (PDOException $ex) {
    $hasTables = false;
    $adminCount = 0;
}

if ($adminCount > 0) {
    http_response_code(403);
    exit('Ya está instalado. Elimina la carpeta install/ del servidor.');
}

$error = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $username = trim((string)($_POST['username'] ?? ''));
    $password = (string)($_POST['password'] ?? '');
    if (!preg_match('/^[A-Za-z0-9_.-]{3,64}$/', $username)) {
        $error = 'Usuario: de 3 a 64 letras, números, punto, guion o guion bajo.';
    } elseif (strlen($password) < 10) {
        $error = 'La contraseña debe tener al menos 10 caracteres.';
    } else {
        if (!$hasTables) {
            $sql = preg_replace('/^\s*--.*$/m', '', (string)file_get_contents(__DIR__ . '/schema.sql'));
            foreach (array_filter(array_map('trim', explode(';', $sql))) as $statement) {
                db()->exec($statement);
            }
        }
        q('INSERT INTO admins (username, password_hash, created_at) VALUES (?, ?, ?)',
          [$username, password_hash($password, PASSWORD_DEFAULT), now_utc()]);
        echo '<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="../assets/panel.css">'
           . '<main class="container"><div class="panel login-box"><h1>Instalado</h1>'
           . '<p>Ahora <strong>elimina la carpeta install/</strong> del servidor y <a href="../login.php">ingresa al panel</a>.</p></div></main>';
        exit;
    }
}
?>
<!doctype html>
<html lang="es">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Instalación</title><link rel="stylesheet" href="../assets/panel.css"></head>
<body><main class="container">
<div class="login-box panel">
  <h1>Instalar panel</h1>
  <p class="muted"><?= $hasTables ? 'Tablas encontradas.' : 'Se crearán las tablas de la base de datos.' ?> Crea el primer administrador.</p>
  <?php if ($error): ?><div class="alert"><div><?= e($error) ?></div></div><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?>
    <div><label for="username">Usuario administrador</label><input type="text" id="username" name="username" required></div>
    <div><label for="password">Contraseña (10 caracteres o más)</label><input type="password" id="password" name="password" minlength="10" required></div>
    <button class="btn btn-primary" type="submit">Instalar</button>
  </form>
</div>
</main></body></html>
