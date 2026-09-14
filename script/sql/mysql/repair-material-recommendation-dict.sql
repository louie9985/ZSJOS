-- UTF-8. 开发库一次性修正：素材推荐维度改为按字段字典自动归属，账号类型字典统一为 zsjos_persona_type。
-- 背景：字段级"推荐维度"人工选择已移除，推荐维度由字段字典自动归属。爆款账号已发布模板 V2 曾被
-- 推荐维度配置把 adapted_persona_types 的字典从 zsjos_persona_type 覆盖为 zsjos_material_account_type，
-- 需要恢复字典、移除遗留 recommendationDimension，并同步模板哈希。
-- 依赖：V194 已应用且 viral_account 已发布版本 2（若不存在则全部语句无影响）。
-- 数据范围：仅 viral_account 的已发布模板 V2、该类型的推荐维度配置、zsjos_material_account_type 字典。
-- 不修改素材、素材版本、媒体账号画像或内容审核配置数据（该字典无真实业务数据，画像字段无已填值）。
-- 可重复性：所有更新均带前置条件，重复执行不产生额外变化。
-- 恢复：字典为逻辑删除，可按 type/dict_type 恢复 deleted=b'0'；模板字段与哈希需按业务确认后重新发布修正。
-- 注意：schema_hash 为 Java 端对模板字段重新序列化后的 SHA-256，修正字段后必须同步为常量值。

SET NAMES utf8mb4;

SET @viral_type_id = (SELECT id FROM `zsjos_material_type`
                      WHERE code='viral_account' AND deleted=b'0' LIMIT 1);
SET @viral_schema_id = (SELECT id FROM `zsjos_material_schema_version`
                        WHERE material_type_id=@viral_type_id AND version_no=2 AND deleted=b'0' LIMIT 1);

SET @persona_base = CONCAT('$[', SUBSTRING_INDEX(SUBSTRING_INDEX(
    (SELECT JSON_UNQUOTE(JSON_SEARCH(fields_json, 'one', 'adapted_persona_types', NULL, '$[*].key'))
     FROM `zsjos_material_schema_version` WHERE id=@viral_schema_id), '[', -1), ']', 1), ']');
SET @business_base = CONCAT('$[', SUBSTRING_INDEX(SUBSTRING_INDEX(
    (SELECT JSON_UNQUOTE(JSON_SEARCH(fields_json, 'one', 'adapted_business_positions', NULL, '$[*].key'))
     FROM `zsjos_material_schema_version` WHERE id=@viral_schema_id), '[', -1), ']', 1), ']');

-- 1. 恢复适配账号类型字典并移除推荐维度人工配置
UPDATE `zsjos_material_schema_version`
SET fields_json = JSON_REMOVE(
        JSON_SET(fields_json, CONCAT(@persona_base, '.dictType'), 'zsjos_persona_type'),
        CONCAT(@persona_base, '.recommendationDimension'))
WHERE id=@viral_schema_id
  AND JSON_UNQUOTE(JSON_EXTRACT(fields_json, CONCAT(@persona_base, '.dictType')))='zsjos_material_account_type';
SET @persona_repaired = ROW_COUNT();

UPDATE `zsjos_material_schema_version`
SET fields_json = JSON_REMOVE(fields_json, CONCAT(@business_base, '.recommendationDimension'))
WHERE id=@viral_schema_id
  AND JSON_EXTRACT(fields_json, CONCAT(@business_base, '.recommendationDimension')) IS NOT NULL;

-- 2. 同步模板哈希（仅当本次修正了字典字段；重复执行不覆盖）
UPDATE `zsjos_material_schema_version`
SET schema_hash='b1369fe4d75afb37b65e1cd8837505b14c0feedc9ffe6df4c0f46fd6d1044e59'
WHERE id=@viral_schema_id AND @persona_repaired=1;

-- 3. 爆款账号推荐维度收敛为模板实际承载的维度
UPDATE `zsjos_material_type`
SET recommendation_config_json = JSON_SET(recommendation_config_json, '$.dimensions',
        JSON_ARRAY('account_type','profession'))
WHERE code='viral_account' AND deleted=b'0'
  AND JSON_SEARCH(recommendation_config_json, 'one', 'account_stage', NULL, '$.dimensions[*]') IS NOT NULL;

-- 4. 逻辑删除已废弃的素材适配账号类型字典（数据与类型均逻辑删除）
UPDATE `system_dict_data` SET deleted=b'1'
WHERE dict_type='zsjos_material_account_type' AND deleted=b'0';
UPDATE `system_dict_type` SET deleted=b'1'
WHERE type='zsjos_material_account_type' AND deleted=b'0';

-- 5. 校验结果
SELECT 'viral_account v2 adapted fields' AS check_name,
       IF((SELECT COUNT(*) FROM `zsjos_material_schema_version` sv,
               JSON_TABLE(sv.fields_json, '$[*]' COLUMNS (
                   fkey VARCHAR(64) PATH '$.key', dictType VARCHAR(64) PATH '$.dictType',
                   recommendationDimension VARCHAR(64) PATH '$.recommendationDimension')) jt
           WHERE sv.id=@viral_schema_id AND jt.fkey='adapted_persona_types'
             AND jt.dictType='zsjos_persona_type' AND jt.recommendationDimension IS NULL)=1
          AND (SELECT COUNT(*) FROM `zsjos_material_schema_version` sv,
               JSON_TABLE(sv.fields_json, '$[*]' COLUMNS (
                   fkey VARCHAR(64) PATH '$.key',
                   recommendationDimension VARCHAR(64) PATH '$.recommendationDimension')) jt
           WHERE sv.id=@viral_schema_id AND jt.recommendationDimension IS NOT NULL)=0
          AND (SELECT schema_hash FROM `zsjos_material_schema_version` WHERE id=@viral_schema_id)
              ='b1369fe4d75afb37b65e1cd8837505b14c0feedc9ffe6df4c0f46fd6d1044e59'
          AND NOT EXISTS (SELECT 1 FROM `system_dict_type`
               WHERE type='zsjos_material_account_type' AND deleted=b'0')
          AND NOT EXISTS (SELECT 1 FROM `system_dict_data`
               WHERE dict_type='zsjos_material_account_type' AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM `system_dict_type`
               WHERE type='zsjos_persona_type' AND status=0 AND deleted=b'0'),
       'PASS','FAIL') AS result;
