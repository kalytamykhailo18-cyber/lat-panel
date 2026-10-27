<?php
declare(strict_types=1);

const APP_ROOT = __DIR__ . '/..';

$configFile = APP_ROOT . '/config.php';
if (!is_file($configFile)) {
    http_response_code(500);
    exit('Falta config.php. Copia config.sample.php como config.php y complétalo.');
}
$CONFIG = require $configFile;

error_reporting(E_ALL);
ini_set('display_errors', !empty($CONFIG['debug']) ? '1' : '0');
date_default_timezone_set('UTC');

function config(string $key, $default = null)
{
    global $CONFIG;
    return $CONFIG[$key] ?? $default;
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $c = config('db');
        $pdo = new PDO($c['dsn'], $c['user'] ?? null, $c['password'] ?? null, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/** Runs a prepared statement and returns it. */
function q(string $sql, array $params = []): PDOStatement
{
    $st = db()->prepare($sql);
    $st->execute($params);
    return $st;
}

/** Current UTC time in the format stored in the database. */
function now_utc(): string
{
    return gmdate('Y-m-d H:i:s');
}

/** Formats a stored UTC datetime in the panel timezone. */
function local_time(?string $utc, string $format = 'Y-m-d H:i'): string
{
    if (!$utc) {
        return '—';
    }
    $dt = new DateTime($utc, new DateTimeZone('UTC'));
    $dt->setTimezone(new DateTimeZone(config('timezone', 'UTC')));
    return $dt->format($format);
}

/** Converts a local date (Y-m-d) from a form into the stored UTC end-of-day datetime. */
function local_date_to_utc_end_of_day(string $date): ?string
{
    $date = trim($date);
    if ($date === '') {
        return null;
    }
    $dt = DateTime::createFromFormat('!Y-m-d H:i:s', $date . ' 23:59:59', new DateTimeZone(config('timezone', 'UTC')));
    if (!$dt) {
        return null;
    }
    $dt->setTimezone(new DateTimeZone('UTC'));
    return $dt->format('Y-m-d H:i:s');
}

function e(?string $s): string
{
    return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
}

/** Device IDs are stored as 12 uppercase hex chars and shown as AA:BB:CC:DD:EE:FF. */
function normalize_device_id(string $raw): ?string
{
    $id = strtoupper(preg_replace('/[^0-9A-Fa-f]/', '', $raw));
    return strlen($id) === 12 ? $id : null;
}

function format_device_id(string $id): string
{
    return implode(':', str_split($id, 2));
}

/** Effective status: an active device past its expiry date is reported as expired. */
function device_status(array $device): string
{
    if ($device['status'] === 'active' && !empty($device['expires_at']) && $device['expires_at'] < now_utc()) {
        return 'expired';
    }
    return $device['status'];
}

function normalize_server_url(string $url): ?string
{
    $url = trim($url);
    if ($url !== '' && !preg_match('#^https?://#i', $url)) {
        $url = 'http://' . $url;
    }
    $url = rtrim($url, '/');
    return filter_var($url, FILTER_VALIDATE_URL) ? $url : null;
}

function client_ip(): string
{
    return $_SERVER['REMOTE_ADDR'] ?? '';
}
