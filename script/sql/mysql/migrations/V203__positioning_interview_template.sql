-- UTF-8. Development baseline V203; requires V202 and V130 template tables.
-- Scope: three empty interview tables, one default outline per tenant, missing button metadata.
-- Repeatable; no role grants, business-row deletion or historical snapshot conversion.
-- Existing templates/versions are never overwritten. Correct an earlier placeholder separately
-- by publishing a new version; do not replace an administrator-maintained published version.
-- Rollback: restore a backup before deploying dependent code; never drop populated tables.
SET NAMES utf8mb4;

-- BEGIN POSITIONING INTERVIEW MENU RETIREMENT
-- Development correction, after V202/V142 menu identity repair. Re-running only changes
-- these existing menu labels and retires the obsolete questionnaire operation. Stable
-- menu IDs, paths, template permission codes, role grants and business rows are preserved.
-- Shared development execution requires exact-target approval. No deployed upgrade is implied.
UPDATE system_menu
SET name = CASE permission
      WHEN 'zsjos:director-interview-template:query' THEN '定位访谈大纲配置'
      WHEN 'zsjos:director-interview-template:update' THEN '保存定位访谈大纲'
      WHEN 'zsjos:director-interview-template:publish' THEN '发布定位访谈大纲'
    END,
    updater='V203', update_time=NOW()
WHERE deleted=b'0' AND permission IN ('zsjos:director-interview-template:query',
    'zsjos:director-interview-template:update','zsjos:director-interview-template:publish')
  AND name <> CASE permission
      WHEN 'zsjos:director-interview-template:query' THEN '定位访谈大纲配置'
      WHEN 'zsjos:director-interview-template:update' THEN '保存定位访谈大纲'
      WHEN 'zsjos:director-interview-template:publish' THEN '发布定位访谈大纲'
    END;
UPDATE system_menu SET status=1, visible=b'0', updater='V203', update_time=NOW()
WHERE deleted=b'0' AND type=3 AND permission='zsjos:student:director-interview'
  AND (status<>1 OR visible<>b'0');
-- Rollback: restore captured name/status/visible for these menu rows only. Old application
-- code must be restored separately to re-enable questionnaires; historical data is untouched.
-- END POSITIONING INTERVIEW MENU RETIREMENT

CREATE TABLE IF NOT EXISTS zsjos_student_positioning_interview (
  id bigint NOT NULL AUTO_INCREMENT,
  student_person_id bigint NOT NULL, service_relation_id bigint NOT NULL, director_user_id bigint NOT NULL,
  template_id bigint NOT NULL, template_version_id bigint NOT NULL, template_snapshot_json longtext NOT NULL,
  status_options_snapshot_json longtext DEFAULT NULL,
  status varchar(32) NOT NULL DEFAULT 'draft', version int NOT NULL DEFAULT 0,
  collected_at date DEFAULT NULL, completed_at datetime DEFAULT NULL, completed_by bigint DEFAULT NULL,
  idempotency_key varchar(100) DEFAULT NULL, request_fingerprint varchar(64) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  active_draft_student bigint GENERATED ALWAYS AS (CASE WHEN status='draft' AND deleted=b'0' THEN service_relation_id ELSE NULL END) STORED,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_current_draft (tenant_id,active_draft_student),
  KEY idx_tenant_student_status (tenant_id,student_person_id,status,deleted),
  KEY idx_tenant_relation (tenant_id,service_relation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员定位访谈';

-- BEGIN V203 RELATION DRAFT CORRECTION
-- Development-only correction of the original V203 baseline, not an upgrade contract
-- for an already deployed release. Changes only the generated draft uniqueness expression.
-- Retain the legacy generated column/index names to avoid breaking schema consumers.
-- Same tenant/relation keeps one draft; distinct relations may share student/director.
-- Existing snapshots, draft rows and attachments are retained. Repeatable after original V203.
-- Rollback to student uniqueness is impossible once multiple relation drafts exist for a student;
-- preserve data and keep corrected application/schema together. Shared DB execution needs scoped approval.
SET @v203_relation_draft = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='zsjos_student_positioning_interview'
 AND column_name='active_draft_student' AND generation_expression NOT LIKE '%service_relation_id%'),
 'ALTER TABLE zsjos_student_positioning_interview MODIFY COLUMN active_draft_student bigint GENERATED ALWAYS AS (CASE WHEN status=''draft'' AND deleted=b''0'' THEN service_relation_id ELSE NULL END) STORED',
 'SELECT 1');
PREPARE v203_relation_stmt FROM @v203_relation_draft;
EXECUTE v203_relation_stmt;
DEALLOCATE PREPARE v203_relation_stmt;
-- END V203 RELATION DRAFT CORRECTION

CREATE TABLE IF NOT EXISTS zsjos_student_positioning_interview_item (
  id bigint NOT NULL AUTO_INCREMENT, interview_id bigint NOT NULL,
  field_key varchar(64) NOT NULL, title_snapshot varchar(100) NOT NULL,
  interview_note_snapshot text, confirmation_status varchar(40) DEFAULT NULL,
  status_label_snapshot varchar(100) DEFAULT NULL, remark text, field_value text,
  sort int NOT NULL DEFAULT 0, system_field bit(1) NOT NULL DEFAULT b'0',
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  PRIMARY KEY (id), KEY idx_tenant_interview (tenant_id,interview_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位访谈字段快照';

CREATE TABLE IF NOT EXISTS zsjos_student_positioning_interview_attachment (
  id bigint NOT NULL AUTO_INCREMENT, interview_id bigint DEFAULT NULL,
  student_person_id bigint NOT NULL, file_id bigint NOT NULL,
  file_name varchar(512) NOT NULL, mime_type varchar(255) DEFAULT NULL,
  file_size bigint NOT NULL, uploaded_by bigint NOT NULL, directory varchar(512) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_file (tenant_id,file_id,deleted),
  KEY idx_tenant_interview (tenant_id,interview_id,deleted),
  KEY idx_tenant_student (tenant_id,student_person_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位访谈稿引用';

-- These three values are the approved fixed confirmation protocol, not editable business choices.
-- Repair the same development baseline if its initial table was already created.
SET @v203_add_status = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_student_positioning_interview' AND column_name='status_options_snapshot_json'),
 'SELECT 1','ALTER TABLE zsjos_student_positioning_interview ADD COLUMN status_options_snapshot_json longtext DEFAULT NULL');
PREPARE v203_stmt FROM @v203_add_status;
EXECUTE v203_stmt;
DEALLOCATE PREPARE v203_stmt;
SET @v203_statuses = JSON_OBJECT('COMMUNICATED_DOCUMENT','已沟通，见文稿','NOT_COMMUNICATED','未沟通','CLIENT_REFUSED','客户拒绝回答');
SET @v203_fields = JSON_ARRAY(
  JSON_OBJECT('key','studentIdentity','title','学员姓名/编号','interviewNote','','type','text','enabled',TRUE,'required',FALSE,'systemField',TRUE,'sort',10,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','familyAge','title','家庭情况、个人年龄','interviewNote','可说可不说，尊重学员的意见；','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',20,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','careerHistory','title','职业生平','interviewNote','干过哪些工作，目前在哪些行业有哪些水平证明','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',30,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','education','title','学历经历','interviewNote','学历教育、职业教育、技能学习都有哪些经历？','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',40,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','learningMotivation','title','学习初心','interviewNote','进入大健康行业初心、愿景，触动点是什么','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',50,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','joiningGoal','title','学员加入目标','interviewNote','兼职变现、新媒体能力成长、职业转型、IP打造、业务增量等','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',60,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','qualificationStage','title','当前学习/资格阶段','interviewNote','未报名/在学/备考/持证/从业/成熟服务者','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',70,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','graduationGoal','title','期望达到的结业目标','interviewNote','','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',80,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','audienceInitial','title','目标用户','interviewNote','最想吸引谁？他们有什么痛点？','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',90,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','primaryTrack','title','主赛道','interviewNote','营养/心理/中医适宜技术/药师/健康管理/慢病/其他','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',100,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','secondaryTrack','title','辅助赛道','interviewNote','最多1项；说明为什么对主用户有帮助','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',110,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','accountPositioning','title','账号定位商榷','interviewNote','1-12类','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',120,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','professionalPositioning','title','专业定位商榷','interviewNote','T1-T9','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',130,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','customerGroup','title','目标客户人群商榷','interviewNote','','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',140,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','productTarget','title','承接产品目标商榷','interviewNote','引流对营养师、健康管理、轻创博主等哪个项目的学员','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',150,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','cooperationLevel','title','学员配合等级','interviewNote','□A级（每周8小时以上）□B级（每周4-8小时）□C级（每周不足4小时）','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',160,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','weeklyHours','title','每周总投入时间','interviewNote','____小时/周','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',170,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','filmingTime','title','连续可拍摄时间','interviewNote','□有半天/一天连续时间 □仅碎片化时段','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',180,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','cameraWillingness','title','出镜意愿','interviewNote','□愿意出镜 □条件式出镜 □完全不出镜','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',190,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','expressionLevel','title','表达能力等级','interviewNote','□自然表达 □提词表达 □采访表达 □表达较弱','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',200,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','professionalAssets','title','专业优势和案例资产','interviewNote','证书、技能、职业经历、可拍素材（学习笔记/实操场景/案例截图等）','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',210,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','recommendedPlatforms','title','推荐平台','interviewNote','□抖音 □小红书 □视频号（可多选）','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',220,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','accountMode','title','推荐账号模式','interviewNote','从12类账号类型中选1个主定位','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',230,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','contentFormat','title','内容形式','interviewNote','与出镜条件、表达能力、时间和拍剪权益匹配','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',240,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','trustEvidence','title','信任证据','interviewNote','学习过程、职业经历、工具、案例、资质、老师授权','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',250,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','prohibitedExpression','title','禁止表达','interviewNote','按赛道和个人身份列出','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',260,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','accountStartupTest','title','账号启动测试','interviewNote','测试账号是否正常、数据是否正常','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',270,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','sevenDayData','title','7天账号数据','interviewNote','测试账号是否正常、数据是否正常','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',280,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','fourteenDayMetrics','title','14天验证指标','interviewNote','发布、互动、咨询和执行指标及复核日期','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',290,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','adjustmentTriggers','title','28天调整触发条件','interviewNote','何时改选题、形式、类型或人群','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',300,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','studentResponsibilities','title','学员承担事项','interviewNote','拍摄/制作/发布/评论承接/私信回复/客资登记提交等','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',310,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','companyResponsibilities','title','公司承担事项','interviewNote','选题/脚本/模板/素材/剪辑支持/辅导/数据复盘等','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',320,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','executionRisks','title','执行主要风险','interviewNote','时间不足/出镜顾虑/合规表达习惯/熟人可见/断更等','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',330,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s0Delivery','title','S0期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',340,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s1Delivery','title','S1期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',350,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s2Delivery','title','S2期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',360,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s3Delivery','title','S3期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',370,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s4Delivery','title','S4期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',380,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s5Delivery','title','S5期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',390,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','s6Delivery','title','S6期交付约定','interviewNote','明确交付交付形式、交付数量、交付频次、交付内容、客资结果；学员需要学习的内容目标、结果目标','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',400,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','monthOneGoal','title','第1个月启动目标','interviewNote','发布条数、形成栏目、完成首轮复盘','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',410,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','monthTwoGoal','title','第2个月验证目标','interviewNote','验证形式可持续性、承接链路跑通、首批咨询出现','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',420,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','monthThreeSixGoal','title','第3-6个月客资目标','interviewNote','□A级：每月50-100条 □B级：每月30-60条 □C级：先完成账号运行与能力培养','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',430,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','oneSentencePositioning','title','一句话定位','interviewNote','说清谁、服务谁、解决什么问题、凭什么可信，例如：二胎宝妈从学习营养师开始，记录从家庭健康到轻创获客的成长过程','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',440,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','audienceDetailed','title','目标用户','interviewNote','至少包含职业/阶段、核心需求、典型场景和付费可能','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',450,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','personaTrust','title','人设与信任','interviewNote','与真实经历、资质和案例一致，不虚构权威','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',460,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','platformMix','title','平台组合','interviewNote','明确抖音、小红书、视频号的主次和内容差异','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',470,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','acquisitionPath','title','获客路径','interviewNote','内容曝光→评论/私信→需求筛选→授权留资→客资提交→销售承接','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',480,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','leadPath','title','客资路径','interviewNote','明确评论、私信、企业微信或客资系统的承接方式','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',490,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','riskBoundaries','title','风险边界','interviewNote','列明资格、疗效、收入、就业和平台敏感表达','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',500,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','targetAgreement','title','目标约定','interviewNote','1月：首先推荐评论区管理、私信区管理，先和客户聊起来；变现是目的之一\n2月：梳理运营的内容生产节奏，感受运营的生产方向；可以和运营讨论内容创作，但是需要听运营的\n3月：客资提取娴熟，每个月不低于30条客资，成交率不低于10%，评估自己的时间\n4月：继续，或者有多余时间可以参与到内容创作中来，尝试先做文案整理，参与拍摄\n5月：继续，或者还有多余时间参与到拍摄、兼职、制作中来\n6月：继续，或者离开运营，运营只是把关和审核，全部自己制作；','type','text','enabled',TRUE,'required',TRUE,'systemField',FALSE,'sort',510,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE),
  JSON_OBJECT('key','collectedAt','title','采集时间（选择）','interviewNote','__年__月__日','type','date','enabled',TRUE,'required',FALSE,'systemField',TRUE,'sort',520,'allowRemark',TRUE,'requireAttachment',FALSE,'studentVisible',FALSE)
);

START TRANSACTION;
INSERT INTO zsjos_director_form_template
 (scene,template_code,name,default_template,status,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 'director_positioning_interview','default_positioning_interview','默认定位访谈大纲',b'1','enabled',0,'V203',NOW(),'V203',NOW(),b'0',t.id
FROM system_tenant t
WHERE t.deleted=b'0' AND NOT EXISTS (
 SELECT 1 FROM zsjos_director_form_template x WHERE x.tenant_id=t.id AND x.scene='director_positioning_interview' AND x.deleted=b'0');
INSERT INTO zsjos_director_form_template_version
 (template_id,version_no,status,fields_json,published_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT t.id,1,'published',@v203_fields,NOW(),0,'V203',NOW(),'V203',NOW(),b'0',t.tenant_id
FROM zsjos_director_form_template t
WHERE t.scene='director_positioning_interview' AND t.template_code='default_positioning_interview'
 AND t.creator='V203' AND t.deleted=b'0' AND NOT EXISTS
 (SELECT 1 FROM zsjos_director_form_template_version v WHERE v.template_id=t.id AND v.deleted=b'0');
UPDATE zsjos_director_form_template t
 JOIN zsjos_director_form_template_version v ON v.template_id=t.id AND v.version_no=1 AND v.deleted=b'0'
SET t.published_version_id=v.id
WHERE t.scene='director_positioning_interview' AND t.template_code='default_positioning_interview'
 AND t.creator='V203' AND t.published_version_id IS NULL;

-- Add configurable operations beneath the existing media-student page without changing role grants.
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT op.name,op.permission,3,op.sort,p.id,'','','',0,b'1',b'1',b'1','V203',NOW(),'V203',NOW(),b'0'
FROM (SELECT MIN(id) id FROM system_menu WHERE permission='zsjos:media-student:query-my' AND deleted=b'0') p
JOIN (
 SELECT '定位访谈填写' name,'zsjos:student:positioning-interview' permission,91 sort UNION ALL
 SELECT '查看定位访谈','zsjos:student:positioning-interview-query',92 UNION ALL
 SELECT '完成定位访谈','zsjos:student:positioning-interview-complete',93
) op
WHERE p.id IS NOT NULL AND NOT EXISTS
 (SELECT 1 FROM system_menu m WHERE m.permission=op.permission AND m.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V203','student positioning interview and complete outline',SHA2('V203__positioning_interview_template.sql',256),NOW())
ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V203','student positioning interview and complete outline',SHA2('V203__positioning_interview_template.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
COMMIT;
