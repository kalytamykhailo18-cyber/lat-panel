<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

require_login();

$now = now_utc();
$dayAgo = gmdate('Y-m-d H:i:s', time() - 86400);

/** Waiting list; also served alone (?partial=pending) for the auto-refresh. */
function render_pending(): void
{
    $pending = q("SELECT id, device_id, model, app_version, last_ip, first_seen_at, last_seen_at
                  FROM devices WHERE status = 'pending' ORDER BY last_seen_at DESC LIMIT 25")->fetchAll();
    if (!$pending) {
        echo '<div class="empty">' . icon('inbox') . '<strong>No hay equipos esperando</strong>'
           . 'Cuando un cliente abre la app, su ID aparece aquí en segundos, sin recargar la página.'
           . '<div class="steps"><span class="step"><b>1</b>El cliente abre la app</span>'
           . '<span class="step"><b>2</b>Su ID aparece aquí</span><span class="step"><b>3</b>Activar</span></div></div>';
        return;
    }
    echo '<table><thead><tr><th>ID del dispositivo</th><th>Conexión</th><th>Modelo</th><th>IP</th><th>Apareció</th><th></th></tr></thead><tbody>';
    foreach ($pending as $d) {
        $id = format_device_id($d['device_id']);
        echo '<tr class="row-link" data-id="' . (int)$d['id'] . '" data-href="device.php?id=' . (int)$d['id'] . '">'
           . '<td><span class="id-cell mono">' . e($id) . copy_button($id) . '</span></td>'
           . '<td>' . online_badge($d['last_seen_at']) . '</td>'
           . '<td>' . e($d['model']) . '</td>'
           . '<td class="mono muted">' . e($d['last_ip']) . '</td>'
           . '<td>' . rel_time($d['first_seen_at']) . '</td>'
           . '<td style="text-align:right"><a class="btn btn-primary btn-sm" href="device.php?id=' . (int)$d['id'] . '">' . icon('zap', 'icon icon-sm') . 'Activar</a></td></tr>';
    }
    echo '</tbody></table>';
}

if (($_GET['partial'] ?? '') === 'pending') {
    render_pending();
    exit;
}

$stats = [
    ['Pendientes', 'pending', 'var(--warn)', (int)q("SELECT COUNT(*) FROM devices WHERE status = 'pending'")->fetchColumn()],
    ['Activos', 'active', 'var(--ok)', (int)q("SELECT COUNT(*) FROM devices WHERE status = 'active' AND (expires_at IS NULL OR expires_at >= ?)", [$now])->fetchColumn()],
    ['Vencidos', 'expired', 'var(--danger)', (int)q("SELECT COUNT(*) FROM devices WHERE status = 'active' AND expires_at < ?", [$now])->fetchColumn()],
    ['Desactivados', 'disabled', 'var(--muted)', (int)q("SELECT COUNT(*) FROM devices WHERE status = 'disabled'")->fetchColumn()],
    ['Conectados (24 h)', 'all', 'var(--accent)', (int)q('SELECT COUNT(*) FROM devices WHERE last_seen_at >= ?', [$dayAgo])->fetchColumn()],
];

render_header('Inicio', 'index');
?>
<div class="page-head">
  <div><h1>Inicio</h1><p class="muted">Resumen de tus equipos y los que esperan activación.</p></div>
  <a class="btn" href="device.php"><?= icon('plus', 'icon icon-sm') ?>Agregar ID</a>
</div>

<div class="cards">
  <?php foreach ($stats as [$label, $filter, $color, $value]): ?>
    <a class="card" href="devices.php<?= $filter === 'all' ? '' : '?status=' . $filter ?>">
      <div class="label"><span class="dot" style="background:<?= $color ?>"></span><?= e($label) ?></div>
      <div class="value"><?= $value ?></div>
    </a>
  <?php endforeach; ?>
</div>

<div class="panel" style="padding:0;overflow:hidden">
  <div class="panel-head" style="padding:18px 22px 0">
    <h2>Esperando activación</h2>
    <span class="refresh-note" id="pending-note">Se actualiza solo</span>
  </div>
  <div class="table-wrap" style="border:0;box-shadow:none;border-radius:0;margin-top:12px" data-poll="index.php?partial=pending" data-poll-every="10" data-poll-note="#pending-note">
    <?php render_pending(); ?>
  </div>
</div>
<?php render_footer();
