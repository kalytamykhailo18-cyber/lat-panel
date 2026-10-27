<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

require_login();

// Live "who will receive this" preview for the form.
if (isset($_GET['recipients'])) {
    header('Content-Type: application/json; charset=utf-8');
    $raw = trim((string)($_GET['device'] ?? ''));
    if ($raw === '') {
        $n = (int)q('SELECT COUNT(*) FROM devices')->fetchColumn();
        echo json_encode(['ok' => true, 'text' => "Se enviará a todos los equipos ($n)."]);
    } else {
        $id = normalize_device_id($raw);
        $row = $id ? q('SELECT customer_name FROM devices WHERE device_id = ?', [$id])->fetch() : null;
        echo json_encode($row
            ? ['ok' => true, 'text' => 'Se enviará solo a ' . format_device_id($id) . ($row['customer_name'] ? ' (' . $row['customer_name'] . ')' : '') . '.']
            : ['ok' => false, 'text' => $id ? 'No hay ningún equipo con ese ID.' : 'Escribe el ID completo (12 caracteres) o deja vacío para todos.']);
    }
    exit;
}

$errors = [];
$form = ['title' => '', 'body' => '', 'device' => (string)($_GET['device'] ?? ''), 'expires_date' => ''];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = $_POST['action'] ?? '';

    if ($action === 'delete') {
        q('DELETE FROM messages WHERE id = ?', [(int)($_POST['id'] ?? 0)]);
        flash('ok', 'Mensaje eliminado.');
        redirect('messages.php');
    }

    if ($action === 'send') {
        $form = [
            'title'        => trim((string)($_POST['title'] ?? '')),
            'body'         => trim((string)($_POST['body'] ?? '')),
            'device'       => trim((string)($_POST['device'] ?? '')),
            'expires_date' => trim((string)($_POST['expires_date'] ?? '')),
        ];
        $deviceRowId = null;
        if ($form['title'] === '' || $form['body'] === '') {
            $errors[] = 'El título y el mensaje son obligatorios.';
        }
        if ($form['device'] !== '') {
            $deviceId = normalize_device_id($form['device']);
            $row = $deviceId ? q('SELECT id FROM devices WHERE device_id = ?', [$deviceId])->fetch() : null;
            if (!$row) {
                $errors[] = 'ID no encontrado. Déjalo vacío para enviar a todos los equipos.';
            } else {
                $deviceRowId = (int)$row['id'];
            }
        }
        $expires = local_date_to_utc_end_of_day($form['expires_date']);
        if ($form['expires_date'] !== '' && !$expires) {
            $errors[] = 'La fecha no es válida.';
        }
        if (!$errors) {
            q('INSERT INTO messages (device_id, title, body, expires_at, created_at) VALUES (?, ?, ?, ?, ?)',
              [$deviceRowId, $form['title'], $form['body'], $expires, now_utc()]);
            flash('ok', $deviceRowId ? 'Mensaje enviado al equipo.' : 'Mensaje enviado a todos los equipos.');
            redirect('messages.php');
        }
    }
}

$messages = q('SELECT m.*, d.device_id AS target FROM messages m LEFT JOIN devices d ON d.id = m.device_id
               ORDER BY m.created_at DESC LIMIT 200')->fetchAll();

render_header('Mensajes', 'messages');
?>
<div class="page-head">
  <div><h1>Mensajes</h1><p class="muted">Avisos que aparecen en la TV (mantenimiento, renovaciones, novedades). Llegan en la próxima conexión de la app, en pocos minutos, y cada equipo los ve una sola vez.</p></div>
</div>

<?php render_errors($errors); ?>

<div class="panel">
  <div class="panel-head"><h2>Nuevo mensaje</h2></div>
  <form method="post" class="grid">
    <?= csrf_field() ?>
    <input type="hidden" name="action" value="send">
    <div><label for="title">Título<span class="req">*</span></label><input type="text" id="title" name="title" maxlength="120" data-counter="60" value="<?= e($form['title']) ?>" placeholder="Mantenimiento programado" required></div>
    <div>
      <label for="device">Para un solo equipo (opcional)</label>
      <input type="text" id="device" name="device" class="mono" value="<?= e($form['device']) ?>" placeholder="Vacío = todos los equipos">
      <div class="hint" data-recipients="messages.php?recipients=1" data-recipients-input="device">&nbsp;</div>
    </div>
    <div>
      <label for="expires_date">Dejar de mostrar después del</label>
      <input type="date" id="expires_date" name="expires_date" value="<?= e($form['expires_date']) ?>">
      <div class="hint">Opcional. Pasada esa fecha, los equipos que no lo vieron ya no lo reciben.</div>
    </div>
    <div class="full"><label for="body">Mensaje<span class="req">*</span></label><textarea id="body" name="body" data-counter="300" placeholder="El servicio se pausará hoy de 02:00 a 03:00 por mejoras." required><?= e($form['body']) ?></textarea></div>
    <div class="full actions"><button class="btn btn-primary" type="submit" data-loading="Enviando…"><?= icon('send', 'icon icon-sm') ?>Enviar mensaje</button></div>
  </form>
</div>

<div class="panel" style="padding:0;overflow:hidden">
  <div class="panel-head" style="padding:18px 22px 0"><h2>Enviados</h2></div>
  <div class="table-wrap" style="border:0;box-shadow:none;border-radius:0;margin-top:12px">
    <?php if (!$messages): ?>
      <div class="empty"><?= icon('message') ?><strong>Todavía no enviaste mensajes</strong>Los mensajes enviados aparecerán aquí.</div>
    <?php else: ?>
    <table>
      <thead><tr><th>Enviado</th><th>Para</th><th>Título</th><th>Mensaje</th><th>Se muestra hasta</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($messages as $m): ?>
      <tr>
        <td><?= rel_time($m['created_at']) ?></td>
        <td><?= $m['target'] ? '<span class="mono">' . e(format_device_id($m['target'])) . '</span>' : '<span class="muted">Todos los equipos</span>' ?></td>
        <td><strong style="font-weight:600"><?= e($m['title']) ?></strong></td>
        <td class="wrap"><?= nl2br(e($m['body'])) ?></td>
        <td><?= $m['expires_at'] ? e(local_time($m['expires_at'], 'Y-m-d')) : '<span class="muted">Sin fecha</span>' ?></td>
        <td style="text-align:right">
          <form class="inline" method="post" data-confirm="¿Eliminar este mensaje?" data-confirm-text="Los equipos que todavía no lo vieron ya no lo recibirán." data-confirm-ok="Eliminar" data-danger>
            <?= csrf_field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$m['id'] ?>">
            <button class="btn btn-sm btn-danger" type="submit"><?= icon('trash', 'icon icon-sm') ?></button>
          </form>
        </td>
      </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
    <?php endif; ?>
  </div>
</div>
<?php render_footer();
