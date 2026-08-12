-- ZSJOS 登录安全基线
-- 依赖：00-bootstrap-schema.sql 中的 system_oauth2_client、infra_config。
-- 可重复执行：仅在客户端或配置键不存在时插入，不删除既有 Token 或配置。
-- 默认值：PC/手机各 1 个会话，刷新令牌 7 天有效。

INSERT INTO system_oauth2_client
    (client_id, secret, name, logo, description, status,
     access_token_validity_seconds, refresh_token_validity_seconds,
     redirect_uris, authorized_grant_types, scopes, auto_approve_scopes,
     authorities, resource_ids, additional_information)
SELECT 'zsjos-pc', '$2a$10$K8KpY7uGvCx7m8VJQ5hWQe3tQg5yDYr2yAYlF6FQq5FzHj7qXqz6K', 'ZSJOS 电脑端', '', '中世健电脑端登录', 0,
       7200, 604800, '[]', '["password","refresh_token"]', '[]', '[]', '[]', '[]', '{}'
WHERE NOT EXISTS (SELECT 1 FROM system_oauth2_client WHERE client_id = 'zsjos-pc' AND deleted = b'0');

INSERT INTO system_oauth2_client
    (client_id, secret, name, logo, description, status,
     access_token_validity_seconds, refresh_token_validity_seconds,
     redirect_uris, authorized_grant_types, scopes, auto_approve_scopes,
     authorities, resource_ids, additional_information)
SELECT 'zsjos-mobile', '$2a$10$K8KpY7uGvCx7m8VJQ5hWQe3tQg5yDYr2yAYlF6FQq5FzHj7qXqz6K', 'ZSJOS 手机端', '', '中世健手机端登录', 0,
       7200, 604800, '[]', '["password","refresh_token"]', '[]', '[]', '[]', '[]', '{}'
WHERE NOT EXISTS (SELECT 1 FROM system_oauth2_client WHERE client_id = 'zsjos-mobile' AND deleted = b'0');

INSERT INTO infra_config (category, type, name, config_key, value, visible, remark)
SELECT 'ZSJOS登录安全', 1, '电脑端最大登录设备数', 'zsjos.auth.pc.max-devices', '1', b'1', '正整数，最大 20'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'zsjos.auth.pc.max-devices' AND deleted = b'0');

INSERT INTO infra_config (category, type, name, config_key, value, visible, remark)
SELECT 'ZSJOS登录安全', 1, '手机端最大登录设备数', 'zsjos.auth.mobile.max-devices', '1', b'1', '正整数，最大 20'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'zsjos.auth.mobile.max-devices' AND deleted = b'0');

INSERT INTO infra_config (category, type, name, config_key, value, visible, remark)
SELECT 'ZSJOS登录安全', 1, '免密登录天数', 'zsjos.auth.remember-days', '7', b'1', '正整数，最大 365 天'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'zsjos.auth.remember-days' AND deleted = b'0');

INSERT INTO zsjos_schema_version (`version`, `description`, `checksum`)
VALUES ('V030', 'Add ZSJOS PC/mobile login security clients and defaults', 'zsjos-login-security-v1')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
