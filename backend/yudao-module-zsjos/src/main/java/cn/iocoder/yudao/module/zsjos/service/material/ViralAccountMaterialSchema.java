package cn.iocoder.yudao.module.zsjos.service.material;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;

/** 默认爆款账号拆解模板。发布后的字段定义会被版本快照冻结。 */
final class ViralAccountMaterialSchema {

    private ViralAccountMaterialSchema() {
    }

    static List<MaterialFieldDefinition> fields() {
        List<MaterialFieldDefinition> fields = new ArrayList<>();
        fields.add(text("account_name", "账号名称", SECTION_ACCOUNT_DETAIL, null, true));
        fields.add(dict("account_platform", "账号平台", FIELD_DICT_SINGLE, "zsjos_account_platform",
                SECTION_ACCOUNT_DETAIL, null, true));
        fields.add(text("account_authentication", "账号认证", SECTION_ACCOUNT_DETAIL, null, true));
        fields.add(text("homepage_id", "主页ID", SECTION_ACCOUNT_DETAIL, null, true));
        fields.add(link("homepage_url", "主页链接", SECTION_ACCOUNT_DETAIL, null, true));
        fields.add(text("avatar_settings", "头像设置", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(text("background_settings", "背景设置", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(text("profile_bio", "主页引导语", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(textarea("pinned_content", "主页置顶内容", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(link("hottest_work_url", "最火作品链接", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(textarea("comment_strategy", "评论区策略", SECTION_ACCOUNT_DETAIL, null, false));
        fields.add(textarea("monetization_methods", "账号变现方式", SECTION_ACCOUNT_DETAIL, null, false));

        String analysis = SECTION_DIRECTOR_ANALYSIS;
        String accountSetup = "账号设置";
        fields.add(textarea("background_strategy", "账号设置：背景策略", analysis, accountSetup, true));
        fields.add(textarea("avatar_strategy", "账号设置：头像策略", analysis, accountSetup, true));
        fields.add(textarea("account_name_strategy", "账号设置：账号名策略", analysis, accountSetup, true));
        fields.add(textarea("profile_bio_strategy", "账号设置：主页引导语策略", analysis, accountSetup, true));
        fields.add(textarea("pinned_strategy", "账号设置：置顶策略", analysis, accountSetup, true));
        fields.add(repeat("content_matrix", "内容矩阵", analysis, "内容矩阵", 1,
                text("matrix_content", "矩阵内容", null, null, true),
                text("proportion", "占比", null, null, true),
                text("purpose", "目的", null, null, true),
                textarea("content_analysis", "内容解析", null, null, true)));
        String rhythm = "内容节奏";
        fields.add(textarea("rhythm_start", "内容节奏：开始", analysis, rhythm, true));
        fields.add(textarea("rhythm_transition", "内容节奏：转型", analysis, rhythm, true));
        fields.add(textarea("rhythm_increase", "内容节奏：增加", analysis, rhythm, true));
        fields.add(textarea("rhythm_stable", "内容节奏：稳定", analysis, rhythm, true));
        fields.add(textarea("recommendation_reason", "推荐理由", analysis, null, true));
        fields.add(textarea("build_notify", "搭建注意", analysis, null, true));

        String suggestion = SECTION_BUILD_SUGGESTION;
        fields.add(dict("adapted_persona_types", "适配账号类型", FIELD_DICT_MULTI, "zsjos_persona_type",
                suggestion, null, true).setAllowUnlimited(true));
        fields.add(dict("adapted_business_positions", "适配业务定位", FIELD_DICT_MULTI,
                "zsjos_material_profession", suggestion, null, true).setAllowUnlimited(true));
        fields.add(textarea("ip_account_advice", "IP/账号适配建议", suggestion, null, true));
        fields.add(textarea("homepage_build_advice", "主页搭建建议", suggestion, null, true));
        fields.add(textarea("lead_conversion_path", "客资转化路径", suggestion, null, true));
        fields.add(textarea("s1_stage_plan", "S1定位期", suggestion, null, true)
                .setPlaceholder("作品数条数、内容方向、参考验收标准"));
        fields.add(textarea("s2_stage_plan", "S2冷启动期", suggestion, null, true)
                .setPlaceholder("作品数条数、内容方向、参考验收标准"));
        fields.add(textarea("s3_stage_plan", "S3内容验证期", suggestion, null, true)
                .setPlaceholder("矩阵内容节奏、作品数条数、内容方向、参考验收标准"));
        fields.add(textarea("s4_stage_plan", "S4咨询验证期", suggestion, null, true));
        fields.add(textarea("s5_stage_plan", "S5客资验证期", suggestion, null, true)
                .setPlaceholder("客资提取方式、客资"));
        fields.add(repeat("s6_stage_plan", "S6稳定增长期", suggestion, null, 1,
                textarea("matrix_content", "矩阵内容", null, null, true),
                text("proportion", "占比", null, null, true),
                text("purpose", "目的", null, null, true),
                textarea("acceptance_standard", "参考验收标准", null, null, true)));
        fields.add(textarea("overall_lead_extraction_method", "客资提取方式", suggestion, null, true));
        applySortOrder(fields);
        return fields;
    }

    private static void applySortOrder(List<MaterialFieldDefinition> fields) {
        for (int index = 0; index < fields.size(); index++) {
            MaterialFieldDefinition field = fields.get(index);
            field.setSort((index + 1) * 10);
            if (field.getChildren() != null) {
                for (int childIndex = 0; childIndex < field.getChildren().size(); childIndex++) {
                    field.getChildren().get(childIndex).setSort((childIndex + 1) * 10);
                }
            }
        }
    }

    private static MaterialFieldDefinition text(String key, String label, String section, String group, boolean required) {
        return base(key, label, FIELD_TEXT, section, group, required);
    }

    private static MaterialFieldDefinition textarea(String key, String label, String section, String group, boolean required) {
        return base(key, label, FIELD_TEXTAREA, section, group, required);
    }

    private static MaterialFieldDefinition link(String key, String label, String section, String group, boolean required) {
        return base(key, label, FIELD_HTTPS_LINK, section, group, required);
    }

    private static MaterialFieldDefinition dict(String key, String label, String type, String dictType,
                                                 String section, String group, boolean required) {
        return base(key, label, type, section, group, required).setDictType(dictType);
    }

    private static MaterialFieldDefinition repeat(String key, String label, String section, String group,
                                                  int minCount, MaterialFieldDefinition... children) {
        return base(key, label, FIELD_REPEAT_GROUP, section, group, true)
                .setMinCount(minCount).setChildren(List.of(children));
    }

    private static MaterialFieldDefinition base(String key, String label, String type, String section,
                                                String group, boolean required) {
        return new MaterialFieldDefinition().setKey(key).setLabel(label).setType(type)
                .setSection(section).setGroup(group).setRequired(required).setSearchable(true);
    }
}
