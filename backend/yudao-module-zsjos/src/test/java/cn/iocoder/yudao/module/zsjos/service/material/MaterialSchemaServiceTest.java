package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_ACCOUNT_STAGE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PERSONA_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_DICT_MULTI;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_HTTPS_LINK;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_REPEAT_GROUP;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_RICH_TEXT;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.FIELD_TEXT;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.SECTION_ACCOUNT_DETAIL;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.SECTION_BUILD_SUGGESTION;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.SECTION_DIRECTOR_ANALYSIS;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.VALUE_UNLIMITED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_SCHEMA_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialSchemaServiceTest {

    @InjectMocks private MaterialSchemaService service;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private FileApi fileApi;
    @Mock private XssCleaner xssCleaner;

    @Test
    void normalizeStoresDictionaryLabelsAndRecommendationDimensionsAsSnapshots() {
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_STAGE)).thenReturn(List.of(
                dictionary("S1", "起步期"), dictionary("S2", "增长期")));
        List<MaterialFieldDefinition> fields = List.of(field("account_stage", "适配账号时期",
                FIELD_DICT_MULTI).setDictType(DICT_ACCOUNT_STAGE)
                .setAllowUnlimited(true).setRequired(true));

        MaterialSchemaService.NormalizedMaterial result = service.normalize(fields,
                Map.of("account_stage", List.of("S1", "S2")), 7L);

        assertEquals(List.of("S1", "S2"), result.values().get("account_stage"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshots = (List<Map<String, Object>>) result.snapshots().get("account_stage");
        assertEquals("起步期", snapshots.getFirst().get("label"));
        assertEquals("增长期", snapshots.getLast().get("label"));
        assertEquals(List.of("S1", "S2"), result.dimensions().stream()
                .map(MaterialSchemaService.DimensionValue::value).toList());
        assertTrue(result.searchText().contains("起步期"));
    }

    @Test
    void normalizeUsesTrustedReviewSnapshotAfterDictionaryOptionIsDisabledOrRenamed() {
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_STAGE)).thenReturn(List.of());
        List<MaterialFieldDefinition> fields = List.of(field("account_stage", "适配账号时期",
                FIELD_DICT_MULTI).setDictType(DICT_ACCOUNT_STAGE)
                .setRequired(true));

        MaterialSchemaService.NormalizedMaterial result = service.normalize(fields,
                Map.of("account_stage", "S1"), 7L, Set.of(), Map.of("account_stage",
                        new MaterialSchemaService.DictionarySnapshotValue(DICT_ACCOUNT_STAGE, "S1", "提交时标签")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshots = (List<Map<String, Object>>) result.snapshots().get("account_stage");
        assertEquals("提交时标签", snapshots.getFirst().get("label"));
        assertEquals("提交时标签", result.dimensions().getFirst().label());
        assertTrue(result.searchText().contains("提交时标签"));
    }

    @Test
    void normalizeRejectsTrustedSnapshotForAnotherDictionaryOrCode() {
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_STAGE)).thenReturn(List.of());
        List<MaterialFieldDefinition> fields = List.of(field("account_stage", "适配账号时期",
                FIELD_DICT_MULTI).setDictType(DICT_ACCOUNT_STAGE));

        assertFieldInvalid(() -> service.normalize(fields, Map.of("account_stage", "S1"), 7L, Set.of(),
                Map.of("account_stage", new MaterialSchemaService.DictionarySnapshotValue(
                        "another_dict", "S1", "错误标签"))));
        assertFieldInvalid(() -> service.normalize(fields, Map.of("account_stage", "S1"), 7L, Set.of(),
                Map.of("account_stage", new MaterialSchemaService.DictionarySnapshotValue(
                        DICT_ACCOUNT_STAGE, "S2", "错误标签"))));
    }

    @Test
    void normalizeAllowsExplicitUnlimitedButRejectsCombiningItWithSpecificValues() {
        when(dictDataApi.getDictDataList(DICT_ACCOUNT_STAGE)).thenReturn(List.of(dictionary("S1", "起步期")));
        List<MaterialFieldDefinition> fields = List.of(field("account_stage", "适配账号时期",
                FIELD_DICT_MULTI).setDictType(DICT_ACCOUNT_STAGE)
                .setAllowUnlimited(true));

        MaterialSchemaService.NormalizedMaterial unlimited = service.normalize(fields,
                Map.of("account_stage", List.of(VALUE_UNLIMITED)), 7L);
        assertTrue(unlimited.dimensions().getFirst().unlimited());
        assertEquals("不限", unlimited.dimensions().getFirst().label());

        assertFieldInvalid(() -> service.normalize(fields,
                Map.of("account_stage", List.of(VALUE_UNLIMITED, "S1")), 7L));
    }

    @Test
    void schemaRejectsDuplicateRecommendationDimensionFromSameDictionary() {
        when(dictDataApi.getDictDataList(DICT_PERSONA_TYPE)).thenReturn(List.of(dictionary("p1", "人设一")));
        List<MaterialFieldDefinition> fields = List.of(
                field("persona_primary", "适配账号类型", FIELD_DICT_MULTI).setDictType(DICT_PERSONA_TYPE),
                field("persona_secondary", "备用账号类型", FIELD_DICT_MULTI).setDictType(DICT_PERSONA_TYPE));

        assertSchemaInvalid(() -> service.validateSchemaForPublish(fields));
    }

    @Test
    void schemaRejectsUnlimitedOnNonRecommendationDictionary() {
        when(dictDataApi.getDictDataList("other_dict")).thenReturn(List.of(dictionary("X", "选项")));
        List<MaterialFieldDefinition> fields = List.of(field("other", "其他字典", FIELD_DICT_MULTI)
                .setDictType("other_dict").setAllowUnlimited(true));

        assertSchemaInvalid(() -> service.validateSchemaForPublish(fields));
    }

    @Test
    void schemaRejectsRecommendationDictionaryInsideRepeatGroup() {
        when(dictDataApi.getDictDataList(DICT_PERSONA_TYPE)).thenReturn(List.of(dictionary("p1", "人设一")));
        MaterialFieldDefinition group = field("cases", "案例", FIELD_REPEAT_GROUP)
                .setChildren(List.of(field("persona", "适配账号类型", FIELD_DICT_MULTI)
                        .setDictType(DICT_PERSONA_TYPE)));

        assertSchemaInvalid(() -> service.validateSchemaForPublish(List.of(group)));
    }

    @Test
    void normalizeSanitizesRichTextAndIndexesPlainText() {
        when(xssCleaner.clean(anyString())).thenReturn("<p>样板文稿</p>");
        List<MaterialFieldDefinition> fields = List.of(field("sample", "样板文稿", FIELD_RICH_TEXT)
                .setRequired(true).setSearchable(true));

        MaterialSchemaService.NormalizedMaterial result = service.normalize(fields,
                Map.of("sample", "<script>alert(1)</script><p>样板文稿</p>"), 7L);

        assertEquals("<p>样板文稿</p>", result.values().get("sample"));
        assertEquals("样板文稿", result.indexes().getFirst().textValue());
        assertFalse(result.searchText().contains("alert"));
    }

    @Test
    void normalizeSupportsOneLevelRepeatGroupAndRejectsInvalidHttpsLinks() {
        MaterialFieldDefinition caseGroup = field("cases", "案例", FIELD_REPEAT_GROUP)
                .setMinCount(1).setMaxCount(2).setChildren(List.of(
                        field("caption", "案例说明", FIELD_TEXT).setRequired(true).setSearchable(true),
                        field("url", "案例链接", FIELD_HTTPS_LINK).setRequired(true)));

        MaterialSchemaService.NormalizedMaterial result = service.normalize(List.of(caseGroup), Map.of("cases", List.of(
                Map.of("caption", "案例一", "url", "https://example.com/1"),
                Map.of("caption", "案例二", "url", "https://example.com/2"))), 7L);

        assertEquals(2, ((List<?>) result.values().get("cases")).size());
        assertTrue(result.indexes().stream().anyMatch(index -> index.groupIndex() == 1
                && "cases.caption".equals(index.fieldKey()) && "案例二".equals(index.textValue())));
        assertFieldInvalid(() -> service.normalize(List.of(caseGroup),
                Map.of("cases", List.of(Map.of("caption", "案例", "url", "http://example.com"))), 7L));
    }

    @Test
    void viralAccountTemplateContainsThreeColumnsAndRepeatGroups() {
        List<MaterialFieldDefinition> fields = ViralAccountMaterialSchema.fields();
        assertEquals(36, fields.size());
        assertEquals(3, fields.stream().map(MaterialFieldDefinition::getSection).distinct().count());
        assertEquals(12, fields.stream().filter(field -> SECTION_ACCOUNT_DETAIL.equals(field.getSection())).count());
        assertEquals(12, fields.stream().filter(field -> SECTION_DIRECTOR_ANALYSIS.equals(field.getSection())).count());
        assertEquals(12, fields.stream().filter(field -> SECTION_BUILD_SUGGESTION.equals(field.getSection())).count());
        assertEquals("build_notify", fields.get(23).getKey());
        assertEquals(SECTION_DIRECTOR_ANALYSIS, fields.get(23).getSection());
        assertNull(fields.get(23).getGroup());
        MaterialFieldDefinition contentMatrix = fields.stream().filter(field -> "content_matrix".equals(field.getKey()))
                .findFirst().orElseThrow();
        MaterialFieldDefinition stageSix = fields.stream().filter(field -> "s6_stage_plan".equals(field.getKey()))
                .findFirst().orElseThrow();
        assertEquals(FIELD_REPEAT_GROUP, contentMatrix.getType());
        assertEquals(1, contentMatrix.getMinCount());
        assertEquals(4, contentMatrix.getChildren().size());
        assertEquals(FIELD_REPEAT_GROUP, stageSix.getType());
        assertEquals(1, stageSix.getMinCount());
        assertEquals(List.of("s1_stage_plan", "s2_stage_plan", "s3_stage_plan", "s4_stage_plan",
                        "s5_stage_plan", "s6_stage_plan"),
                fields.subList(29, 35).stream().map(MaterialFieldDefinition::getKey).toList());
        assertTrue(fields.stream().allMatch(field -> field.getSort() != null));
        assertEquals("zsjos_persona_type", fields.stream()
                .filter(field -> "adapted_persona_types".equals(field.getKey()))
                .findFirst().orElseThrow().getDictType());
    }

    @Test
    void viralContentTemplateContainsOnlyConfirmedFieldsAndTwoInitialLinks() {
        List<MaterialFieldDefinition> fields = ViralContentMaterialSchema.fields();

        assertEquals(24, fields.size());
        assertEquals(3, fields.stream().map(MaterialFieldDefinition::getSection).distinct().count());
        MaterialFieldDefinition links = fields.stream().filter(field -> "reference_work_links".equals(field.getKey()))
                .findFirst().orElseThrow();
        assertEquals(FIELD_REPEAT_GROUP, links.getType());
        assertEquals(2, links.getInitialCount());
        assertEquals(0, links.getMinCount());
        assertEquals(FIELD_HTTPS_LINK, links.getChildren().getFirst().getType());
        assertEquals("zsjos_viral_content_type", fields.stream()
                .filter(field -> "viral_content_types".equals(field.getKey())).findFirst().orElseThrow().getDictType());
        assertEquals("1-3s；3-15s", fields.stream()
                .filter(field -> "body_structure".equals(field.getKey())).findFirst().orElseThrow().getPlaceholder());
    }

    @Test
    void draftNormalizationAllowsMissingRequiredFieldsButStrictNormalizationRejectsThem() {
        List<MaterialFieldDefinition> fields = List.of(field("account_name", "账号名称", FIELD_TEXT).setRequired(true));

        assertTrue(service.normalizeDraft(fields, Map.of(), 7L).values().isEmpty());
        assertFieldInvalid(() -> service.normalize(fields, Map.of(), 7L));
    }

    private MaterialFieldDefinition field(String key, String label, String type) {
        return new MaterialFieldDefinition().setKey(key).setLabel(label).setType(type)
                .setRequired(false).setSearchable(false).setMultiple(false).setAllowUnlimited(false);
    }

    private DictDataRespDTO dictionary(String value, String label) {
        return new DictDataRespDTO().setValue(value).setLabel(label).setDictType(DICT_ACCOUNT_STAGE)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private void assertFieldInvalid(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(MATERIAL_FIELD_INVALID.getCode(), error.getCode());
    }

    private void assertSchemaInvalid(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(MATERIAL_SCHEMA_INVALID.getCode(), error.getCode());
    }
}
