<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

if (current_admin()) {
    redirect('index.php');
}

$error = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    if (login_blocked()) {
        $error = 'Demasiados intentos fallidos. Intenta de nuevo en ' . LOGIN_WINDOW_MINUTES . ' minutos.';
    } elseif (attempt_login(trim((string)($_POST['username'] ?? '')), (string)($_POST['password'] ?? ''))) {
        redirect('index.php');
    } else {
        $error = 'Usuario o contraseña incorrectos.';
    }
}

render_header('Ingresar');
$panel = config('panel_name', 'Panel');
?>
<div class="login-box">
  <div class="brand"><span class="brand-mark"><?= e(mb_strtoupper(mb_substr($panel, 0, 1))) ?></span><?= e($panel) ?></div>
  <div class="panel">
    <h1 style="font-size:18px;margin-bottom:4px">Ingresar al panel</h1>
    <p class="muted" style="margin-bottom:18px">Administra tus equipos, DNS y mensajes.</p>
    <?php if ($error): ?><?php render_errors([$error]); ?><?php endif; ?>
    <form method="post">
      <?= csrf_field() ?>
      <div><label for="username">Usuario</label><input type="text" id="username" name="username" autocomplete="username" value="<?= e((string)($_POST['username'] ?? '')) ?>" autofocus required></div>
      <div>
        <label for="password">Contraseña</label>
        <div class="pw"><input type="password" id="password" name="password" autocomplete="current-password" required><button type="button" class="pw-toggle">Mostrar</button></div>
      </div>
      <button class="btn btn-primary" type="submit" data-loading="Ingresando…">Ingresar</button>
    </form>
  </div>
</div>
<?php render_footer();
