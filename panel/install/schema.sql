-- Panel schema (MySQL 5.7+ / MariaDB 10.3+). All datetimes are UTC.

CREATE TABLE IF NOT EXISTS admins (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      DATETIME     NOT NULL,
    last_login_at   DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admins_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_attempts (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    ip              VARCHAR(45)  NOT NULL,
    attempted_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_login_attempts_ip (ip, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Provider servers ("DNS"). Each device is assigned one.
CREATE TABLE IF NOT EXISTS dns_servers (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    url             VARCHAR(255) NOT NULL,
    notes           VARCHAR(255) NULL,
    sort_order      INT          NOT NULL DEFAULT 0,    -- manual login tries DNS in this order
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id       CHAR(12)     NOT NULL,              -- 12 hex chars, shown as AA:BB:CC:DD:EE:FF
    device_key_hash CHAR(64)     NULL,                  -- sha256 of the secret the app sent on first contact
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending', -- pending | active | disabled
    dns_id          INT UNSIGNED NULL,
    xtream_username VARCHAR(128) NULL,
    xtream_password VARCHAR(128) NULL,
    expires_at      DATETIME     NULL,                  -- NULL = never expires
    customer_name   VARCHAR(120) NULL,
    notes           VARCHAR(255) NULL,
    model           VARCHAR(120) NULL,
    app_version     VARCHAR(32)  NULL,
    last_ip         VARCHAR(45)  NULL,
    first_seen_at   DATETIME     NULL,
    last_seen_at    DATETIME     NULL,
    activated_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_devices_device_id (device_id),
    KEY ix_devices_status (status),
    KEY ix_devices_dns (dns_id),
    CONSTRAINT fk_devices_dns FOREIGN KEY (dns_id) REFERENCES dns_servers (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Messages shown in the app. device_id NULL = broadcast to every device.
CREATE TABLE IF NOT EXISTS messages (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id       INT UNSIGNED NULL,
    title           VARCHAR(120) NOT NULL,
    body            TEXT         NOT NULL,
    expires_at      DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_messages_device (device_id),
    CONSTRAINT fk_messages_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
