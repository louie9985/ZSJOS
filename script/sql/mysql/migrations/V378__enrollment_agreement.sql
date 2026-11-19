-- UTF-8. Apply after V370 before application rollout. Tenant 1 global agreement.
-- Rerunnable; DDL commits implicitly. Preserve archived records on rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v378_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v378_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V370') OR
    NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V370') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V378 requires V370'; END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 IF root_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing workbench root'; END IF;
 CREATE TABLE IF NOT EXISTS zsjos_enrollment_agreement (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, revision BIGINT NOT NULL, published_version BIGINT NOT NULL,
 draft_body MEDIUMTEXT NOT NULL, published_body MEDIUMTEXT NOT NULL,
 creator VARCHAR(64) DEFAULT '', updater VARCHAR(64) DEFAULT '', create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted BIT NOT NULL DEFAULT 0
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_version') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_version bigint NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_template') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_template mediumtext NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_read_hash') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_read_hash varchar(64) NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_consent') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_consent varchar(160) NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_read_at') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_read_at datetime NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name='agreement_confirmed_body') THEN
 ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_confirmed_body mediumtext NULL;
 END IF;
 START TRANSACTION;
 SET @agreement_seed='<h3>职业证书培训服务协议</h3><p>甲    方：合肥中世健职业技能培训学校有限公司</p><p>联系地址：合肥市蜀山区绿地蓝海国际大厦C座2416室</p><p>联系电话：0551-62612113</p><p>乙    方：{{学员姓名}}</p><p>联系电话：{{联系电话}}</p><p>甲乙双方本着平等、自愿、互利、公平的原则，经甲乙双方协商一致，就乙方参加  {{课程名称}} 报名服务、考试等相关事项达成以下协议，以供共同遵守：</p><h3>第一条 总则</h3><p>1.乙方自愿接受甲方的报名服务，参加 {{课程名称}} 的报名。</p><p>2.乙方已经对甲方提供的师资、学习时间、学习形式、收费、退费等有关事项进行详细咨询，并表示认可。</p><h3>第二条 培训费用及支付方式</h3><p>1.乙方向甲方支付培训费用 {{实际费用}} 元（以发票或收据为准），该培训费用包含报名服务费、考试费、培训课程、考前冲刺、教材、辅导资料费用，这是首次报名，费用一次性收清，后面没有第二次的收费，不包含考试未通过的补考费。</p><p>2.培训费用乙方须在签订本协议之前一次性全额付清，甲方收到后出具收款凭证。</p><h3>第三条 合同期限</h3><p>1.本协议有效期限为从乙方支付培训费之日起开始计算到一年内考试通过即终止，考试不通过，合同一年之内有效。</p><p>2.如乙方从支付培训费之日起，一直未曾参加考试，上述有效期限最长不超过一年，该一年期限从乙方支付培训费之日起计算。从截止日起，本服务协议自动终止，甲方不再向乙方另行通知，未完成的培训和考试甲方不再负责。</p><h3>第四条 退费事项</h3><p>1.七天无理由退费：付款后7日内（含），且未提交考试报名资料的，乙方可无理由解除合同，甲方7个工作日内全额退款。若7日内已提交考试资料并完成考试报名的，不适用无理由退费。</p><p>2.常规阶梯退费（超过7日或报名已生效）：</p><p>8-30天（含），任意进度：扣除50 %</p><p>31-60天（含），进度≤50 %：扣除50 %；进度＞50 %：扣除70 %</p><p>61天及以上：不予退费（学习进度以甲方平台系统记录为准）</p><p>3.已参加考试（含缺考、迟到、违纪）或学员提供虚假材料导致报名失败的，一律不予退费。</p><p>4.乙方退费时须提供甲方开具的收费凭证原件，按照甲方的退费流程进行。</p><h3>第五条 甲方的权利及义务</h3><p>1.甲方有义务审查乙方的报名材料的真实性并且协助乙方参加该职业考证报名，对于不符合报名条件的材料须及时告知乙方。</p><p>2.甲方应按照协议中的服务项目，对乙方进行服务。</p><p>3.甲方有权在服务期间根据情况决定考前辅导安排等事宜。</p><p>4.如因不可抗力因素（如国家政策调整、洪水、地震等）造成乙方不能如期参加考试，由此造成的损失由乙方自行承担，乙方需及时通知甲方，甲方协调帮助安排乙方延期参加考试。</p><h3>第六条 乙方的权利及义务</h3><p>1.乙方须按时足额缴纳培训费用。</p><p>2.乙方须提供真实有效的身份信息和考试报名信息（包括但不限于身份证、毕业证、证件照、工作证明等）。</p><p>3.乙方如有特殊情况应提前联系甲方教务把考试时间延期，如名单已经上报给鉴定单位而不能准时参加考试，那么只能算作乙方缺考，后期补考的，需缴纳补考费用（补考费用按当期鉴定部门规定价格收）；若名单还未上报给鉴定单位，则不加收费用，课程可以继续正常学习。</p><h3>第七条 违约责任</h3><p>乙方若违反本协议约定的，甲方有权关闭甲方的服务学习权限，且乙方已交全部费用不予办理退款，因此给甲方造成损失的，乙方须予以赔偿。</p><h3>第八条 保密条款</h3><p>甲乙双方无论在协议期内或协议终止后，未经对方书面同意，另一方不得以任何方式将本协议的具体内容及相关事宜披露给任何第三方，否则，一方应当赔偿因此给对方造成的损失。</p><h3>第九条 争议解决</h3><p>本合同在履行过程中，如发生争议，由双方协商解决；如协商不成，则向甲方所在地有管辖权的人民法院提起诉讼解决。</p><h3>第十条 其他事项</h3><p>1.乙方应按协议约定足额向甲方支付培训费用，若因乙方拖延支付培训费用而影响考试的，造成的损失由乙方自行承担。</p><p>2. 乙方不按照考试规定参加考试（缺考、代考、迟到，或考试时没带证件）造成的所有后果由乙方自己承担，甲方不予负责。</p><p>3.甲方通过乙方电子签署的邮寄地址寄送相关文件资料物品时，无论乙方是不是实际签收人，都视为乙方签收成功。</p><p>4.本协议未尽事项可由双方约定后签订补充协议，补充协议具有同等效力。</p><p>甲    方：合肥中世健职业技能培训学校有限公司</p><p>签订日期：</p><p>乙    方：{{学员姓名}}</p><p>签订日期：</p>';
 INSERT INTO zsjos_enrollment_agreement(id,tenant_id,revision,published_version,draft_body,published_body) SELECT 1,1,1,1,@agreement_seed,@agreement_seed WHERE NOT EXISTS(SELECT 1 FROM zsjos_enrollment_agreement WHERE id=1);
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '协议维护','zsjos:agreement:query',2,50,root_id,'enrollment-agreement','ep:document','zsjos/enrollment-agreement/index','admin_embed',0,1,1,1,'V378','V378',0 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:agreement:query');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:agreement:query' AND parent_id=root_id AND type=2 AND path='enrollment-agreement' AND deleted=0)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Agreement menu conflict'; END IF;
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:agreement:query' AND deleted=0;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '编辑协议','zsjos:agreement:update',3,50,page_id,'','ep:document','','native',0,1,1,1,'V378','V378',0 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:agreement:update');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:agreement:update' AND parent_id=page_id AND type=3 AND path='' AND deleted=0)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Agreement menu conflict'; END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '发布协议','zsjos:agreement:publish',3,50,page_id,'','ep:document','','native',0,1,1,1,'V378','V378',0 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:agreement:publish');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:agreement:publish' AND parent_id=page_id AND type=3 AND path='' AND deleted=0)<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Agreement menu conflict'; END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND is_nullable='YES' AND ((column_name='agreement_version' AND data_type='bigint') OR (column_name='agreement_template' AND data_type='mediumtext') OR (column_name='agreement_read_hash' AND data_type='varchar') OR (column_name='agreement_consent' AND data_type='varchar') OR (column_name='agreement_read_at' AND data_type='datetime') OR (column_name='agreement_confirmed_body' AND data_type='mediumtext')))<>6
 OR NOT EXISTS (SELECT 1 FROM zsjos_enrollment_agreement WHERE id=1 AND tenant_id=1 AND deleted=0 AND published_version>0 AND CHAR_LENGTH(published_body)>0)
 OR NOT EXISTS (SELECT 1 FROM system_menu WHERE id=page_id AND component='zsjos/enrollment-agreement/index' AND workbench_render_mode='admin_embed') THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V378 agreement postcondition failed'; END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V378','报名协议',SHA2('V378__enrollment_agreement.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V378');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V378','报名协议',SHA2('V378__enrollment_agreement.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V378');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v378_apply();
DROP PROCEDURE zsjos_v378_apply;
