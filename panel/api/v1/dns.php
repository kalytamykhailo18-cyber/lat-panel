<?php
declare(strict_types=1);
// Ordered DNS list for the app's manual login. The app checks the customer's username and
// password against these servers one at a time, from the customer's own connection, so the
// providers never see a burst of logins from a single IP. Documented in docs/API.md.

require_once __DIR__ . '/../../includes/devices.php';

$input = api_input();

try {
    api_device($input);
    $servers = q('SELECT url FROM dns_servers ORDER BY sort_order, id')->fetchAll();
    respond(200, [
        'ok'         => true,
        'servers'    => array_column($servers, 'url'),
        'timeout_ms' => max(300, min(10000, (int)config('manual_login_timeout_ms', 1000))),
    ]);
} catch (Throwable $ex) {
    error_log('api/v1/dns: ' . $ex->getMessage());
    fail(500, 'server_error', 'Server error. Try again later.');
}
