<?php
declare(strict_types=1);
// Xtream Codes compatible API serving the royalty-free demo catalog (content.php).
// Register "{panel}/demo" as a DNS in the panel to make the demo login work in the app.

require_once __DIR__ . '/demo.php';

header('Content-Type: application/json; charset=utf-8');

function out($data): void
{
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

if (!demo_valid_login((string)($_GET['username'] ?? ''), (string)($_GET['password'] ?? ''))) {
    out(['user_info' => ['auth' => 0]]);
}

$c = demo_content();
$categories = fn(array $list) => array_map(fn($cat) => ['category_id' => $cat['id'], 'category_name' => $cat['name'], 'parent_id' => 0], $list);
$inCategory = fn(array $item) => !isset($_GET['category_id']) || (string)$_GET['category_id'] === (string)$item['category'];

switch ($_GET['action'] ?? '') {
    case '':
        out([
            'user_info' => [
                'username' => $_GET['username'], 'auth' => 1, 'status' => 'Active', 'exp_date' => null,
                'max_connections' => '1', 'active_cons' => '0', 'allowed_output_formats' => ['m3u8'],   // demo sources are HLS
            ],
            'server_info' => ['url' => $_SERVER['HTTP_HOST'], 'timezone' => 'UTC', 'timestamp_now' => time()],
        ]);

    case 'get_live_categories':
        out($categories($c['live_categories']));

    case 'get_live_streams':
        // Channel numbers follow the full list order, so they stay the same in every category.
        $channels = [];
        foreach (array_values($c['live']) as $i => $ch) {
            if ($inCategory($ch)) {
                $channels[] = [
                    'num' => $i + 1, 'name' => $ch['name'], 'stream_type' => 'live', 'stream_id' => $ch['id'],
                    'stream_icon' => demo_url($ch['logo']), 'category_id' => $ch['category'], 'epg_channel_id' => null,
                ];
            }
        }
        out($channels);

    case 'get_short_epg':
        // Rotating 30-minute schedule built from the open movies, so the guide is never empty.
        $titles = array_column($c['vod'], 'name');
        $slot = 1800;
        $start = intdiv(time(), $slot) * $slot;
        $offset = (int)($_GET['stream_id'] ?? 0);
        $listings = [];
        for ($i = 0; $i < 2; $i++) {
            $title = $titles[($offset + intdiv($start, $slot) + $i) % count($titles)];
            $listings[] = [
                'title' => base64_encode($title), 'description' => base64_encode('Contenido de demostración libre de derechos. (CC) Blender Foundation.'),
                'start_timestamp' => (string)($start + $i * $slot), 'stop_timestamp' => (string)($start + ($i + 1) * $slot),
            ];
        }
        out(['epg_listings' => $listings]);

    case 'get_vod_categories':
        out($categories($c['vod_categories']));

    case 'get_vod_streams':
        out(array_values(array_map(fn($m) => [
            'stream_id' => $m['id'], 'name' => $m['name'], 'stream_icon' => demo_url($m['poster']), 'rating' => $m['rating'],
            'category_id' => $m['category'], 'container_extension' => $m['ext'],
        ], array_filter($c['vod'], $inCategory))));

    case 'get_vod_info':
        foreach ($c['vod'] as $m) {
            if ((string)$m['id'] === (string)($_GET['vod_id'] ?? '')) {
                out([
                    'info' => ['name' => $m['name'], 'plot' => $m['plot'], 'releasedate' => $m['year'] . '-01-01', 'duration' => $m['duration'],
                        'rating' => $m['rating'], 'genre' => 'Animación', 'movie_image' => demo_url($m['poster']), 'backdrop_path' => [demo_url($m['backdrop'])]],
                    'movie_data' => ['stream_id' => $m['id'], 'name' => $m['name'], 'category_id' => $m['category'], 'container_extension' => $m['ext']],
                ]);
            }
        }
        out([]);

    case 'get_series_categories':
        out($categories($c['series_categories']));

    case 'get_series':
        out(array_values(array_map(fn($s) => [
            'series_id' => $s['id'], 'name' => $s['name'], 'cover' => demo_url($s['cover']), 'plot' => $s['plot'],
            'rating' => $s['rating'], 'category_id' => $s['category'], 'releaseDate' => $s['year'] . '-01-01',
        ], array_filter($c['series'], $inCategory))));

    case 'get_series_info':
        $vodById = array_column($c['vod'], null, 'id');
        foreach ($c['series'] as $s) {
            if ((string)$s['id'] !== (string)($_GET['series_id'] ?? '')) {
                continue;
            }
            $episodes = [];
            foreach ($s['episodes'] as $season => $vodIds) {
                foreach (array_values($vodIds) as $i => $vodId) {
                    $m = $vodById[$vodId];
                    $episodes[(string)$season][] = [
                        'id' => (string)$m['id'], 'episode_num' => $i + 1, 'title' => $m['name'], 'container_extension' => $m['ext'],
                        'info' => ['plot' => $m['plot'], 'duration' => $m['duration'], 'movie_image' => demo_url($m['poster'])],
                    ];
                }
            }
            out([
                'seasons' => [],
                'info' => ['name' => $s['name'], 'cover' => demo_url($s['cover']), 'plot' => $s['plot'], 'genre' => 'Animación',
                    'releaseDate' => $s['year'] . '-01-01', 'rating' => $s['rating'], 'backdrop_path' => [demo_url($s['backdrop'])]],
                'episodes' => $episodes,
            ]);
        }
        out([]);

    default:
        out([]);
}
