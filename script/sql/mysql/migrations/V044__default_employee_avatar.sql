-- V044: add the global default employee avatar configuration.
-- Dependencies/order: apply after V043; Infra owns the configuration and System exposes it in the
-- authenticated permission response.
-- Data scope: inserts only the missing `zsjos.user.default-avatar` configuration with an empty value.
-- It does not update users, backfill avatars, delete files, or change tenant/account permissions.
-- Repeatability: reruns preserve an existing administrator-configured value.
-- Rollback limitation: forward-only in normal operation. Removing the configuration restores the
-- nickname-initial fallback but should be coordinated with clients that expect this key.

INSERT INTO `infra_config` (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`)
SELECT 'ZSJOS品牌配置',1,'默认员工头像','zsjos.user.default-avatar','',b'1',
       '未配置个人头像时使用；空值回退昵称首字'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key`='zsjos.user.default-avatar' AND `deleted`=b'0'
);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V044','Add global default employee avatar configuration','default-employee-avatar-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V044','Add global default employee avatar configuration',
        SHA2('default-employee-avatar-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
