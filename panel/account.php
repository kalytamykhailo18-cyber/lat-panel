<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

$admin = require_login();
$errors = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = $_POST['action'] ?? '';

    if ($action === 'password') {
        $current = (string)($_POST['current'] ?? '');
        $new = (string)($_POST['new'] ?? '');
        $hash = q('SELECT password_hash FROM admins WHERE id = ?', [$admin['id']])->fetchColumn();
        if (!password_verify($current, (string)$hash)) {
            $errors[] = 'La contraseña actual no es correcta.';
        } elseif (strlen($new) < 10) {
            $errors[] = 'La nueva contraseña debe tener al menos 10 caracteres.';
        } elseif ($new !== ($_POST['confirm'] ?? '')) {
            $errors[] = 'Las contraseñas nuevas no coinciden.';
        } else {
            q('UPDATE admins SET password_hash = ? WHERE id = ?', [password_hash($new, PASSWORD_DEFAULT), $admin['id']]);
            flash('ok', 'Contraseña actualizada.');
            redirect('account.php');
        }
    }

    if ($action === 'add_admin') {
        $username = trim((string)($_POST['username'] ?? ''));
        $password = (string)($_POST['password'] ?? '');
        if (!preg_match('/^[A-Za-z0-9_.-]{3,64}$/', $username)) {
            $errors[] = 'Usuario: de 3 a 64 letras, números, punto, guion o guion bajo.';
        } elseif (strlen($password) < 10) {
            $errors[] = 'La contraseña debe tener al menos 10 caracteres.';
        } elseif (q('SELECT 1 FROM admins WHERE username = ?', [$username])->fetch()) {
            $errors[] = 'Ese usuario ya existe.';
        } else {
            q('INSERT INTO admins (username, password_hash, created_at) VALUES (?, ?, ?)',
              [$username, password_hash($password, PASSWORD_DEFAULT), now_utc()]);
            flash('ok', "Administrador \"$username\" creado.");
            redirect('account.php');
        }
    }

    if ($action === 'delete_admin') {
        $id = (int)($_POST['id'] ?? 0);
        if ($id === (int)$admin['id']) {
            $errors[] = 'No puedes eliminar tu propia cuenta.';
        } else {
            q('DELETE FROM admins WHERE id = ?', [$id]);
            flash('ok', 'Administrador eliminado.');
            redirect('account.php');
        }
    }
}

$admins = q('SELECT id, username, created_at, last_login_at FROM admins ORDER BY username')->fetchAll();

render_header('Cuenta', 'account');
$pw = fn(string $id, string $name, string $auto, bool $min = true) =>
    '<div class="pw"><input type="password" id="' . $id . '" name="' . $name . '" autocomplete="' . $auto . '"' . ($min ? ' minlength="10"' : '') . ' required><button type="button" class="pw-toggle">Mostrar</button></div>';
?>
<div class="page-head">
  <div><h1>Cuenta</h1><p class="muted">Tu contraseña y quién más puede administrar el panel.</p></div>
</div>

<?php render_errors($errors); ?>

<div class="panel">
  <div class="panel-head"><h2>Cambiar mi contraseña</h2></div>
  <form method="post" class="grid">
    <?= csrf_field() ?><input type="hidden" name="action" value="password">
    <div><label for="current">Contraseña actual</label><?= $pw('current', 'current', 'current-password', false) ?></div>
    <div><label for="new">Nueva contraseña</label><?= $pw('new', 'new', 'new-password') ?><div class="hint">Mínimo 10 caracteres.</div></div>
    <div><label for="confirm">Repetir nueva contraseña</label><?= $pw('confirm', 'confirm', 'new-password') ?></div>
    <div class="full"><button class="btn btn-primary" type="submit" data-loading="Guardando…"><?= icon('save', 'icon icon-sm') ?>Cambiar contraseña</button></div>
  </form>
</div>

<div class="panel">
  <div class="panel-head"><h2>Administradores</h2></div>
  <div class="table-wrap" style="box-shadow:none">
    <table>
      <thead><tr><th>Usuario</th><th>Creado</th><th>Último ingreso</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($admins as $a): ?>
      <tr>
        <td><strong style="font-weight:600"><?= e($a['username']) ?></strong><?= (int)$a['id'] === (int)$admin['id'] ? ' <span class="muted">(tú)</span>' : '' ?></td>
        <td><?= rel_time($a['created_at']) ?></td>
        <td><?= rel_time($a['last_login_at'], 'Nunca') ?></td>
        <td style="text-align:right">
          <?php if ((int)$a['id'] !== (int)$admin['id']): ?>
          <form class="inline" method="post" data-confirm="¿Eliminar a <?= e($a['username']) ?>?" data-confirm-text="Ya no podrá ingresar al panel." data-confirm-ok="Eliminar" data-danger>
            <?= csrf_field() ?><input type="hidden" name="action" value="delete_admin"><input type="hidden" name="id" value="<?= (int)$a['id'] ?>">
            <button class="btn btn-sm btn-danger" type="submit"><?= icon('trash', 'icon icon-sm') ?>Eliminar</button>
          </form>
          <?php endif; ?>
        </td>
      </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
  </div>
  <h2 style="margin-top:22px">Agregar administrador</h2>
  <form method="post" class="grid">
    <?= csrf_field() ?><input type="hidden" name="action" value="add_admin">
    <div><label for="username">Usuario</label><input type="text" id="username" name="username" autocomplete="off" required></div>
    <div><label for="password">Contraseña</label><?= $pw('password', 'password', 'new-password') ?><div class="hint">Mínimo 10 caracteres.</div></div>
    <div class="actions" style="align-items:flex-start;padding-top:24px"><button class="btn" type="submit" data-loading="Agregando…"><?= icon('plus', 'icon icon-sm') ?>Agregar</button></div>
  </form>
</div>
<?php render_footer();
