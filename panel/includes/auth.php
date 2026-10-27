<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

const LOGIN_MAX_ATTEMPTS = 8;
const LOGIN_WINDOW_MINUTES = 15;

function start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    session_name('panel_sid');
    session_set_cookie_params([
        'lifetime' => 0,
        'path'     => '/',
        'secure'   => !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
    session_start();
}

function current_admin(): ?array
{
    start_session();
    if (empty($_SESSION['admin_id'])) {
        return null;
    }
    static $admin = null;
    if ($admin === null) {
        $admin = q('SELECT id, username FROM admins WHERE id = ?', [$_SESSION['admin_id']])->fetch() ?: null;
    }
    return $admin;
}

function require_login(): array
{
    $admin = current_admin();
    if (!$admin) {
        // Background requests (auto-refresh) must not receive the login page as content.
        if (($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '') === 'fetch' || ($_GET['format'] ?? '') === 'json') {
            http_response_code(401);
            exit;
        }
        header('Location: login.php');
        exit;
    }
    return $admin;
}

function login_blocked(): bool
{
    $since = gmdate('Y-m-d H:i:s', time() - LOGIN_WINDOW_MINUTES * 60);
    $count = (int)q('SELECT COUNT(*) FROM login_attempts WHERE ip = ? AND attempted_at > ?', [client_ip(), $since])->fetchColumn();
    return $count >= LOGIN_MAX_ATTEMPTS;
}

function attempt_login(string $username, string $password): bool
{
    $admin = q('SELECT id, password_hash FROM admins WHERE username = ?', [$username])->fetch();
    if (!$admin || !password_verify($password, $admin['password_hash'])) {
        q('INSERT INTO login_attempts (ip, attempted_at) VALUES (?, ?)', [client_ip(), now_utc()]);
        return false;
    }
    q('DELETE FROM login_attempts WHERE ip = ?', [client_ip()]);
    q('UPDATE admins SET last_login_at = ? WHERE id = ?', [now_utc(), $admin['id']]);

    start_session();
    session_regenerate_id(true);
    $_SESSION['admin_id'] = (int)$admin['id'];
    return true;
}

function logout(): void
{
    start_session();
    $_SESSION = [];
    session_destroy();
}

function csrf_token(): string
{
    start_session();
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="csrf" value="' . e(csrf_token()) . '">';
}

/** Call at the top of every POST handler. */
function verify_csrf(): void
{
    start_session();
    $sent = $_POST['csrf'] ?? '';
    if (!is_string($sent) || empty($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'], $sent)) {
        http_response_code(400);
        exit('El formulario expiró. Vuelve atrás, recarga la página e intenta de nuevo.');
    }
}

function flash(string $type, string $message): void
{
    start_session();
    $_SESSION['flash'][] = [$type, $message];
}

function take_flashes(): array
{
    start_session();
    $f = $_SESSION['flash'] ?? [];
    unset($_SESSION['flash']);
    return $f;
}

function redirect(string $to): void
{
    header('Location: ' . $to);
    exit;
}
