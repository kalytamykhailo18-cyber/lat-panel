<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

require_login();

$id = (int)($_GET['id'] ?? 0);
$device = null;
if ($id) {
    $device = q('SELECT * FROM devices WHERE id = ?', [$id])->fetch();
    if (!$device) {
        flash('error', 'Dispositivo no encontrado.');
        redirect('devices.php');
    }
}

// Live status for the "waiting for the device" box (polled by panel.js).
if ($device && ($_GET['format'] ?? '') === 'json') {
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode([
        'status' => device_status($device),
        'last_seen_iso' => $device['last_seen_at'] ? gmdate('c', strtotime($device['last_seen_at'] . ' UTC')) : null,
        'connected_after_activation' => $device['activated_at'] && $device['last_seen_at'] && $device['last_seen_at'] >= $device['activated_at'],
    ]);
    exit;
}

$dnsList = q('SELECT id, name, url FROM dns_servers ORDER BY sort_order, name')->fetchAll();
$errors = [];
$form = $device ?? ['device_id' => '', 'status' => 'active', 'dns_id' => null, 'xtream_username' => '',
                    'xtream_password' => '', 'expires_at' => null, 'customer_name' => '', 'notes' => ''];
if ($device && $device['status'] === 'pending') {
    $form['status'] = 'active';   // opening a pending device means "activate it"
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = $_POST['action'] ?? 'save';

    if ($device && $action === 'delete') {
        q('DELETE FROM devices WHERE id = ?', [$id]);
        flash('ok', 'Dispositivo ' . format_device_id($device['device_id']) . ' eliminado.');
        redirect('devices.php');
    }

    if ($device && $action === 'reset_key') {
        q('UPDATE devices SET device_key_hash = NULL WHERE id = ?', [$id]);
        flash('ok', 'Vinculación reiniciada. El próximo equipo que se conecte con este ID quedará vinculado.');
        redirect('device.php?id=' . $id);
    }

    $form = [
        'device_id'       => $device['device_id'] ?? normalize_device_id((string)($_POST['device_id'] ?? '')),
        'status'          => in_array($_POST['status'] ?? '', ['pending', 'active', 'disabled'], true) ? $_POST['status'] : 'pending',
        'dns_id'          => ($_POST['dns_id'] ?? '') !== '' ? (int)$_POST['dns_id'] : null,
        'xtream_username' => trim((string)($_POST['xtream_username'] ?? '')),
        'xtream_password' => trim((string)($_POST['xtream_password'] ?? '')),
        'expires_at'      => local_date_to_utc_end_of_day((string)($_POST['expires_date'] ?? '')),
        'customer_name'   => trim((string)($_POST['customer_name'] ?? '')),
        'notes'           => trim((string)($_POST['notes'] ?? '')),
    ];

    if (!$form['device_id']) {
        $errors[] = 'El ID debe tener 12 caracteres hexadecimales, por ejemplo A1:B2:C3:D4:E5:F6.';
    } elseif (!$device && q('SELECT 1 FROM devices WHERE device_id = ?', [$form['device_id']])->fetch()) {
        $errors[] = 'Ese ID ya está registrado.';
    }
    if ($form['dns_id'] !== null && !in_array($form['dns_id'], array_map('intval', array_column($dnsList, 'id')), true)) {
        $errors[] = 'Elige un DNS válido.';
    }
    if ($form['status'] === 'active' && ($form['dns_id'] === null || $form['xtream_username'] === '' || $form['xtream_password'] === '')) {
        $errors[] = 'Para activar un equipo asigna un DNS, un usuario y una contraseña.';
    }
    if (($_POST['expires_date'] ?? '') !== '' && $form['expires_at'] === null) {
        $errors[] = 'La fecha de vencimiento no es válida.';
    }

    if (!$errors) {
        $now = now_utc();
        $fields = [
            $form['status'], $form['dns_id'], $form['xtream_username'] ?: null, $form['xtream_password'] ?: null,
            $form['expires_at'], $form['customer_name'] ?: null, $form['notes'] ?: null,
        ];
        if ($device) {
            $activatedAt = $form['status'] === 'active' && $device['status'] !== 'active' ? $now : $device['activated_at'];
            q('UPDATE devices SET status = ?, dns_id = ?, xtream_username = ?, xtream_password = ?, expires_at = ?,
                  customer_name = ?, notes = ?, activated_at = ? WHERE id = ?',
              array_merge($fields, [$activatedAt, $id]));
        } else {
            q('INSERT INTO devices (status, dns_id, xtream_username, xtream_password, expires_at, customer_name, notes,
                  activated_at, device_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
              array_merge($fields, [$form['status'] === 'active' ? $now : null, $form['device_id'], $now]));
            $id = (int)db()->lastInsertId();
        }
        flash('ok', 'Dispositivo ' . format_device_id($form['device_id']) . ' guardado.'
            . ($form['status'] === 'active' ? ' La app ingresará sola en unos segundos.' : ''));
        redirect('device.php?id=' . $id);
    }
}

$expiresDate = $form['expires_at'] ? local_time($form['expires_at'], 'Y-m-d') : '';
$title = $device ? 'Dispositivo ' . format_device_id($device['device_id']) : 'Agregar ID de dispositivo';
// Show the live box for 30 minutes after an activation.
$justActivated = $device && $device['status'] === 'active' && $device['activated_at']
    && strtotime($device['activated_at'] . ' UTC') >= time() - 1800;
$connected = $justActivated && $device['last_seen_at'] && $device['last_seen_at'] >= $device['activated_at'];
render_header($title, 'devices');
?>
<?= back_link('devices.php', 'Dispositivos') ?>
<div class="page-head">
  <div>
    <h1 style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
      <?php if ($device): $fid = format_device_id($device['device_id']); ?>
        <span class="mono" style="font-size:20px"><?= e($fid) ?></span><?= copy_button($fid) ?> <?= status_badge(device_status($device)) ?> <?= online_badge($device['last_seen_at']) ?>
      <?php else: ?>Agregar ID de dispositivo<?php endif; ?>
    </h1>
    <p class="muted"><?= $device
        ? ($device['status'] === 'pending' ? 'Elige el DNS y escribe el usuario y la contraseña del cliente para activarlo.' : 'Cambia los datos y guarda; la app los toma en su próxima conexión.')
        : 'Registra un ID que el cliente te dictó; si lo activas ahora, la app ingresará sola apenas se abra.' ?></p>
  </div>
</div>

<?php if ($justActivated): ?>
  <?php if ($connected): ?>
    <div class="live-status ok"><span class="check"><?= icon('check', 'icon icon-sm') ?></span><div><strong>El equipo ya ingresó</strong><small>Se conectó <?= rel_time($device['last_seen_at']) ?>. El cliente ya puede ver el contenido.</small></div></div>
  <?php else: ?>
    <div class="live-status" data-live-status="device.php?id=<?= (int)$device['id'] ?>&amp;format=json"><span class="spinner"></span><div><strong>Esperando que el equipo se conecte…</strong><small>Si la app está abierta, ingresará sola en unos segundos. Esta página se actualiza sola.</small></div></div>
  <?php endif; ?>
<?php endif; ?>

<?php render_errors($errors); ?>

<?php if (!$dnsList): ?>
  <div class="alert alert-info"><?= icon('alert') ?><div>Todavía no hay DNS registrados. <a href="dns.php">Agrega un DNS</a> antes de activar equipos.</div></div>
<?php endif; ?>

<div class="panel">
  <form method="post" class="grid">
    <?= csrf_field() ?>
    <input type="hidden" name="action" value="save">
    <?php if (!$device): ?>
    <div class="full">
      <label for="device_id">ID del dispositivo<span class="req">*</span></label>
      <input type="text" id="device_id" name="device_id" class="mono" value="<?= e($form['device_id'] ? format_device_id($form['device_id']) : ($_POST['device_id'] ?? '')) ?>" placeholder="A1:B2:C3:D4:E5:F6" required autofocus>
      <div class="hint">Tal como aparece en la pantalla de la TV. Mayúsculas, minúsculas y separadores dan igual.</div>
    </div>
    <?php endif; ?>
    <div>
      <label for="status">Estado</label>
      <select id="status" name="status" data-required-when="active">
        <?php foreach (['active' => 'Activo', 'pending' => 'Pendiente', 'disabled' => 'Desactivado'] as $k => $v): ?>
          <option value="<?= $k ?>" <?= $form['status'] === $k ? 'selected' : '' ?>><?= $v ?></option>
        <?php endforeach; ?>
      </select>
    </div>
    <div>
      <label for="dns_id">DNS (servidor del proveedor)<span class="req">*</span></label>
      <select id="dns_id" name="dns_id" data-required-if-active>
        <option value="">— elegir —</option>
        <?php foreach ($dnsList as $s): ?>
          <option value="<?= (int)$s['id'] ?>" <?= (int)$form['dns_id'] === (int)$s['id'] ? 'selected' : '' ?>><?= e($s['name']) ?></option>
        <?php endforeach; ?>
      </select>
    </div>
    <div>
      <label for="xtream_username">Usuario<span class="req">*</span></label>
      <input type="text" id="xtream_username" name="xtream_username" value="<?= e($form['xtream_username']) ?>" autocomplete="off" data-required-if-active>
    </div>
    <div>
      <label for="xtream_password">Contraseña<span class="req">*</span></label>
      <input type="text" id="xtream_password" name="xtream_password" value="<?= e($form['xtream_password']) ?>" autocomplete="off" data-required-if-active>
    </div>
    <div class="full">
      <label for="expires_date">Vence el</label>
      <input type="date" id="expires_date" name="expires_date" value="<?= e($expiresDate) ?>" style="max-width:240px">
      <div class="presets" data-preset-for="expires_date">
        <button type="button" class="chip" data-months="1">1 mes</button>
        <button type="button" class="chip" data-months="3">3 meses</button>
        <button type="button" class="chip" data-months="6">6 meses</button>
        <button type="button" class="chip" data-months="12">1 año</button>
        <button type="button" class="chip <?= $expiresDate === '' ? 'on' : '' ?>">Sin vencimiento</button>
      </div>
      <div class="hint">Al vencer, la TV vuelve a la pantalla de activación con el aviso "Tu suscripción venció".</div>
    </div>
    <div>
      <label for="customer_name">Nombre del cliente</label>
      <input type="text" id="customer_name" name="customer_name" value="<?= e($form['customer_name']) ?>" placeholder="Para encontrarlo después">
    </div>
    <div>
      <label for="notes">Notas</label>
      <input type="text" id="notes" name="notes" value="<?= e($form['notes']) ?>" placeholder="Teléfono, plan, etc.">
    </div>
    <div class="full actions">
      <button class="btn btn-primary" type="submit" data-loading="Guardando…"><?= icon('save', 'icon icon-sm') ?><?= $device && $device['status'] === 'pending' ? 'Activar equipo' : 'Guardar' ?></button>
      <span class="hint" style="margin:0"><span style="color:var(--danger)">*</span> Obligatorio para activar.</span>
    </div>
  </form>
</div>

<?php if ($device): ?>
<div class="panel">
  <div class="panel-head"><h2>Datos del equipo</h2></div>
  <dl class="kv">
    <div><dt>Modelo</dt><dd><?= $device['model'] ? e($device['model']) : '—' ?></dd></div>
    <div><dt>Versión de la app</dt><dd><?= $device['app_version'] ? e($device['app_version']) : '—' ?></dd></div>
    <div><dt>Última conexión</dt><dd><?= rel_time($device['last_seen_at']) ?></dd></div>
    <div><dt>Última IP</dt><dd class="mono"><?= $device['last_ip'] ? e($device['last_ip']) : '—' ?></dd></div>
    <div><dt>Primera conexión</dt><dd><?= rel_time($device['first_seen_at']) ?></dd></div>
    <div><dt>Activado</dt><dd><?= rel_time($device['activated_at']) ?></dd></div>
    <div><dt>Vinculado a un equipo</dt><dd><?= $device['device_key_hash'] ? 'Sí' : 'No' ?></dd></div>
    <div><dt>Registrado</dt><dd><?= rel_time($device['created_at']) ?></dd></div>
  </dl>
  <div class="actions" style="margin-top:18px">
    <a class="btn" href="messages.php?device=<?= e(format_device_id($device['device_id'])) ?>"><?= icon('message', 'icon icon-sm') ?>Enviar mensaje</a>
    <form class="inline" method="post" data-confirm="¿Reiniciar la vinculación?" data-confirm-text="Úsalo si el cliente cambió de equipo o reinstaló la app y ve un error de vinculación. El próximo equipo que se conecte con este ID quedará vinculado." data-confirm-ok="Reiniciar">
      <?= csrf_field() ?><input type="hidden" name="action" value="reset_key">
      <button class="btn" type="submit"><?= icon('key', 'icon icon-sm') ?>Reiniciar vinculación</button>
    </form>
    <form class="inline" method="post" data-confirm="¿Eliminar este dispositivo?" data-confirm-text="Se borra el equipo y sus datos. Si la app sigue abierta, volverá a aparecer como pendiente." data-confirm-ok="Eliminar" data-danger>
      <?= csrf_field() ?><input type="hidden" name="action" value="delete">
      <button class="btn btn-danger" type="submit"><?= icon('trash', 'icon icon-sm') ?>Eliminar</button>
    </form>
  </div>
</div>
<?php endif; ?>
<?php render_footer();
