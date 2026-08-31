-- 客资提交表单：来源平台字典
INSERT INTO `system_dict_type`
    (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT
    '来源平台', 'zsjos_lead_source_platform', 0, '客资提交时选择的来源平台', '1', NOW(), '1', NOW(), 0, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'zsjos_lead_source_platform' AND `deleted` = 0
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '抖音自然流', '抖音自然流', 'zsjos_lead_source_platform', 0, 'primary', '', '', '1', NOW(), '1', NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'zsjos_lead_source_platform' AND `value` = '抖音自然流' AND `deleted` = 0
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '小红书私信', '小红书私信', 'zsjos_lead_source_platform', 0, 'success', '', '', '1', NOW(), '1', NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'zsjos_lead_source_platform' AND `value` = '小红书私信' AND `deleted` = 0
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '信息流投放', '信息流投放', 'zsjos_lead_source_platform', 0, 'warning', '', '', '1', NOW(), '1', NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'zsjos_lead_source_platform' AND `value` = '信息流投放' AND `deleted` = 0
);

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '其他', '其他', 'zsjos_lead_source_platform', 0, 'default', '', '', '1', NOW(), '1', NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'zsjos_lead_source_platform' AND `value` = '其他' AND `deleted` = 0
);
