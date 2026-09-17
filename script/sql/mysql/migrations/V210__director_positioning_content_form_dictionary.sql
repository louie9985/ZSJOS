SET NAMES utf8mb4;
-- V210 adds the administrator-maintained content form dictionary for positioning cards.
INSERT INTO system_dict_type (name,type,status,remark,creator,create_time,updater,update_time,deleted)
SELECT '编导主要内容形式','zsjos_director_content_form',0,'编导定位卡主要内容形式；管理员可配置', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='zsjos_director_content_form' AND deleted=b'0');

INSERT INTO system_dict_data (sort, label, value, dict_type, status, remark, creator, create_time, updater, update_time, deleted)
SELECT seed.sort, seed.label, seed.value, 'zsjos_director_content_form', 0, '编导定位卡预置内容形式', '1', NOW(), '1', NOW(), b'0'
FROM (
 SELECT 1 sort,'健康科普KOC图文账号' label,'health_koc_image_text' value UNION ALL
 SELECT 2,'伴学备考KOC图文账号','study_koc_image_text' UNION ALL
 SELECT 3,'伴学备考KOC视频账号','study_koc_video' UNION ALL
 SELECT 4,'考试介绍资料流图文账号','exam_info_image_text' UNION ALL
 SELECT 5,'学姐推荐KOC图文账号','senior_koc_image_text' UNION ALL
 SELECT 6,'健康科普KOC视频账号','health_koc_video' UNION ALL
 SELECT 7,'副业变现KOC视频账号','side_hustle_koc_video' UNION ALL
 SELECT 8,'创业变现KOC视频账号','entrepreneur_koc_video' UNION ALL
 SELECT 9,'职业赋能KOC视频账号','career_enablement_koc_video' UNION ALL
 SELECT 10,'学姐推荐KOC视频账号','senior_koc_video' UNION ALL
 SELECT 11,'名师IP网红KOL视频账号','teacher_kol_video' UNION ALL
 SELECT 12,'职业规划师职工图文账号','career_planner_staff_image_text' UNION ALL
 SELECT 13,'学习规划师职工图文账号','study_planner_staff_image_text' UNION ALL
 SELECT 14,'职业规划师职工视频账号','career_planner_staff_video' UNION ALL
 SELECT 15,'学习规划师职工视频账号','study_planner_staff_video' UNION ALL
 SELECT 16,'课程顾问职工视频账号','course_consultant_staff_video' UNION ALL
 SELECT 17,'课程顾问职工图文账号','course_consultant_staff_image_text' UNION ALL
 SELECT 18,'课程助教职工视频账号','course_assistant_staff_video' UNION ALL
 SELECT 19,'课程助教职工图文账号','course_assistant_staff_image_text' UNION ALL
 SELECT 20,'带着学员做轻创-运营视频账号','light_entrepreneur_operation_video' UNION ALL
 SELECT 21,'带着学员做轻创-运营图文账号','light_entrepreneur_operation_image_text' UNION ALL
 SELECT 22,'考试介绍资料流视频账号','exam_info_video'
) seed
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_director_content_form' AND value=seed.value AND deleted=b'0');

-- Development baseline: the director card owns positioning; account config is read-only projection.
SOURCE script/sql/mysql/positioning-single-source.sql;
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
SELECT 'V210','director positioning card',SHA2('V210__director_positioning_content_form_dictionary.sql',256),NOW()
WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V210');
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
SELECT 'core','V210','director positioning card',SHA2('V210__director_positioning_content_form_dictionary.sql',256),'baseline',NOW()
WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V210');
