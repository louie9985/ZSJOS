-- V097: server-owned menus and button permissions for the new-media workflow.
-- Data scope: additive menu metadata only; no role grants or business rows.
-- Repeatability: stable IDs with INSERT IGNORE. Apply after V096.
-- This file must not be executed without separate environment approval.
SET NAMES utf8mb4;
INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6970,'新媒体账号','zsjos:media-account:query',2,40,6735,'accounts','ep:platform','zsjos-workbench','MediaAccountsPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6971,'创建账号','zsjos:media-account:create',3,1,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6972,'绑定学员','zsjos:media-account:bind-student',3,2,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6973,'推进账号阶段','zsjos:media-account:stage-advance',3,3,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6974,'内容生产','zsjos:content:query',2,41,6735,'content','ep:document','zsjos-workbench','MediaContentPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6975,'创建内容','zsjos:content:create',3,1,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6976,'完成选题','zsjos:content:complete-topic',3,2,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6977,'拍剪工单','zsjos:production-ticket:query',2,42,6735,'production-tickets','ep:film','zsjos-workbench','MediaProductionTicketsPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6978,'创建拍剪工单','zsjos:production-ticket:create',3,1,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6979,'接单拍剪工单','zsjos:production-ticket:accept',3,2,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6980,'账号定位','zsjos:positioning-card:query',2,43,6735,'positioning','ep:compass','zsjos-workbench','MediaPositioningPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6981,'创建定位卡','zsjos:positioning-card:create',3,1,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6982,'提交定位审核','zsjos:positioning-card:submit-review',3,2,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6984,'学员运营','zsjos:student-ops:query',2,45,6735,'student-ops','ep:user','zsjos-workbench','MediaStudentOpsPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6985,'复盘与诊断','zsjos:review:query',2,46,6735,'reviews','ep:data-analysis','zsjos-workbench','MediaReviewsPage',0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0');
INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6987,'提交制作','zsjos:content:submit-production',3,3,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6988,'提交验收','zsjos:content:submit-acceptance',3,4,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6989,'验收处理','zsjos:content:acceptance-review',3,5,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6990,'开始修改','zsjos:content:revise',3,6,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6991,'重新提交制作','zsjos:content:resubmit-production',3,7,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6992,'开始制作','zsjos:production-ticket:produce',3,3,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6993,'提交成品','zsjos:production-ticket:submit',3,4,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6994,'开始核对','zsjos:production-ticket:check',3,5,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6995,'运营复核','zsjos:positioning-card:feasibility-review',3,3,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6996,'确认试跑','zsjos:positioning-card:confirm-trial',3,4,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6997,'归档定位','zsjos:positioning-card:archive',3,5,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6998,'账号查询全部','zsjos:media-account:query-all',3,10,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(6999,'内容查询全部','zsjos:content:query-all',3,10,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7000,'工单查询全部','zsjos:production-ticket:query-all',3,10,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7001,'定位查询全部','zsjos:positioning-card:query-all',3,10,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0');
INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(7002,'回退账号阶段','zsjos:media-account:stage-rollback',3,4,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0');
INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(7003,'编辑账号','zsjos:media-account:edit',3,5,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7004,'账号诊断','zsjos:media-account:diagnose',3,6,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7005,'账号挽救','zsjos:media-account:rescue',3,7,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7006,'编辑内容版本','zsjos:content:edit',3,8,6974,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7007,'编辑工单内容项','zsjos:production-ticket:edit',3,6,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7008,'编辑定位工作台','zsjos:positioning-card:edit',3,6,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7009,'定位执行卡签字','zsjos:positioning-card:sign',3,7,6980,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7013,'创建异常工单','zsjos:student-ops:create-exception',3,1,6984,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7014,'处理异常工单','zsjos:student-ops:resolve-exception',3,2,6984,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7015,'配合度评估','zsjos:student-ops:assess',3,3,6984,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7016,'创建复盘','zsjos:review:create',3,1,6985,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7017,'提交复盘','zsjos:review:submit',3,2,6985,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7018,'归档复盘','zsjos:review:archive',3,3,6985,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0');
INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(7019,'账号换绑申请','zsjos:media-account:rebind',3,8,6970,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7020,'超权益处理','zsjos:production-ticket:over-entitlement',3,7,6977,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0'),
(7021,'学员结业','zsjos:student-ops:graduate',3,4,6984,'','','',NULL,0,b'1',b'1',b'1','migration-V097',NOW(),'migration-V097',NOW(),b'0');
INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V097','New-media workflow menu permissions','new-media-menu-v3') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V097','New-media workflow menu permissions',SHA2('new-media-menu-v3',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
