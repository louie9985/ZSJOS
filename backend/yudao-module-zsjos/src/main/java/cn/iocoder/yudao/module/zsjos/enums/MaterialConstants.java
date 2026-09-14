package cn.iocoder.yudao.module.zsjos.enums;

import java.util.Map;
import java.util.Set;

public interface MaterialConstants {

    String SCHEMA_DRAFT = "DRAFT";
    String SCHEMA_PUBLISHED = "PUBLISHED";
    String SCHEMA_ARCHIVED = "ARCHIVED";

    String MATERIAL_DRAFT = "DRAFT";
    String MATERIAL_IN_APPROVAL = "IN_APPROVAL";
    String MATERIAL_EFFECTIVE = "EFFECTIVE";
    String MATERIAL_REJECTED = "REJECTED";
    String MATERIAL_DISABLED = "DISABLED";

    String VERSION_DRAFT = "DRAFT";
    String VERSION_IN_APPROVAL = "IN_APPROVAL";
    String VERSION_EFFECTIVE = "EFFECTIVE";
    String VERSION_REJECTED = "REJECTED";

    String SOURCE_MANUAL = "MANUAL";
    String SOURCE_IMPORT = "IMPORT";
    String SOURCE_CONTENT_REVIEW = "CONTENT_REVIEW";

    String IMPORT_PREVIEWED = "PREVIEWED";
    String IMPORT_COMMITTED = "COMMITTED";

    String DIMENSION_ACCOUNT_TYPE = "account_type";
    String DIMENSION_PROFESSION = "profession";
    String DIMENSION_ACCOUNT_STAGE = "account_stage";
    Set<String> RECOMMENDATION_DIMENSIONS = Set.of(
            DIMENSION_ACCOUNT_TYPE, DIMENSION_PROFESSION, DIMENSION_ACCOUNT_STAGE);

    String SECTION_ACCOUNT_DETAIL = "ACCOUNT_DETAIL";
    String SECTION_DIRECTOR_ANALYSIS = "DIRECTOR_ANALYSIS";
    String SECTION_BUILD_SUGGESTION = "BUILD_SUGGESTION";
    Set<String> FIELD_SECTIONS = Set.of(SECTION_ACCOUNT_DETAIL, SECTION_DIRECTOR_ANALYSIS,
            SECTION_BUILD_SUGGESTION);

    String VALUE_UNLIMITED = "__ALL__";
    String LABEL_UNLIMITED = "不限";
    /** 账号类型维度使用的字典：人设类型。字段字典即维度，不再人工选择推荐维度。 */
    String DICT_PERSONA_TYPE = "zsjos_persona_type";
    String DICT_PROFESSION = "zsjos_material_profession";
    String DICT_ACCOUNT_STAGE = "zsjos_media_account_stage";
    /** 字典到推荐维度的自动归属关系；只有这三个字典的字段参与推荐。 */
    Map<String, String> RECOMMENDATION_DICT_DIMENSIONS = Map.of(
            DICT_PERSONA_TYPE, DIMENSION_ACCOUNT_TYPE,
            DICT_PROFESSION, DIMENSION_PROFESSION,
            DICT_ACCOUNT_STAGE, DIMENSION_ACCOUNT_STAGE);

    /** 返回字段字典自动归属的推荐维度；非推荐维度字典返回 null。 */
    static String recommendationDimensionOf(String dictType) {
        return dictType == null ? null : RECOMMENDATION_DICT_DIMENSIONS.get(dictType);
    }

    String BUSINESS_KEY_PREFIX = "material-version:";
    String MATERIAL_BPM_CATEGORY = "zsjos_material";
    String CONTENT_REVIEW_BUSINESS_KEY_PREFIX = "content-review-batch:";

    String FIELD_TEXT = "text";
    String FIELD_TEXTAREA = "textarea";
    String FIELD_RICH_TEXT = "rich-text";
    String FIELD_NUMBER = "number";
    String FIELD_DATE = "date";
    String FIELD_DATETIME = "datetime";
    String FIELD_DICT_SINGLE = "dict-single";
    String FIELD_DICT_MULTI = "dict-multi";
    String FIELD_EMPLOYEE = "employee";
    String FIELD_DEPARTMENT = "department";
    String FIELD_IMAGE = "image";
    String FIELD_VIDEO = "video";
    String FIELD_ATTACHMENT = "attachment";
    String FIELD_HTTPS_LINK = "https-link";
    String FIELD_REPEAT_GROUP = "repeat-group";

    Set<String> FIELD_TYPES = Set.of(FIELD_TEXT, FIELD_TEXTAREA, FIELD_RICH_TEXT, FIELD_NUMBER,
            FIELD_DATE, FIELD_DATETIME, FIELD_DICT_SINGLE, FIELD_DICT_MULTI, FIELD_EMPLOYEE,
            FIELD_DEPARTMENT, FIELD_IMAGE, FIELD_VIDEO, FIELD_ATTACHMENT, FIELD_HTTPS_LINK,
            FIELD_REPEAT_GROUP);
    Set<String> FILE_FIELD_TYPES = Set.of(FIELD_IMAGE, FIELD_VIDEO, FIELD_ATTACHMENT);
    Set<String> MULTI_VALUE_FIELD_TYPES = Set.of(FIELD_DICT_MULTI, FIELD_IMAGE, FIELD_VIDEO, FIELD_ATTACHMENT);
}
