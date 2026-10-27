<?php
declare(strict_types=1);
// Resolves Xtream stream URLs ({demo}/live|movie|series/{user}/{pass}/{id}.{ext}, rewritten here
// by .htaccess) to the royalty-free source in content.php.

require_once __DIR__ . '/demo.php';

$type = (string)($_GET['type'] ?? '');
$id = (string)($_GET['id'] ?? '');

if (!demo_valid_login((string)($_GET['u'] ?? ''), (string)($_GET['p'] ?? ''))) {
    http_response_code(403);
    exit;
}

$c = demo_content();
$list = $type === 'live' ? $c['live'] : $c['vod'];   // series episodes are vod ids
foreach ($list as $item) {
    if ((string)$item['id'] === $id) {
        header('Location: ' . demo_url($item['url']), true, 302);
        exit;
    }
}
http_response_code(404);
