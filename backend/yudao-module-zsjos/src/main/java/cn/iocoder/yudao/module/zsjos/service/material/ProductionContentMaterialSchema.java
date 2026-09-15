package cn.iocoder.yudao.module.zsjos.service.material;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;

/**
 * 默认生产内容收录模板。生产内容只在批审终审收录时由服务端写入，不支持手工创建和导入，
 * 因此全部字段均为非必填：某条内容缺少可映射来源时只影响该字段，不阻塞批审提交与完成。
 */
final class ProductionContentMaterialSchema {

    private ProductionContentMaterialSchema() {
    }

    static List<MaterialFieldDefinition> fields() {
        List<MaterialFieldDefinition> fields = new ArrayList<>();
        fields.add(text("content_title", "内容标题", SECTION_ACCOUNT_DETAIL, null));
        fields.add(textarea("content_topic", "内容选题", SECTION_ACCOUNT_DETAIL, null));
        fields.add(textarea("script_text", "脚本正文", SECTION_ACCOUNT_DETAIL, null));
        fields.add(link("deliverable_url", "成品链接", SECTION_ACCOUNT_DETAIL, null));
        fields.add(link("lead_resource_url", "客资承接链接", SECTION_ACCOUNT_DETAIL, null));
        fields.add(datetime("planned_publish_at", "计划发布时间", SECTION_ACCOUNT_DETAIL, null));
        fields.add(video("deliverable_files", "成品文件", SECTION_ACCOUNT_DETAIL, null));

        // 三个推荐维度字段来自账号快照，使收录后的生产内容可以参与素材推荐。
        String suggestion = SECTION_BUILD_SUGGESTION;
        fields.add(dict("account_type", "适配账号类型", DICT_PERSONA_TYPE, suggestion, null));
        fields.add(dict("profession", "适配业务定位", DICT_PROFESSION, suggestion, null));
        fields.add(dict("account_stage", "适配账号阶段", DICT_ACCOUNT_STAGE, suggestion, null));
        applySortOrder(fields);
        return fields;
    }

    private static void applySortOrder(List<MaterialFieldDefinition> fields) {
        for (int index = 0; index < fields.size(); index++) {
            fields.get(index).setSort((index + 1) * 10);
        }
    }

    private static MaterialFieldDefinition text(String key, String label, String section, String group) {
        return base(key, label, FIELD_TEXT, section, group);
    }

    private static MaterialFieldDefinition textarea(String key, String label, String section, String group) {
        return base(key, label, FIELD_TEXTAREA, section, group);
    }

    private static MaterialFieldDefinition link(String key, String label, String section, String group) {
        return base(key, label, FIELD_HTTPS_LINK, section, group);
    }

    private static MaterialFieldDefinition datetime(String key, String label, String section, String group) {
        return base(key, label, FIELD_DATETIME, section, group);
    }

    private static MaterialFieldDefinition video(String key, String label, String section, String group) {
        return base(key, label, FIELD_VIDEO, section, group);
    }

    private static MaterialFieldDefinition dict(String key, String label, String dictType,
                                                String section, String group) {
        return base(key, label, FIELD_DICT_SINGLE, section, group).setDictType(dictType);
    }

    private static MaterialFieldDefinition base(String key, String label, String type, String section,
                                                String group) {
        return new MaterialFieldDefinition().setKey(key).setLabel(label).setType(type)
                .setSection(section).setGroup(group).setRequired(false).setSearchable(true);
    }
}
