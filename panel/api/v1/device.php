<?php
declare(strict_types=1);
// App <-> panel endpoint. Documented in docs/API.md.

require_once __DIR__ . '/../../includes/devices.php';

$input = api_input();

try {
    $device = api_device($input);
    $deviceId = $device['device_id'];
    $now = now_utc();

    $status = device_status($device);
    $response = [
        'ok'           => true,
        'device_id'    => format_device_id($deviceId),
        'status'       => $status,
        'server_time'  => gmdate('c'),
        'poll_seconds' => (int)($status === 'active' ? config('poll_active_seconds', 900) : config('poll_pending_seconds', 10)),
        'account'      => null,
        'messages'     => [],
    ];

    if ($status === 'active') {
        $dns = q('SELECT name, url FROM dns_servers WHERE id = ?', [$device['dns_id']])->fetch();
        if ($dns) {
            $response['account'] = [
                'dns_name'   => $dns['name'],
                'server_url' => $dns['url'],
                'username'   => $device['xtream_username'],
                'password'   => $device['xtream_password'],
                'expires_at' => $device['expires_at'] ? gmdate('c', strtotime($device['expires_at'] . ' UTC')) : null,
            ];
        } else {
            $response['status'] = 'pending';
        }
    }

    // Broadcasts older than 30 days are not shown to devices that never saw them.
    $broadcastSince = gmdate('Y-m-d H:i:s', time() - 30 * 86400);
    $rows = q('SELECT id, title, body, created_at FROM messages
               WHERE (device_id = ? OR (device_id IS NULL AND created_at >= ?))
                 AND (expires_at IS NULL OR expires_at >= ?)
               ORDER BY created_at ASC LIMIT 20',
              [$device['id'], $broadcastSince, $now])->fetchAll();
    foreach ($rows as $m) {
        $response['messages'][] = [
            'id'         => (int)$m['id'],
            'title'      => $m['title'],
            'body'       => $m['body'],
            'created_at' => gmdate('c', strtotime($m['created_at'] . ' UTC')),
        ];
    }

    respond(200, $response);
} catch (Throwable $ex) {
    error_log('api/v1/device: ' . $ex->getMessage());
    fail(500, 'server_error', 'Server error. Try again later.');
}
