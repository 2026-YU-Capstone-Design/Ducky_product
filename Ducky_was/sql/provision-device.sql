\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE provision_input (
    serial varchar(120) NOT NULL CHECK (length(trim(serial)) > 0 AND trim(serial) = serial),
    owner_id bigint NOT NULL,
    token_hash text NOT NULL CHECK (token_hash ~ '^[0-9a-f]{64}$')
) ON COMMIT DROP;
INSERT INTO provision_input VALUES (:'device_serial', :'owner_id', :'token_hash');

INSERT INTO devices (serial_number, status, registered_at, updated_at)
SELECT serial, 'REGISTERED', now(), now() FROM provision_input
ON CONFLICT (serial_number) DO UPDATE SET updated_at = now();

UPDATE device_users AS link SET token_hash = NULL
FROM devices AS d, provision_input AS p
WHERE link.device_id = d.device_id AND d.serial_number = p.serial AND link.user_id <> p.owner_id;

INSERT INTO device_users (device_id, user_id, role, linked_at, token_hash)
SELECT d.device_id, p.owner_id, 'OWNER', now(), p.token_hash
FROM devices AS d JOIN provision_input AS p ON p.serial = d.serial_number
ON CONFLICT (device_id, user_id) DO UPDATE SET role = 'OWNER', token_hash = EXCLUDED.token_hash;

COMMIT;
