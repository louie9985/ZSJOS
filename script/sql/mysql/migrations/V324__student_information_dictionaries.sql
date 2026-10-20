-- UTF-8. User-approved dedicated collection dictionaries; requires V323 in both ledgers.
-- Four types, 56 ethnic groups, 2 retraining, 8 education and 4 registration choices.
-- Insert missing entries only; preserve administrator labels/status/deletions and template snapshots.
-- No role assignments or template publication. Transactional repeatable recovery; retain on rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v324_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v324_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V323') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 requires V323 in both ledgers';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT type FROM system_dict_type WHERE type='zsjos_student_info_ethnicity' AND deleted=0 GROUP BY type HAVING COUNT(*)>1)
 OR EXISTS(SELECT value FROM system_dict_data WHERE dict_type='zsjos_student_info_ethnicity' AND deleted=0 GROUP BY value HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 duplicate dictionary identities require review';
 END IF;
 INSERT INTO system_dict_type(name,type,status,remark,creator,updater)
 SELECT '学员收集表民族','zsjos_student_info_ethnicity',0,'报名履约学员信息收集表','migration-V324','migration-V324'
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_ethnicity');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
 SELECT s.sort,s.label,s.value,'zsjos_student_info_ethnicity',0,'','','报名履约学员信息收集表','migration-V324','migration-V324'
 FROM (
 SELECT 1 sort,'汉族' label,'01' value
 UNION ALL SELECT 2 sort,'蒙古族' label,'02' value
 UNION ALL SELECT 3 sort,'回族' label,'03' value
 UNION ALL SELECT 4 sort,'藏族' label,'04' value
 UNION ALL SELECT 5 sort,'维吾尔族' label,'05' value
 UNION ALL SELECT 6 sort,'苗族' label,'06' value
 UNION ALL SELECT 7 sort,'彝族' label,'07' value
 UNION ALL SELECT 8 sort,'壮族' label,'08' value
 UNION ALL SELECT 9 sort,'布依族' label,'09' value
 UNION ALL SELECT 10 sort,'朝鲜族' label,'10' value
 UNION ALL SELECT 11 sort,'满族' label,'11' value
 UNION ALL SELECT 12 sort,'侗族' label,'12' value
 UNION ALL SELECT 13 sort,'瑶族' label,'13' value
 UNION ALL SELECT 14 sort,'白族' label,'14' value
 UNION ALL SELECT 15 sort,'土家族' label,'15' value
 UNION ALL SELECT 16 sort,'哈尼族' label,'16' value
 UNION ALL SELECT 17 sort,'哈萨克族' label,'17' value
 UNION ALL SELECT 18 sort,'傣族' label,'18' value
 UNION ALL SELECT 19 sort,'黎族' label,'19' value
 UNION ALL SELECT 20 sort,'傈僳族' label,'20' value
 UNION ALL SELECT 21 sort,'佤族' label,'21' value
 UNION ALL SELECT 22 sort,'畲族' label,'22' value
 UNION ALL SELECT 23 sort,'高山族' label,'23' value
 UNION ALL SELECT 24 sort,'拉祜族' label,'24' value
 UNION ALL SELECT 25 sort,'水族' label,'25' value
 UNION ALL SELECT 26 sort,'东乡族' label,'26' value
 UNION ALL SELECT 27 sort,'纳西族' label,'27' value
 UNION ALL SELECT 28 sort,'景颇族' label,'28' value
 UNION ALL SELECT 29 sort,'柯尔克孜族' label,'29' value
 UNION ALL SELECT 30 sort,'土族' label,'30' value
 UNION ALL SELECT 31 sort,'达斡尔族' label,'31' value
 UNION ALL SELECT 32 sort,'仫佬族' label,'32' value
 UNION ALL SELECT 33 sort,'羌族' label,'33' value
 UNION ALL SELECT 34 sort,'布朗族' label,'34' value
 UNION ALL SELECT 35 sort,'撒拉族' label,'35' value
 UNION ALL SELECT 36 sort,'毛南族' label,'36' value
 UNION ALL SELECT 37 sort,'仡佬族' label,'37' value
 UNION ALL SELECT 38 sort,'锡伯族' label,'38' value
 UNION ALL SELECT 39 sort,'阿昌族' label,'39' value
 UNION ALL SELECT 40 sort,'普米族' label,'40' value
 UNION ALL SELECT 41 sort,'塔吉克族' label,'41' value
 UNION ALL SELECT 42 sort,'怒族' label,'42' value
 UNION ALL SELECT 43 sort,'乌孜别克族' label,'43' value
 UNION ALL SELECT 44 sort,'俄罗斯族' label,'44' value
 UNION ALL SELECT 45 sort,'鄂温克族' label,'45' value
 UNION ALL SELECT 46 sort,'德昂族' label,'46' value
 UNION ALL SELECT 47 sort,'保安族' label,'47' value
 UNION ALL SELECT 48 sort,'裕固族' label,'48' value
 UNION ALL SELECT 49 sort,'京族' label,'49' value
 UNION ALL SELECT 50 sort,'塔塔尔族' label,'50' value
 UNION ALL SELECT 51 sort,'独龙族' label,'51' value
 UNION ALL SELECT 52 sort,'鄂伦春族' label,'52' value
 UNION ALL SELECT 53 sort,'赫哲族' label,'53' value
 UNION ALL SELECT 54 sort,'门巴族' label,'54' value
 UNION ALL SELECT 55 sort,'珞巴族' label,'55' value
 UNION ALL SELECT 56 sort,'基诺族' label,'56' value
 ) s WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_student_info_ethnicity' AND value=s.value)
 AND EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_ethnicity' AND deleted=0);
 IF EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_ethnicity' AND deleted=0)
 AND (SELECT COUNT(DISTINCT value) FROM system_dict_data WHERE dict_type='zsjos_student_info_ethnicity' AND value IN ('01','02','03','04','05','06','07','08','09','10','11','12','13','14','15','16','17','18','19','20','21','22','23','24','25','26','27','28','29','30','31','32','33','34','35','36','37','38','39','40','41','42','43','44','45','46','47','48','49','50','51','52','53','54','55','56'))<>56 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 dictionary postcondition failed';
 END IF;
 IF EXISTS(SELECT type FROM system_dict_type WHERE type='zsjos_student_info_retraining' AND deleted=0 GROUP BY type HAVING COUNT(*)>1)
 OR EXISTS(SELECT value FROM system_dict_data WHERE dict_type='zsjos_student_info_retraining' AND deleted=0 GROUP BY value HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 duplicate dictionary identities require review';
 END IF;
 INSERT INTO system_dict_type(name,type,status,remark,creator,updater)
 SELECT '学员收集表是否复训','zsjos_student_info_retraining',0,'报名履约学员信息收集表','migration-V324','migration-V324'
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_retraining');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
 SELECT s.sort,s.label,s.value,'zsjos_student_info_retraining',0,'','','报名履约学员信息收集表','migration-V324','migration-V324'
 FROM (
 SELECT 1 sort,'是' label,'yes' value
 UNION ALL SELECT 2 sort,'否' label,'no' value
 ) s WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_student_info_retraining' AND value=s.value)
 AND EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_retraining' AND deleted=0);
 IF EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_retraining' AND deleted=0)
 AND (SELECT COUNT(DISTINCT value) FROM system_dict_data WHERE dict_type='zsjos_student_info_retraining' AND value IN ('yes','no'))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 dictionary postcondition failed';
 END IF;
 IF EXISTS(SELECT type FROM system_dict_type WHERE type='zsjos_student_info_education_level' AND deleted=0 GROUP BY type HAVING COUNT(*)>1)
 OR EXISTS(SELECT value FROM system_dict_data WHERE dict_type='zsjos_student_info_education_level' AND deleted=0 GROUP BY value HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 duplicate dictionary identities require review';
 END IF;
 INSERT INTO system_dict_type(name,type,status,remark,creator,updater)
 SELECT '学员收集表现学历','zsjos_student_info_education_level',0,'报名履约学员信息收集表','migration-V324','migration-V324'
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_education_level');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
 SELECT s.sort,s.label,s.value,'zsjos_student_info_education_level',0,'','','报名履约学员信息收集表','migration-V324','migration-V324'
 FROM (
 SELECT 1 sort,'博士' label,'doctor' value
 UNION ALL SELECT 2 sort,'硕士' label,'master' value
 UNION ALL SELECT 3 sort,'本科' label,'bachelor' value
 UNION ALL SELECT 4 sort,'大专' label,'junior_college' value
 UNION ALL SELECT 5 sort,'中专' label,'technical_secondary' value
 UNION ALL SELECT 6 sort,'高中' label,'high_school' value
 UNION ALL SELECT 7 sort,'初中' label,'junior_high' value
 UNION ALL SELECT 8 sort,'其他' label,'other' value
 ) s WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_student_info_education_level' AND value=s.value)
 AND EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_education_level' AND deleted=0);
 IF EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_education_level' AND deleted=0)
 AND (SELECT COUNT(DISTINCT value) FROM system_dict_data WHERE dict_type='zsjos_student_info_education_level' AND value IN ('doctor','master','bachelor','junior_college','technical_secondary','high_school','junior_high','other'))<>8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 dictionary postcondition failed';
 END IF;
 IF EXISTS(SELECT type FROM system_dict_type WHERE type='zsjos_student_info_registration_level' AND deleted=0 GROUP BY type HAVING COUNT(*)>1)
 OR EXISTS(SELECT value FROM system_dict_data WHERE dict_type='zsjos_student_info_registration_level' AND deleted=0 GROUP BY value HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 duplicate dictionary identities require review';
 END IF;
 INSERT INTO system_dict_type(name,type,status,remark,creator,updater)
 SELECT '学员收集表报名层次','zsjos_student_info_registration_level',0,'报名履约学员信息收集表','migration-V324','migration-V324'
 WHERE NOT EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_registration_level');
 INSERT INTO system_dict_data(sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
 SELECT s.sort,s.label,s.value,'zsjos_student_info_registration_level',0,'','','报名履约学员信息收集表','migration-V324','migration-V324'
 FROM (
 SELECT 1 sort,'高起专' label,'high_to_college' value
 UNION ALL SELECT 2 sort,'专升本' label,'college_to_bachelor' value
 UNION ALL SELECT 3 sort,'统考硕士研究生' label,'national_master' value
 UNION ALL SELECT 4 sort,'同等学历申硕' label,'equivalent_master' value
 ) s WHERE NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_student_info_registration_level' AND value=s.value)
 AND EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_registration_level' AND deleted=0);
 IF EXISTS(SELECT 1 FROM system_dict_type WHERE type='zsjos_student_info_registration_level' AND deleted=0)
 AND (SELECT COUNT(DISTINCT value) FROM system_dict_data WHERE dict_type='zsjos_student_info_registration_level' AND value IN ('high_to_college','college_to_bachelor','national_master','equivalent_master'))<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V324 dictionary postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V324','学员收集表业务字典',SHA2('V324__student_information_dictionaries.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V324');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V324','学员收集表业务字典',SHA2('V324__student_information_dictionaries.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V324');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v324_apply();
DROP PROCEDURE zsjos_v324_apply;
