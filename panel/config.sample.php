<?php
// Copy this file to config.php and fill in your values.
// config.php is never served to browsers (see .htaccess).

return [
    // MySQL connection (Hostinger: hPanel > Databases > MySQL Databases)
    'db' => [
        'dsn'      => 'mysql:host=localhost;dbname=panel;charset=utf8mb4',
        'user'     => 'panel_user',
        'password' => 'change-me',
    ],

    // Shown in the panel header, page titles and the privacy policy.
    'panel_name' => 'Premium Plus',

    // Contact address shown in the privacy policy (privacy.php). Optional.
    'contact_email' => '',

    // Timezone used to display dates in the panel. Dates are stored in UTC.
    'timezone' => 'America/Lima',

    // How often (seconds) the app asks for its status.
    'poll_pending_seconds' => 10,   // while showing the device ID screen
    'poll_active_seconds'  => 900,  // while the user is watching (picks up messages / deactivation)

    // Manual login: maximum time (milliseconds) the app waits for each DNS while searching
    // which one accepts the customer's account. The DNS are tried one at a time.
    'manual_login_timeout_ms' => 1000,

    // Royalty-free demo provider at {panel}/demo (Xtream compatible). Used for the Google Play
    // review: register "https://your-panel/demo" as a DNS and give Google these credentials.
    'demo' => [
        'enabled'  => true,
        'username' => 'demo',
        'password' => 'change-me',
    ],

    // Set to true only while debugging; never in production.
    'debug' => false,
];
