package cn.iocoder.yudao.module.zsjos.service.material;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;

/** 默认爆款内容拆解模板。发布后的字段定义会由素材版本快照冻结。 */
final class ViralContentMaterialSchema {
    private ViralContentMaterialSchema() {}

    static List<MaterialFieldDefinition> fields() {
        List<MaterialFieldDefinition> fields = new ArrayList<>();
        fields.add(dict("account_platform", "账号平台", FIELD_DICT_SINGLE, "zsjos_account_platform", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(dict("viral_content_types", "爆款类型", FIELD_DICT_MULTI, "zsjos_viral_content_type", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(text("account_id", "账号ID", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(link("work_url", "作品链接", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(text("work_title", "作品标题", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(textarea("cover_features", "封面图特点", SECTION_ACCOUNT_DETAIL, "作品详情", false));
        fields.add(textarea("work_body", "作品正文", SECTION_ACCOUNT_DETAIL, "作品详情", true));
        fields.add(textarea("comment_section", "评论区情况", SECTION_ACCOUNT_DETAIL, "作品详情", false));

        String analysis = SECTION_DIRECTOR_ANALYSIS;
        fields.add(textarea("title_framework", "标题框架", analysis, "编导拆解", true));
        fields.add(textarea("body_framework", "正文框架", analysis, "编导拆解", true));
        fields.add(textarea("cover_page_settings", "封面页设置", analysis, "编导拆解", true));
        fields.add(textarea("target_audience", "目标人群", analysis, "编导拆解", true));
        fields.add(textarea("topic_trigger", "选题触发", analysis, "编导拆解", true));
        fields.add(textarea("body_structure", "正文结构", analysis, "编导拆解", true)
                .setPlaceholder("1-3s；3-15s"));
        fields.add(textarea("five_layer_analysis", "五层拆解", analysis, "编导拆解", true)
                .setPlaceholder("1.选题层：2.标题层：3.结构层：4.画面层：5.转化层："));
        fields.add(textarea("viral_reason", "爆点归因", analysis, "编导拆解", true));
        fields.add(repeat("reference_work_links", "参考作品链接", analysis, "编导拆解", 2, 0,
                link("url", "作品链接", null, null, false)));

        String suggestion = SECTION_BUILD_SUGGESTION;
        fields.add(dict("adapted_persona_types", "适配账号类型", FIELD_DICT_MULTI, "zsjos_persona_type", suggestion, null, true));
        fields.add(dict("adapted_business_positions", "适配业务定位", FIELD_DICT_MULTI, "zsjos_material_profession", suggestion, null, true));
        fields.add(dict("adapted_account_stages", "适配账号期段", FIELD_DICT_MULTI, "zsjos_media_account_stage", suggestion, null, true));
        fields.add(textarea("guide_or_questions", "正文引导语/采集问题集", suggestion, null, true));
        fields.add(textarea("topic_scope_advice", "选题范围建议", suggestion, null, true));
        fields.add(textarea("production_advice", "制作建议要点", suggestion, null, true));
        fields.add(dict("primary_problems", "辅助功能解决", FIELD_DICT_MULTI, "zsjos_media_account_primary_problem", suggestion, null, false));
        return fields;
    }

    private static MaterialFieldDefinition text(String key, String label, String section, String group, boolean required) { return base(key, label, FIELD_TEXT, section, group, required); }
    private static MaterialFieldDefinition textarea(String key, String label, String section, String group, boolean required) { return base(key, label, FIELD_TEXTAREA, section, group, required); }
    private static MaterialFieldDefinition link(String key, String label, String section, String group, boolean required) { return base(key, label, FIELD_HTTPS_LINK, section, group, required); }
    private static MaterialFieldDefinition dict(String key, String label, String type, String dictType, String section, String group, boolean required) { return base(key, label, type, section, group, required).setDictType(dictType); }
    private static MaterialFieldDefinition repeat(String key, String label, String section, String group, int initialCount, int minCount, MaterialFieldDefinition... children) { return base(key, label, FIELD_REPEAT_GROUP, section, group, false).setInitialCount(initialCount).setMinCount(minCount).setChildren(List.of(children)); }
    private static MaterialFieldDefinition base(String key, String label, String type, String section, String group, boolean required) { return new MaterialFieldDefinition().setKey(key).setLabel(label).setType(type).setSection(section).setGroup(group).setRequired(required).setSearchable(true); }
}
