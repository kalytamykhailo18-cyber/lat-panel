<?php
declare(strict_types=1);
// Device identification shared by the app endpoints in api/v1/.

require_once __DIR__ . '/api.php';

/**
 * Validates device_id + device_key from the request, registers unknown devices as pending,
 * refreshes last-seen data and returns the device row. Ends the request with an error when
 * the ID is malformed or bound to another device's key.
 */
function api_device(array $input): array
{
    $deviceId = normalize_device_id((string)($input['device_id'] ?? ''));
    $deviceKey = (string)($input['device_key'] ?? '');
    if (!$deviceId) {
        fail(400, 'invalid_device_id', 'device_id must be 12 hex characters.');
    }
    if (strlen($deviceKey) < 16 || strlen($deviceKey) > 128) {
        fail(400, 'invalid_device_key', 'device_key must be 16 to 128 characters.');
    }
    $keyHash = hash('sha256', $deviceKey);
    $model = mb_substr(trim((string)($input['model'] ?? '')), 0, 120) ?: null;
    $appVersion = mb_substr(trim((string)($input['app_version'] ?? '')), 0, 32) ?: null;
    $now = now_utc();

    $device = q('SELECT * FROM devices WHERE device_id = ?', [$deviceId])->fetch();
    if (!$device) {
        q('INSERT INTO devices (device_id, device_key_hash, status, model, app_version, last_ip, first_seen_at, last_seen_at, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
          [$deviceId, $keyHash, 'pending', $model, $appVersion, client_ip(), $now, $now, $now]);
        return q('SELECT * FROM devices WHERE device_id = ?', [$deviceId])->fetch();
    }

    if ($device['device_key_hash'] !== null && !hash_equals($device['device_key_hash'], $keyHash)) {
        fail(403, 'key_mismatch', 'This device ID is bound to another device. Contact your provider.');
    }
    q('UPDATE devices SET device_key_hash = ?, model = COALESCE(?, model), app_version = COALESCE(?, app_version),
          last_ip = ?, last_seen_at = ?, first_seen_at = COALESCE(first_seen_at, ?) WHERE id = ?',
      [$keyHash, $model, $appVersion, client_ip(), $now, $now, $device['id']]);
    return $device;
}
