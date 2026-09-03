-- V180: repair the tenant WeCom channel display snapshot written with a wrong connection charset.
-- Data scope: the single tenant-1, channel=wecom configuration row. No credentials are changed.
-- Repeatability: deterministic update; safe to run after V179.
SET NAMES utf8mb4;
UPDATE system_notify_channel_config
SET masked_config='企业微信自建应用配置',
    updater='migration-V180', update_time=NOW()
WHERE tenant_id=1 AND channel_code='wecom' AND deleted=b'0';

INSERT IGNORE INTO zsjos_schema_version (version, description, checksum)
VALUES ('V180','Repair WeCom channel display encoding','wecom-channel-encoding-v1');
