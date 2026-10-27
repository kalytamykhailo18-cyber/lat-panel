<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

require_login();

const PER_PAGE = 50;

$filter = (string)($_GET['status'] ?? 'all');
$search = trim((string)($_GET['q'] ?? ''));
$page = max(1, (int)($_GET['page'] ?? 1));

$where = [];
$params = [];
$now = now_utc();

switch ($filter) {
    case 'pending':
    case 'disabled':
        $where[] = 'd.status = ?';
        $params[] = $filter;
        break;
    case 'active':
        $where[] = "d.status = 'active' AND (d.expires_at IS NULL OR d.expires_at >= ?)";
        $params[] = $now;
        break;
    case 'expired':
        $where[] = "d.status = 'active' AND d.expires_at < ?";
        $params[] = $now;
        break;
    default:
        $filter = 'all';
}

$dnsFilter = (int)($_GET['dns'] ?? 0);
if ($dnsFilter) {
    $where[] = 'd.dns_id = ?';
    $params[] = $dnsFilter;
}

if ($search !== '') {
    $like = '%' . $search . '%';
    $idLike = '%' . strtoupper(preg_replace('/[^0-9A-Fa-f]/', '', $search)) . '%';
    $where[] = '(d.device_id LIKE ? OR d.customer_name LIKE ? OR d.xtream_username LIKE ? OR d.notes LIKE ?)';
    array_push($params, $idLike, $like, $like, $like);
}

$sqlWhere = $where ? 'WHERE ' . implode(' AND ', $where) : '';
$total = (int)q("SELECT COUNT(*) FROM devices d $sqlWhere", $params)->fetchColumn();
$pages = max(1, (int)ceil($total / PER_PAGE));
$page = min($page, $pages);
$offset = ($page - 1) * PER_PAGE;

$devices = q("SELECT d.*, s.name AS dns_name
              FROM devices d LEFT JOIN dns_servers s ON s.id = d.dns_id
              $sqlWhere
              ORDER BY d.last_seen_at DESC, d.id DESC
              LIMIT " . PER_PAGE . " OFFSET $offset", $params)->fetchAll();

function devices_url(array $override): string
{
    $q = array_merge(['status' => $_GET['status'] ?? 'all', 'q' => $_GET['q'] ?? '', 'dns' => $_GET['dns'] ?? '', 'page' => 1], $override);
    return 'devices.php?' . http_build_query(array_filter($q, fn($v) => $v !== '' && $v !== 'all' && $v !== 1));
}

render_header('Dispositivos', 'devices');
?>
<div class="page-head">
  <div><h1>Dispositivos <span class="muted" style="font-weight:500">(<?= $total ?>)</span></h1><p class="muted">Haz clic en un equipo para activarlo o editarlo.</p></div>
  <a class="btn btn-primary" href="device.php"><?= icon('plus', 'icon icon-sm') ?>Agregar ID</a>
</div>
<div class="toolbar">
  <div class="tabs">
    <?php foreach (['all' => 'Todos', 'pending' => 'Pendientes', 'active' => 'Activos', 'expired' => 'Vencidos', 'disabled' => 'Desactivados'] as $key => $label): ?>
      <a href="<?= e(devices_url(['status' => $key])) ?>" class="<?= $filter === $key ? 'active' : '' ?>"><?= $label ?></a>
    <?php endforeach; ?>
  </div>
  <form class="search" method="get">
    <?php if ($filter !== 'all'): ?><input type="hidden" name="status" value="<?= e($filter) ?>"><?php endif; ?>
    <?php if ($dnsFilter): ?><input type="hidden" name="dns" value="<?= $dnsFilter ?>"><?php endif; ?>
    <?= icon('search', 'icon icon-sm') ?>
    <input type="search" name="q" value="<?= e($search) ?>" placeholder="Buscar por ID, cliente o usuario…">
  </form>
</div>

<div class="table-wrap">
  <?php if (!$devices): ?>
    <div class="empty"><?= icon('search') ?><strong>No se encontraron dispositivos</strong>
      <?= $search !== '' || $filter !== 'all' || $dnsFilter ? '<a href="devices.php">Limpiar búsqueda y filtros</a>' : 'Cuando un cliente abre la app, su equipo aparece aquí.' ?>
    </div>
  <?php else: ?>
  <table>
    <thead><tr><th>ID del dispositivo</th><th>Estado</th><th>Conexión</th><th>Cliente</th><th>DNS</th><th>Usuario</th><th>Vence</th><th>Última conexión</th><th></th></tr></thead>
    <tbody>
    <?php foreach ($devices as $d): $fid = format_device_id($d['device_id']); ?>
    <tr class="row-link" data-href="device.php?id=<?= (int)$d['id'] ?>">
      <td><span class="id-cell mono"><?= e($fid) ?><?= copy_button($fid) ?></span></td>
      <td><?= status_badge(device_status($d)) ?></td>
      <td><?= online_badge($d['last_seen_at']) ?></td>
      <td><?= $d['customer_name'] ? e($d['customer_name']) : '<span class="muted">—</span>' ?></td>
      <td><?= $d['dns_name'] ? e($d['dns_name']) : '<span class="muted">—</span>' ?></td>
      <td><?= $d['xtream_username'] ? e($d['xtream_username']) : '<span class="muted">—</span>' ?></td>
      <td><?= $d['status'] === 'pending' ? '<span class="muted">—</span>' : ($d['expires_at'] ? rel_time($d['expires_at']) : '<span class="muted">Nunca</span>') ?></td>
      <td><?= rel_time($d['last_seen_at']) ?></td>
      <td style="text-align:right"><a class="btn btn-sm <?= $d['status'] === 'pending' ? 'btn-primary' : '' ?>" href="device.php?id=<?= (int)$d['id'] ?>"><?= icon($d['status'] === 'pending' ? 'zap' : 'edit', 'icon icon-sm') ?><?= $d['status'] === 'pending' ? 'Activar' : 'Editar' ?></a></td>
    </tr>
    <?php endforeach; ?>
    </tbody>
  </table>
  <?php endif; ?>
</div>

<?php if ($pages > 1): ?>
<div class="pager">
  <?php if ($page > 1): ?><a class="btn btn-sm" href="<?= e(devices_url(['page' => $page - 1])) ?>">‹ Anterior</a><?php endif; ?>
  <span>Página <?= $page ?> de <?= $pages ?></span>
  <?php if ($page < $pages): ?><a class="btn btn-sm" href="<?= e(devices_url(['page' => $page + 1])) ?>">Siguiente ›</a><?php endif; ?>
</div>
<?php endif; ?>
<?php render_footer();
