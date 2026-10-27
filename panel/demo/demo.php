<?php
declare(strict_types=1);
// Shared code for the demo provider (player_api.php and stream.php).

require_once __DIR__ . '/../includes/bootstrap.php';

function demo_config(): array
{
    return (array)config('demo', []) + ['enabled' => false, 'username' => '', 'password' => ''];
}

function demo_content(): array
{
    static $content = null;
    return $content ??= require __DIR__ . '/content.php';
}

function demo_valid_login(string $username, string $password): bool
{
    $c = demo_config();
    return $c['enabled'] && $c['username'] !== '' && hash_equals($c['username'], $username) && hash_equals((string)$c['password'], $password);
}

/** Absolute URL of the demo folder, e.g. https://panel.example.com/demo/ */
function demo_base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'])), '/');
    return ($https ? 'https' : 'http') . '://' . $_SERVER['HTTP_HOST'] . $dir . '/';
}

/** Leaves absolute URLs alone and resolves relative ones against the demo folder. */
function demo_url(string $pathOrUrl): string
{
    return preg_match('#^https?://#i', $pathOrUrl) ? $pathOrUrl : demo_base_url() . ltrim($pathOrUrl, '/');
}
