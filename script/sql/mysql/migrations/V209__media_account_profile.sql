-- UTF-8. V209: account profile, after V208. Additive feature upgrade, no business rows deleted.
-- Nullable profile fields allow director-created empty accounts. Existing rows/snapshots remain unchanged.
-- Publishes one configuration version per existing published tenant. Existing drafts are retained;
-- they must be recopied/reconciled before publication. Query grants follow existing account capabilities.
-- Repeatable: version marker protects configuration publication; DDL and empty dictionary types are idempotent.
-- Rollback: restore the previous published config only after reverting application; keep entry rows for audit.
SET NAMES utf8mb4;
SOURCE script/sql/mysql/permissions/media-account-profile-query.sql;
ALTER TABLE zsjos_media_account
 MODIFY owner_operator_user_id bigint NULL,
 MODIFY platform_value varchar(100) NULL,
 MODIFY platform_label_snapshot varchar(100) NULL,
 MODIFY nickname varchar(255) NULL;
CREATE TABLE IF NOT EXISTS zsjos_media_account_profile_entry (
 id bigint NOT NULL AUTO_INCREMENT,
 account_id bigint NOT NULL,
 operated_by_user_id bigint NOT NULL,
 operated_by_name varchar(100) DEFAULT NULL,
 kind varchar(20) NOT NULL,
 field_key varchar(64) DEFAULT NULL,
 title varchar(255) NOT NULL,
 content mediumtext,
 snapshot_json json DEFAULT NULL,
 files_json json DEFAULT NULL,
 idempotency_key varchar(128) COLLATE utf8mb4_bin NOT NULL,
 fingerprint char(64) NOT NULL,
 result_version int NOT NULL,
 creator varchar(64) DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id),
 UNIQUE KEY uk_account_command(tenant_id,account_id,operated_by_user_id,idempotency_key),
 KEY idx_account_history(tenant_id,account_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号档案维护与复盘追加记录';

INSERT INTO system_dict_type (name,type,status,remark,creator,updater,create_time,update_time,deleted)
SELECT '账号发布节奏','zsjos_account_publish_rhythm',0,'账号档案：管理员维护选项，初始化为空','1','1',NOW(),NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_account_publish_rhythm' AND deleted=b'0');
INSERT INTO system_dict_type (name,type,status,remark,creator,updater,create_time,update_time,deleted)
SELECT '账号内容主要形式','zsjos_account_content_format',0,'账号档案：管理员维护选项，初始化为空','1','1',NOW(),NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_account_content_format' AND deleted=b'0');

DROP PROCEDURE IF EXISTS zsjos_V209_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_V209_apply()
BEGIN
 DECLARE done INT DEFAULT 0;
 DECLARE cid BIGINT;
 DECLARE tid BIGINT;
 DECLARE next_version INT;
 DECLARE old_fields JSON;
 DECLARE new_fields JSON;
 DECLARE defaults_json JSON;
 DECLARE field_json JSON;
 DECLARE matching JSON;
 DECLARE k VARCHAR(64);
 DECLARE n INT;
 DECLARE idx INT;
 DECLARE config_cursor CURSOR FOR SELECT id,tenant_id,fields_json FROM zsjos_media_account_field_config WHERE status='published' AND deleted=b'0';
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V209') THEN
  SET defaults_json=CAST('[{"key":"cover","label":"主页截图","ownerType":"OPERATOR","group":"PROFILE","type":"image","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":10,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"account_no","label":"账号编号","ownerType":"AUTO","group":"PROFILE","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":20,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"nickname","label":"账号名称","ownerType":"OPERATOR","group":"PROFILE","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":30,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"platform","label":"账号平台","ownerType":"OPERATOR","group":"PROFILE","type":"select","dictType":"zsjos_account_platform","required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":40,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"uid","label":"主页 ID","ownerType":"OPERATOR","group":"PROFILE","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":50,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"homepage_url","label":"主页链接","ownerType":"OPERATOR","group":"PROFILE","type":"url","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":60,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"avatar","label":"头像设置","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":70,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"background","label":"背景设置","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":80,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"bio","label":"主页引导语","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":90,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"pinned_description","label":"置顶视频描述（why）","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":100,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"lead_capture","label":"客资提取方式","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":110,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"work_format","label":"主要作品形式","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":120,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"target_audience","label":"目标用户","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":130,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"product_goal","label":"承接产品目标","ownerType":"OPERATOR","group":"PROFILE","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":140,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"publish_rhythm","label":"当前发布节奏","ownerType":"OPERATOR","group":"PROFILE","type":"select","dictType":"zsjos_account_publish_rhythm","required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":150,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"student_name","label":"学员姓名","ownerType":"AUTO","group":"POSITIONING","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":160,"searchable":false,"sourceType":"STUDENT","snapshotPolicy":"ON_SELECTION"},{"key":"contact","label":"联系方式","ownerType":"AUTO","group":"POSITIONING","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":170,"searchable":false,"sourceType":"STUDENT","snapshotPolicy":"ON_SELECTION"},{"key":"joining_goal","label":"学员加入目标","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":180,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"learning_stage","label":"当前学习/资格阶段","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":190,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"main_track","label":"主赛道","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":200,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"secondary_track","label":"辅助赛道","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":210,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"cooperation","label":"学员配合等级","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":220,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"shooting_time","label":"连续可拍摄时间","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":230,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"camera_willingness","label":"出镜意愿","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":240,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"expression_level","label":"表达能力等级","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":250,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"professional_assets","label":"专业优势和案例资产","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":260,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"trust_evidence","label":"信任证据","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":270,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"execution_risk","label":"执行主要风险","ownerType":"DIRECTOR","group":"POSITIONING","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":280,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"accompany_days","label":"陪跑天数","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":290,"searchable":false,"sourceType":"PENDING","snapshotPolicy":"ON_SELECTION"},{"key":"position_rounds","label":"定位轮数","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":300,"searchable":false,"sourceType":"PENDING","snapshotPolicy":"ON_SELECTION"},{"key":"account_position","label":"账号定位","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":310,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"professional_position","label":"专业定位","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":320,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"stage","label":"当前期段","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":330,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"current_status","label":"账号状态","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":340,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"bottleneck","label":"当前瓶颈","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":350,"searchable":false,"sourceType":"PENDING","snapshotPolicy":"ON_SELECTION"},{"key":"content_format","label":"内容主要形式","ownerType":"AUTO","group":"STATUS","type":"text","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":360,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"total_leads","label":"累计客资数","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":370,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"total_conversion","label":"累计成交率","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":380,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"total_amount","label":"累计成交金额","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":390,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"month_leads","label":"本月客资数","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":400,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"month_conversion","label":"本月成交率","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":410,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"month_amount","label":"本月成交金额","ownerType":"AUTO","group":"METRICS","type":"number","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":420,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"},{"key":"positioning_snapshot","label":"完整定位卡信息","ownerType":"AUTO","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":430,"searchable":false,"sourceType":"PENDING","snapshotPolicy":"ON_SELECTION"},{"key":"diagnosis_7d","label":"7天账号数据诊断","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":440,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"diagnosis_14d","label":"14天验证指标诊断","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":450,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"adjustment_28d","label":"28天调整触发条件","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":460,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"positioning_history","label":"历史定位与采访记录","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":470,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s0","label":"S0期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":480,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s1","label":"S1期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":490,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s2","label":"S2期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":500,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s3","label":"S3期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":510,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s4","label":"S4期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":520,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s5","label":"S5期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":530,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_s6","label":"S6期交付确认","ownerType":"DIRECTOR","group":"REVIEW","type":"record","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":540,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"student_commitments","label":"学员承担事项","ownerType":"DIRECTOR","group":"REVIEW","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":550,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"company_commitments","label":"公司承担事项","ownerType":"DIRECTOR","group":"REVIEW","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":true,"enabled":true,"sort":560,"searchable":false,"sourceType":"MANUAL","snapshotPolicy":"ON_SELECTION"},{"key":"delivery_goals","label":"内部交付目标约定","ownerType":"AUTO","group":"REVIEW","type":"textarea","dictType":null,"required":false,"requiredForCreate":false,"requiredForComplete":false,"enabled":true,"sort":570,"searchable":false,"sourceType":"ACCOUNT","snapshotPolicy":"ON_SELECTION"}]' AS JSON);
  START TRANSACTION;
  OPEN config_cursor;
  config_loop: LOOP
   FETCH config_cursor INTO cid,tid,old_fields;
   IF done THEN LEAVE config_loop; END IF;
   SET new_fields=JSON_ARRAY();
   SET idx=0;
   WHILE idx<JSON_LENGTH(defaults_json) DO
    SET field_json=JSON_EXTRACT(defaults_json,CONCAT('$[',idx,']'));
    SET k=JSON_UNQUOTE(JSON_EXTRACT(field_json,'$.key'));
    SET matching=JSON_SEARCH(old_fields,'one',k,NULL,'$[*].key');
    IF matching IS NOT NULL THEN
     -- Preserve configured label/type/dictionary; the approved responsibility/group becomes authoritative.
     SET matching=JSON_EXTRACT(old_fields,REPLACE(JSON_UNQUOTE(matching),'.key',''));
     SET field_json=JSON_MERGE_PATCH(matching,JSON_OBJECT(
      'ownerType',JSON_UNQUOTE(JSON_EXTRACT(field_json,'$.ownerType')),
      'group',JSON_UNQUOTE(JSON_EXTRACT(field_json,'$.group')),
      'sourceType',JSON_UNQUOTE(JSON_EXTRACT(field_json,'$.sourceType')),
      'requiredForCreate',CAST('false' AS JSON),'required',CAST('false' AS JSON),
      'requiredForComplete',JSON_EXTRACT(field_json,'$.requiredForComplete'),
      'snapshotPolicy','ON_SELECTION','sort',(idx+1)*10));
    END IF;
    SET new_fields=JSON_ARRAY_APPEND(new_fields,'$',field_json);
    SET idx=idx+1;
   END WHILE;
   SET idx=0;
   WHILE idx<JSON_LENGTH(old_fields) DO
    SET field_json=JSON_EXTRACT(old_fields,CONCAT('$[',idx,']'));
    SET k=JSON_UNQUOTE(JSON_EXTRACT(field_json,'$.key'));
    IF JSON_SEARCH(defaults_json,'one',k,NULL,'$[*].key') IS NULL THEN
     SET new_fields=JSON_ARRAY_APPEND(new_fields,'$',JSON_MERGE_PATCH(field_json,JSON_OBJECT(
      'ownerType','UNASSIGNED','group','PROFILE','sourceType','MANUAL','required',CAST('false' AS JSON),
      'requiredForCreate',CAST('false' AS JSON),'requiredForComplete',CAST('false' AS JSON),
      'snapshotPolicy','ON_SELECTION','sort',1000+idx)));
    END IF;
    SET idx=idx+1;
   END WHILE;
   SELECT COALESCE(MAX(version_no),0)+1 INTO next_version FROM zsjos_media_account_field_config WHERE tenant_id=tid;
   UPDATE zsjos_media_account_field_config SET status='archived' WHERE id=cid;
   INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,published_at,version,tenant_id,creator,updater)
    VALUES(next_version,'published',new_fields,NOW(),0,tid,'1','1');
  END LOOP;
  CLOSE config_cursor;
  INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
   VALUES('V209','media account profile',SHA2('V209__media_account_profile.sql',256),NOW());
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
   VALUES('core','V209','media account profile',SHA2('V209__media_account_profile.sql',256),'baseline',NOW())
   ON DUPLICATE KEY UPDATE description=VALUES(description);
  COMMIT;
 END IF;
END$$
DELIMITER ;
CALL zsjos_V209_apply();
DROP PROCEDURE IF EXISTS zsjos_V209_apply;

-- Unreleased development correction; existing V209 installations run this file directly.
SOURCE script/sql/mysql/media-account-cover-operator.sql;

SOURCE script/sql/mysql/media-account-appearance-text.sql;

SOURCE script/sql/mysql/media-account-partner-metrics.sql;

SOURCE script/sql/mysql/media-account-content-format-auto.sql;
