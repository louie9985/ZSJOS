-- ZSJOS default OAuth2 client for account-password login.
-- The login flow uses CLIENT_ID_DEFAULT ("default") to look up the OAuth2 client
-- (AdminAuthServiceImpl), but the V030 login-security baseline only seeded
-- zsjos-pc / zsjos-mobile. Add the missing default client so account/password
-- login does not fail with "OAuth2 客户端不存在".
-- Repeatable: guarded insert; no tokens or config rows are touched.

INSERT INTO system_oauth2_client
    (client_id, secret, name, logo, description, status,
     access_token_validity_seconds, refresh_token_validity_seconds,
     redirect_uris, authorized_grant_types, scopes, auto_approve_scopes,
     authorities, resource_ids, additional_information)
SELECT 'default', '$2a$10$K8KpY7uGvCx7m8VJQ5hWQe3tQg5yDYr2yAYlF6FQq5FzHj7qXqz6K', 'ZSJOS 默认端', '', '中世健默认登录端', 0,
       7200, 604800, '[]', '["password","refresh_token"]', '[]', '[]', '[]', '[]', '{}'
WHERE NOT EXISTS (SELECT 1 FROM system_oauth2_client WHERE client_id = 'default' AND deleted = b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V177','Add default OAuth2 client for account login','V177__add_default_oauth2_client.sql')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V177','Add default OAuth2 client for account login',SHA2('V177__add_default_oauth2_client.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
