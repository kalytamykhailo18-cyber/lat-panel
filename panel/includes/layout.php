<?php
declare(strict_types=1);

/** Minutes since the last check-in under which a device counts as "online" (the app checks every 15 min). */
const ONLINE_MINUTES = 20;

/** Inline SVG icon (Lucide-style strokes). */
function icon(string $name, string $class = 'icon'): string
{
    static $paths = [
        'home'     => '<path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"/>',
        'tv'       => '<rect x="3" y="6" width="18" height="13" rx="2"/><path d="m8 2 4 4 4-4"/>',
        'server'   => '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
        'message'  => '<path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
        'user'     => '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
        'logout'   => '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3"/>',
        'plus'     => '<path d="M12 5v14M5 12h14"/>',
        'search'   => '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
        'copy'     => '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/>',
        'back'     => '<path d="M15 18l-6-6 6-6"/>',
        'save'     => '<path d="M20 6 9 17l-5-5"/>',
        'send'     => '<path d="m22 2-7 20-4-9-9-4z"/><path d="M22 2 11 13"/>',
        'trash'    => '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/>',
        'key'      => '<circle cx="7.5" cy="15.5" r="4.5"/><path d="m11 12 9-9M16 7l3 3"/>',
        'edit'     => '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
        'zap'      => '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>',
        'move'     => '<path d="M5 12h14M13 6l6 6-6 6"/>',
        'menu'     => '<path d="M4 6h16M4 12h16M4 18h16"/>',
        'inbox'    => '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5h13L22 12v7a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-7z"/>',
        'clock'    => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>',
        'wifi'     => '<path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"/>',
        'alert'    => '<circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16h.01"/>',
        'check'    => '<path d="M20 6 9 17l-5-5"/>',
    ];
    return '<svg class="' . e($class) . '" viewBox="0 0 24 24" aria-hidden="true">' . ($paths[$name] ?? '') . '</svg>';
}

function render_header(string $title, string $active = ''): void
{
    $admin = current_admin();
    $panel = config('panel_name', 'Panel');
    $initial = mb_strtoupper(mb_substr($panel, 0, 1));
    $nav = [
        'index'    => ['index.php', 'Inicio', 'home'],
        'devices'  => ['devices.php', 'Dispositivos', 'tv'],
        'dns'      => ['dns.php', 'DNS', 'server'],
        'messages' => ['messages.php', 'Mensajes', 'message'],
        'account'  => ['account.php', 'Cuenta', 'user'],
    ];
    $favicon = "data:image/svg+xml," . rawurlencode('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#4f8cff"/><stop offset="1" stop-color="#8a5cff"/></linearGradient></defs><rect width="32" height="32" rx="8" fill="url(#g)"/><text x="16" y="22" font-family="Arial" font-weight="700" font-size="17" fill="#fff" text-anchor="middle">' . htmlspecialchars($initial) . '</text></svg>');
    ?>
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#0c1017">
<title><?= e($title) ?> · <?= e($panel) ?></title>
<link rel="icon" href="<?= $favicon ?>">
<link rel="stylesheet" href="assets/panel.css?v=2">
</head>
<body>
<?php if ($admin): ?>
<header class="topbar">
  <a class="brand" href="index.php"><span class="brand-mark"><?= e($initial) ?></span><?= e($panel) ?></a>
  <button class="menu-btn" aria-label="Menú" aria-expanded="false"><?= icon('menu') ?></button>
  <nav>
    <?php foreach ($nav as $key => [$href, $label, $ic]): ?>
      <a href="<?= $href ?>" class="<?= $key === $active ? 'active' : '' ?>"><?= icon($ic, 'icon icon-sm') ?><?= $label ?></a>
    <?php endforeach; ?>
  </nav>
  <span class="who"><span class="who-name"><?= e($admin['username']) ?></span><a href="logout.php" title="Cerrar sesión"><?= icon('logout', 'icon icon-sm') ?>Salir</a></span>
</header>
<?php endif; ?>
<main class="container">
    <?php
}

function render_footer(): void
{
    $flashes = take_flashes();
    if ($flashes) {
        echo '<div class="toasts">';
        foreach ($flashes as [$type, $msg]) {
            echo '<div class="toast toast-' . e($type) . '">' . icon($type === 'ok' ? 'check' : 'alert')
                . '<div class="toast-body">' . e($msg) . '</div><button class="toast-close" aria-label="Cerrar">×</button></div>';
        }
        echo '</div>';
    }
    echo "</main>\n<script src=\"assets/panel.js?v=2\"></script>\n</body>\n</html>\n";
}

/** Inline form errors (validation), shown where the user is looking. */
function render_errors(array $errors): void
{
    foreach ($errors as $err) {
        echo '<div class="alert">' . icon('alert') . '<div>' . e($err) . '</div></div>';
    }
}

function status_badge(string $status): string
{
    $labels = ['active' => 'Activo', 'pending' => 'Pendiente', 'disabled' => 'Desactivado', 'expired' => 'Vencido'];
    return '<span class="badge badge-' . e($status) . '">' . e($labels[$status] ?? $status) . '</span>';
}

/** "hace 5 min" (filled in by panel.js), with the exact local date on hover. */
function rel_time(?string $utc, string $empty = '—'): string
{
    if (!$utc) {
        return '<span class="muted">' . e($empty) . '</span>';
    }
    $iso = gmdate('c', strtotime($utc . ' UTC'));
    $exact = local_time($utc);
    return '<time datetime="' . e($iso) . '" data-rel title="' . e($exact) . '">' . e($exact) . '</time>';
}

/** Green pulsing dot when the device checked in recently. */
function online_badge(?string $lastSeenUtc): string
{
    $on = $lastSeenUtc && strtotime($lastSeenUtc . ' UTC') >= time() - ONLINE_MINUTES * 60;
    return '<span class="online' . ($on ? ' on' : '') . '">' . ($on ? 'En línea' : 'Desconectado') . '</span>';
}

function copy_button(string $text): string
{
    return '<button type="button" class="copy" data-copy="' . e($text) . '" title="Copiar">' . icon('copy', 'icon icon-sm') . '</button>';
}

/** Back link at the top of a sub-page. */
function back_link(string $href, string $label): string
{
    return '<a class="back-link" href="' . e($href) . '">' . icon('back', 'icon icon-sm') . e($label) . '</a>';
}
