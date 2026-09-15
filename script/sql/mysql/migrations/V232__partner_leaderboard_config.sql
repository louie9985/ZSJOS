SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `zsjos_partner_leaderboard_config` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0,
  `enabled` bit(1) NOT NULL DEFAULT b'1', `include_employee_submitter` bit(1) NOT NULL DEFAULT b'0',
  `employee_role_codes` text NULL, `enabled_types` varchar(255) NOT NULL DEFAULT 'estimated_income,withdrawn_amount,lead_count,valid_lead_count',
  `default_type` varchar(64) NOT NULL DEFAULT 'estimated_income', `default_period` varchar(16) NOT NULL DEFAULT 'month',
  `page_size` int NOT NULL DEFAULT 20, `mask_name` bit(1) NOT NULL DEFAULT b'1',
  `creator` varchar(64) NULL, `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL, `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS H5排行榜租户配置';

-- 管理后台配置菜单及按钮权限；父菜单由 ZSJOS 配置菜单维护时再绑定
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
SELECT 'H5排行榜配置','zsjos:partner:leaderboard-config:query',2,90,0,'zsjos/leaderboard-config','ep:trend-charts','zsjos/leaderboardConfig/index','ZsjosLeaderboardConfig',0,b'1',b'1',b'1','1','1',b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:partner:leaderboard-config:query' AND `deleted`=b'0');
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
SELECT '保存','zsjos:partner:leaderboard-config:update',3,1,(SELECT `id` FROM `system_menu` WHERE `permission`='zsjos:partner:leaderboard-config:query' AND `deleted`=b'0' LIMIT 1),NULL,0,b'1',b'1',b'1','1','1',b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:partner:leaderboard-config:update' AND `deleted`=b'0');

