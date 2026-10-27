<?php
declare(strict_types=1);
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/layout.php';

require_login();

// "Probar": does the URL answer like an Xtream server, and how fast? One request without
// credentials, from this server; it never touches customer accounts.
if (isset($_GET['test'])) {
    header('Content-Type: application/json; charset=utf-8');
    $server = q('SELECT url FROM dns_servers WHERE id = ?', [(int)$_GET['test']])->fetch();
    if (!$server) {
        echo json_encode(['ok' => false, 'text' => 'DNS no encontrado.']);
        exit;
    }
    $ch = curl_init(rtrim($server['url'], '/') . '/player_api.php');
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_FOLLOWLOCATION => true, CURLOPT_MAXREDIRS => 3,
        CURLOPT_CONNECTTIMEOUT => 5, CURLOPT_TIMEOUT => 8, CURLOPT_USERAGENT => 'Mozilla/5.0 (Linux; Android 11) PanelCheck/1.0']);
    $start = microtime(true);
    $body = curl_exec($ch);
    $ms = (int)round((microtime(true) - $start) * 1000);
    $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $errno = curl_errno($ch);
    curl_close($ch);
    if ($body === false || $code === 0) {
        // Plain-language cause, with what to check.
        $why = match ($errno) {
            CURLE_COULDNT_RESOLVE_HOST => 'No se encuentra el dominio. Revisa que la URL esté bien escrita.',
            CURLE_COULDNT_CONNECT => 'El servidor rechaza la conexión. Revisa el puerto.',
            CURLE_OPERATION_TIMEDOUT => 'El servidor no responde (tiempo agotado). Puede estar caído o bloqueando conexiones.',
            CURLE_SSL_CONNECT_ERROR, CURLE_PEER_FAILED_VERIFICATION => 'Problema con el certificado HTTPS. Prueba con http:// si el proveedor lo indica.',
            default => 'No se pudo conectar.',
        };
        echo json_encode(['ok' => false, 'text' => $why]);
    } elseif (is_string($body) && (str_contains($body, 'user_info') || str_contains($body, '"auth"'))) {
        echo json_encode(['ok' => true, 'text' => "Responde como servidor Xtream · {$ms} ms"]);
    } else {
        echo json_encode(['ok' => false, 'text' => "Responde (HTTP {$code}, {$ms} ms), pero no parece un servidor Xtream. Revisa la URL y el puerto."]);
    }
    exit;
}

$editId = (int)($_GET['edit'] ?? 0);
$errors = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = $_POST['action'] ?? '';
    $id = (int)($_POST['id'] ?? 0);

    if ($action === 'save') {
        $name = trim((string)($_POST['name'] ?? ''));
        $url = normalize_server_url((string)($_POST['url'] ?? ''));
        $notes = trim((string)($_POST['notes'] ?? '')) ?: null;
        $order = (int)($_POST['sort_order'] ?? 0);
        if ($name === '') {
            $errors[] = 'El nombre es obligatorio.';
        }
        if (!$url) {
            $errors[] = 'La URL no es válida. Ejemplo: http://line.proveedor.com:8080';
        }
        if (!$errors) {
            if ($id) {
                q('UPDATE dns_servers SET name = ?, url = ?, notes = ?, sort_order = ? WHERE id = ?', [$name, $url, $notes, $order, $id]);
                flash('ok', "DNS \"$name\" actualizado. Los equipos asignados usan la nueva URL en su próxima conexión.");
            } else {
                q('INSERT INTO dns_servers (name, url, notes, sort_order, created_at) VALUES (?, ?, ?, ?, ?)', [$name, $url, $notes, $order, now_utc()]);
                flash('ok', "DNS \"$name\" agregado.");
            }
            redirect('dns.php');
        }
        $editId = $id;
    }

    if ($action === 'delete' && $id) {
        $assigned = (int)q('SELECT COUNT(*) FROM devices WHERE dns_id = ?', [$id])->fetchColumn();
        if ($assigned > 0) {
            flash('error', "Este DNS está asignado a $assigned equipo(s). Muévelos primero a otro DNS.");
        } else {
            q('DELETE FROM dns_servers WHERE id = ?', [$id]);
            flash('ok', 'DNS eliminado.');
        }
        redirect('dns.php');
    }

    if ($action === 'move') {
        $from = (int)($_POST['from'] ?? 0);
        $to = (int)($_POST['to'] ?? 0);
        if ($from && $to && $from !== $to && q('SELECT 1 FROM dns_servers WHERE id = ?', [$to])->fetch()) {
            $n = q('UPDATE devices SET dns_id = ? WHERE dns_id = ?', [$to, $from])->rowCount();
            flash('ok', "$n equipo(s) movidos.");
        } else {
            flash('error', 'Elige dos DNS distintos.');
        }
        redirect('dns.php');
    }
}

$servers = q('SELECT s.*, (SELECT COUNT(*) FROM devices d WHERE d.dns_id = s.id) AS device_count
              FROM dns_servers s ORDER BY s.sort_order, s.id')->fetchAll();

$editing = ['id' => 0, 'name' => '', 'url' => '', 'notes' => '', 'sort_order' => count($servers) + 1];
if ($editId) {
    foreach ($servers as $s) {
        if ((int)$s['id'] === $editId) {
            $editing = $s;
        }
    }
}
if ($errors) {
    $editing = array_merge($editing, ['name' => $_POST['name'] ?? '', 'url' => $_POST['url'] ?? '', 'notes' => $_POST['notes'] ?? '', 'sort_order' => (int)($_POST['sort_order'] ?? 0)]);
}

render_header('DNS', 'dns');
?>
<div class="page-head">
  <div><h1>DNS</h1><p class="muted" style="max-width:760px">Los servidores de tus proveedores. En la <b>activación por ID</b> eliges el DNS de cada equipo; en el <b>ingreso manual</b> la app los prueba uno por uno según el <b>Orden</b> y se queda con el que acepta al usuario. Si cambias una URL, todos sus equipos usan la nueva en su próxima conexión.</p></div>
</div>

<?php render_errors($errors); ?>

<div class="panel">
  <div class="panel-head"><h2><?= $editing['id'] ? 'Editar DNS' : 'Agregar DNS' ?></h2></div>
  <form method="post" class="grid">
    <?= csrf_field() ?>
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<?= (int)$editing['id'] ?>">
    <div><label for="name">Nombre<span class="req">*</span></label><input type="text" id="name" name="name" value="<?= e($editing['name']) ?>" placeholder="Proveedor A" required></div>
    <div><label for="url">URL con puerto<span class="req">*</span></label><input type="text" id="url" name="url" class="mono" value="<?= e($editing['url']) ?>" placeholder="http://line.proveedor.com:8080" required></div>
    <div><label for="sort_order">Orden en el ingreso manual</label><input type="text" inputmode="numeric" id="sort_order" name="sort_order" value="<?= (int)$editing['sort_order'] ?>"><div class="hint">1 se prueba primero.</div></div>
    <div><label for="notes">Notas</label><input type="text" id="notes" name="notes" value="<?= e($editing['notes']) ?>"></div>
    <div class="full actions">
      <button class="btn btn-primary" type="submit" data-loading="Guardando…"><?= icon($editing['id'] ? 'save' : 'plus', 'icon icon-sm') ?><?= $editing['id'] ? 'Guardar cambios' : 'Agregar DNS' ?></button>
      <?php if ($editing['id']): ?><a class="btn btn-ghost" href="dns.php">Cancelar</a><?php endif; ?>
    </div>
  </form>
</div>

<div class="table-wrap">
  <?php if (!$servers): ?>
    <div class="empty"><?= icon('server') ?><strong>Todavía no hay DNS</strong>Agrega la URL de tu primer proveedor arriba (por ejemplo <span class="mono">http://line.proveedor.com:8080</span>).</div>
  <?php else: ?>
  <table>
    <thead><tr><th>Orden</th><th>Nombre</th><th>URL</th><th>Equipos</th><th>Prueba de conexión</th><th>Notas</th><th></th></tr></thead>
    <tbody>
    <?php foreach ($servers as $s): ?>
    <tr>
      <td><?= (int)$s['sort_order'] ?></td>
      <td><strong style="font-weight:600"><?= e($s['name']) ?></strong></td>
      <td><span class="id-cell mono"><?= e($s['url']) ?><?= copy_button($s['url']) ?></span></td>
      <td><a href="devices.php?dns=<?= (int)$s['id'] ?>"><?= (int)$s['device_count'] ?> equipo<?= (int)$s['device_count'] === 1 ? '' : 's' ?></a></td>
      <td>
        <button class="btn btn-sm" type="button" data-dns-test="dns.php?test=<?= (int)$s['id'] ?>" data-out="test-<?= (int)$s['id'] ?>"><?= icon('wifi', 'icon icon-sm') ?>Probar</button>
        <span id="test-<?= (int)$s['id'] ?>" class="hint" style="margin-left:8px"></span>
      </td>
      <td class="wrap"><?= $s['notes'] ? e($s['notes']) : '<span class="muted">—</span>' ?></td>
      <td style="text-align:right">
        <span class="actions" style="justify-content:flex-end;flex-wrap:nowrap">
          <a class="btn btn-sm" href="dns.php?edit=<?= (int)$s['id'] ?>"><?= icon('edit', 'icon icon-sm') ?>Editar</a>
          <form class="inline" method="post" data-confirm="¿Eliminar el DNS <?= e($s['name']) ?>?" data-confirm-text="<?= (int)$s['device_count'] > 0 ? 'Tiene equipos asignados: muévelos antes a otro DNS.' : 'Esta acción no se puede deshacer.' ?>" data-confirm-ok="Eliminar" data-danger>
            <?= csrf_field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$s['id'] ?>">
            <button class="btn btn-sm btn-danger" type="submit"><?= icon('trash', 'icon icon-sm') ?></button>
          </form>
        </span>
      </td>
    </tr>
    <?php endforeach; ?>
    </tbody>
  </table>
  <?php endif; ?>
</div>

<?php if (count($servers) > 1): ?>
<div class="panel" style="margin-top:20px">
  <div class="panel-head"><h2>Mover equipos entre DNS</h2></div>
  <p class="muted">Pasa todos los equipos de un proveedor a otro de una vez, por ejemplo si un proveedor cambia o falla.</p>
  <form method="post" class="grid" data-confirm="¿Mover todos los equipos?" data-confirm-text="Todos los equipos del DNS de origen pasarán al de destino en su próxima conexión." data-confirm-ok="Mover">
    <?= csrf_field() ?>
    <input type="hidden" name="action" value="move">
    <div><label for="from">Desde</label><select id="from" name="from"><?php foreach ($servers as $s): ?><option value="<?= (int)$s['id'] ?>"><?= e($s['name']) ?> (<?= (int)$s['device_count'] ?>)</option><?php endforeach; ?></select></div>
    <div><label for="to">Hacia</label><select id="to" name="to"><?php foreach ($servers as $i => $s): ?><option value="<?= (int)$s['id'] ?>" <?= $i === 1 ? 'selected' : '' ?>><?= e($s['name']) ?></option><?php endforeach; ?></select></div>
    <div class="actions" style="align-items:end"><button class="btn" type="submit" data-loading="Moviendo…"><?= icon('move', 'icon icon-sm') ?>Mover todos los equipos</button></div>
  </form>
</div>
<?php endif; ?>
<?php render_footer();
