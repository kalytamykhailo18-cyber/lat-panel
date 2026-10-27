<?php
declare(strict_types=1);
// Shared helpers for the JSON endpoints in api/v1/.

require_once __DIR__ . '/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(int $code, array $body): void
{
    http_response_code($code);
    echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function fail(int $code, string $error, string $message): void
{
    respond($code, ['ok' => false, 'error' => $error, 'message' => $message]);
}

/** Reads a POST body sent as JSON or as a regular form. */
function api_input(): array
{
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        fail(405, 'method_not_allowed', 'Use POST.');
    }
    if (stripos($_SERVER['CONTENT_TYPE'] ?? '', 'application/json') === false) {
        return $_POST;
    }
    $input = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($input)) {
        fail(400, 'bad_request', 'Body is not valid JSON.');
    }
    return $input;
}
