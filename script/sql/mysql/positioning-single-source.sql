SET NAMES utf8mb4;
-- Development correction for the approved single positioning-card source (2026-09-17).
-- Prerequisites: V209 tables and director templates; invoke from repository root.
-- Scope: default_positioning templates and published account configs only, per tenant.
-- Preserve business rows, immutable old versions and administrator drafts. No role grants.
-- Repeatable by replacement field signature; administrator-customized new templates are preserved.
-- Rollback: republish archived config/template through administration; never delete history.
-- Applied deployment checksums are not reconciled by this script. Review deployed upgrades separately.
DROP PROCEDURE IF EXISTS zsjos_positioning_single_source;
DELIMITER $$
CREATE PROCEDURE zsjos_positioning_single_source()
BEGIN
 DECLARE finished INT DEFAULT 0;
 DECLARE template_id_value BIGINT;
 DECLARE tenant_value BIGINT;
 DECLARE published_id BIGINT;
 DECLARE next_version INT;
 DECLARE fields_value JSON;
 DECLARE replacement JSON;
 DECLARE config_id BIGINT;
 DECLARE item JSON;
 DECLARE merged JSON;
 DECLARE i INT;
 DECLARE changed BOOLEAN;
 DECLARE templates CURSOR FOR SELECT id,tenant_id,published_version_id FROM zsjos_director_form_template
  WHERE scene='positioning_card' AND template_code='default_positioning' AND deleted=b'0';
 DECLARE configs CURSOR FOR SELECT id,tenant_id,fields_json FROM zsjos_media_account_field_config
  WHERE status='published' AND deleted=b'0';
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SET replacement=CAST('[{"key":"pc_account_name","description":"注意描述：最好帮助运营直接定下来","type":"textarea","sort":10,"title":"账号名称建议","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_account_position","description":"第1类-第12类","type":"multi_select","sort":20,"dictType":"zsjos_persona_type","title":"账号定位","enabled":true,"required":false,"systemField":false,"group":"账号定位卡","multiple":true},{"key":"pc_profession","description":"T1-T9","type":"multi_select","sort":30,"dictType":"zsjos_material_profession","title":"专业定位","enabled":true,"required":false,"systemField":false,"group":"账号定位卡","multiple":true},{"key":"pc_target_user","description":"","type":"textarea","sort":40,"title":"目标用户","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_content_form","description":"选择；增加选择项目需要总监审核。","type":"multi_select","sort":50,"dictType":"zsjos_director_content_form","title":"主要内容形式","enabled":true,"required":false,"systemField":false,"group":"账号定位卡","multiple":true},{"key":"pc_lead_capture","description":"","type":"textarea","sort":60,"title":"客资提取方式","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_product_goal","description":"","type":"textarea","sort":70,"title":"承接产品目标","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_join_goal","description":"兼职变现、新媒体能力成长、职业转型、IP打造、业务增量等","type":"textarea","sort":80,"title":"学员加入目标","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_learning_stage","description":"未报名/在学/备考/持证/从业/成熟服务者","type":"textarea","sort":90,"title":"当前学习/资格阶段","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_primary_track","description":"营养/心理/中医适宜技术/药师/健康管理/慢病/其他","type":"textarea","sort":100,"title":"主赛道","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_secondary_track","description":"最多1项；说明为什么对主用户有帮助","type":"textarea","sort":110,"title":"辅助赛道","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_cooperation","description":"A级（每周8小时以上）、B级（每周4-8小时）、C级（每周不足4小时）","type":"textarea","sort":120,"title":"学员配合等级","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_shoot_time","description":"有半天/一天连续时间，或仅碎片化时段","type":"textarea","sort":130,"title":"连续可拍摄时间","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_appearance","description":"愿意出镜、条件式出镜、完全不出镜","type":"textarea","sort":140,"title":"出镜意愿","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_expression","description":"自然表达、提词表达、采访表达、表达较弱","type":"textarea","sort":150,"title":"表达能力等级","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_assets","description":"证书、技能、职业经历、可拍素材（学习笔记/实操场景/案例截图等）","type":"textarea","sort":160,"title":"专业优势和案例资产","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_trust","description":"学习过程、职业经历、工具、案例、资质、老师授权（可查看）","type":"textarea","sort":170,"title":"信任证据","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_risk","description":"时间不足/出镜顾虑/合规表达习惯/熟人可见/断更等","type":"textarea","sort":180,"title":"执行主要风险","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_history","description":"查看系统实际保存的记录","type":"system_history","sort":190,"title":"历史定位、采访记录","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_days7","description":"测试账号是否正常、数据是否正常","type":"textarea","sort":200,"title":"7天账号数据","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_days14","description":"发布、互动、咨询和执行指标及复核日期","type":"textarea","sort":210,"title":"14天验证指标","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_days28","description":"何时改选题、形式、类型或人群","type":"textarea","sort":220,"title":"28天调整触发条件","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_student_duties","description":"拍摄/制作/发布/评论承接/私信回复/客资登记提交等","type":"textarea","sort":230,"title":"学员承担事项","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_company_duties","description":"选题/脚本/模板/素材/剪辑支持/辅导/数据复盘等","type":"textarea","sort":240,"title":"公司承担事项","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_douyin","description":"主页搭建、头像选择、背景图设置、主页引导语设置、置顶视频描述等建议（暂时不做改平台账号，暂时不做）","type":"textarea","sort":250,"title":"抖音平台主页搭建","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_douyin_refs","description":"","type":"material_picker","sort":251,"referenceFor":"pc_homepage_douyin","materialTypeCode":"viral_account","recommendedCount":"1-3","title":"抖音平台主页搭建参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_xiaohongshu","description":"主页搭建、头像选择、背景图设置、主页引导语设置、置顶视频描述等建议（暂时不做改平台账号，暂时不做）","type":"textarea","sort":260,"title":"小红书平台主页搭建","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_xiaohongshu_refs","description":"","type":"material_picker","sort":261,"referenceFor":"pc_homepage_xiaohongshu","materialTypeCode":"viral_account","recommendedCount":"1-3","title":"小红书平台主页搭建参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_channels","description":"主页搭建、头像选择、背景图设置、主页引导语设置、置顶视频描述等建议（暂时不做改平台账号，暂时不做）","type":"textarea","sort":270,"title":"视频号平台主页搭建","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_channels_refs","description":"","type":"material_picker","sort":271,"referenceFor":"pc_homepage_channels","materialTypeCode":"viral_account","recommendedCount":"1-3","title":"视频号平台主页搭建参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_other","description":"主页搭建、头像选择、背景图设置、主页引导语设置、置顶视频描述等建议（暂时不做改平台账号，暂时不做）","type":"textarea","sort":280,"title":"其他平台主页搭建","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_homepage_other_refs","description":"","type":"material_picker","sort":281,"referenceFor":"pc_homepage_other","materialTypeCode":"viral_account","recommendedCount":"1-3","title":"其他平台主页搭建参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s0","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":290,"title":"S0期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s1","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":300,"title":"S1期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s1_refs","description":"","type":"material_picker","sort":301,"referenceFor":"pc_delivery_s1","materialTypeCode":"viral_content","recommendedCount":"2-4","title":"S1期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s2","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":310,"title":"S2期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s2_refs","description":"","type":"material_picker","sort":311,"referenceFor":"pc_delivery_s2","materialTypeCode":"viral_content","recommendedCount":"2-4","title":"S2期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s3","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":320,"title":"S3期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s3_refs","description":"","type":"material_picker","sort":321,"referenceFor":"pc_delivery_s3","materialTypeCode":"viral_content","recommendedCount":"4-6","title":"S3期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s4","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":330,"title":"S4期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s4_refs","description":"","type":"material_picker","sort":331,"referenceFor":"pc_delivery_s4","materialTypeCode":"viral_content","recommendedCount":"4-7","title":"S4期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s5","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":340,"title":"S5期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s5_refs","description":"","type":"material_picker","sort":341,"referenceFor":"pc_delivery_s5","materialTypeCode":"viral_content","recommendedCount":"4-8","title":"S5期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s6","description":"明确交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标","type":"textarea","sort":350,"title":"S6期交付约定","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_delivery_s6_refs","description":"","type":"material_picker","sort":351,"referenceFor":"pc_delivery_s6","materialTypeCode":"viral_content","recommendedCount":"6-9","title":"S6期交付约定参考素材","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_internal_goal","description":"A级：每月50-100条；B级：每月30-60条；C级：先完成账号运行与能力培养","type":"textarea","sort":360,"title":"内部交付目标倡议","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"},{"key":"pc_interview_files","description":"上传本次文稿附件","type":"attachment","sort":370,"title":"采访稿全文","enabled":true,"required":false,"systemField":false,"group":"账号定位卡"}]' AS JSON);
 OPEN templates;
 template_loop: LOOP
  FETCH templates INTO template_id_value,tenant_value,published_id;
  IF finished=1 THEN LEAVE template_loop; END IF;
  SELECT fields_json INTO fields_value FROM zsjos_director_form_template_version WHERE id=published_id AND tenant_id=tenant_value;
  IF JSON_SEARCH(fields_value,'one','pc_account_name',NULL,'$[*].key') IS NULL THEN
   SELECT COALESCE(MAX(version_no),0)+1 INTO next_version FROM zsjos_director_form_template_version WHERE template_id=template_id_value AND tenant_id=tenant_value;
   INSERT INTO zsjos_director_form_template_version(template_id,version_no,status,fields_json,published_at,tenant_id)
    VALUES(template_id_value,next_version,'published',replacement,NOW(),tenant_value);
   UPDATE zsjos_director_form_template SET published_version_id=LAST_INSERT_ID(),version=version+1 WHERE id=template_id_value AND tenant_id=tenant_value;
   UPDATE zsjos_director_form_template_version SET status='archived' WHERE id=published_id AND tenant_id=tenant_value;
  END IF;
 END LOOP;
 CLOSE templates;
 SET finished=0;
 OPEN configs;
 config_loop: LOOP
  FETCH configs INTO config_id,tenant_value,fields_value;
  IF finished=1 THEN LEAVE config_loop; END IF;
  SET merged=JSON_ARRAY(); SET i=0; SET changed=FALSE;
  WHILE i<JSON_LENGTH(fields_value) DO
   SET item=JSON_EXTRACT(fields_value,CONCAT('$[',i,']'));
   IF (JSON_UNQUOTE(JSON_EXTRACT(item,'$.group'))='POSITIONING'
       OR LEFT(JSON_UNQUOTE(JSON_EXTRACT(item,'$.key')),3)='pc_'
       OR JSON_UNQUOTE(JSON_EXTRACT(item,'$.key')) IN ('positioning_history','positioning_snapshot')) THEN
    SET changed=TRUE;
   ELSE
    SET merged=JSON_ARRAY_APPEND(merged,'$',item);
   END IF;
   SET i=i+1;
  END WHILE;
  IF changed THEN
   SELECT COALESCE(MAX(version_no),0)+1 INTO next_version FROM zsjos_media_account_field_config WHERE tenant_id=tenant_value;
   UPDATE zsjos_media_account_field_config SET status='archived' WHERE id=config_id AND tenant_id=tenant_value;
   INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,published_at,version,tenant_id)
    VALUES(next_version,'published',merged,NOW(),0,tenant_value);
  END IF;
 END LOOP;
 CLOSE configs;
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_positioning_single_source();
DROP PROCEDURE zsjos_positioning_single_source;
