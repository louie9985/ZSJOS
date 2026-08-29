package cn.iocoder.yudao.module.zsjos.service.forcedform;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForcedFormFieldValidatorTest {

    @InjectMocks
    private ForcedFormFieldValidator validator;
    @Mock
    private DictDataApi dictDataApi;

    @Test
    void parseAndValidateRejectsInvalidKeyAndDuplicateKey() {
        assertServiceException(() -> validator.parseAndValidate(json(
                field("Name", "text", "姓名", true))),
                ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);

        assertServiceException(() -> validator.parseAndValidate(json(
                field("name", "text", "姓名", true),
                field("name", "textarea", "备注", false))),
                ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
    }

    @Test
    void parseAndValidateRejectsStaticOptionsAndUnknownType() {
        ForcedFormFieldDefinition withOptions = field("choice", "radio", "选择", true);
        withOptions.setOptions(List.of(Map.of("value", "a", "label", "A")));
        assertServiceException(() -> validator.parseAndValidate(json(withOptions)),
                ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);

        assertServiceException(() -> validator.parseAndValidate(json(
                field("age", "number", "年龄", false))),
                ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
    }

    @Test
    void parseAndValidateRequiresValidDictionaryForChoiceFields() {
        ForcedFormFieldDefinition radio = field("gender", "radio", "性别", true);
        radio.setDictType("zsjos_gender");
        when(dictDataApi.getDictDataList("zsjos_gender")).thenReturn(List.of());

        assertServiceException(() -> validator.parseAndValidate(json(radio)),
                ZsjosErrorCodeConstants.FORCED_FORM_DICT_INVALID);
    }

    @Test
    void normalizeAnswersRejectsExtraFieldWrongTypeAndTextTooLong() {
        ForcedFormFieldDefinition name = field("name", "text", "姓名", true);
        name.setMaxLength(3);
        List<ForcedFormFieldDefinition> fields = List.of(name);

        assertServiceException(() -> validator.normalizeAnswers(fields, Map.of("name", "张三", "extra", "x")),
                ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
        assertServiceException(() -> validator.normalizeAnswers(fields, Map.of("name", 12)),
                ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
        assertServiceException(() -> validator.normalizeAnswers(fields, Map.of("name", "张三丰丰")),
                ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
    }

    @Test
    void normalizeAnswersRequiresTextAndCheckboxTrueWhenRequired() {
        ForcedFormFieldDefinition name = field("name", "text", "姓名", true);
        ForcedFormFieldDefinition agreement = field("agreement", "checkbox", "确认", true);

        assertServiceException(() -> validator.normalizeAnswers(List.of(name), Map.of()),
                ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
        assertServiceException(() -> validator.normalizeAnswers(List.of(agreement), Map.of("agreement", false)),
                ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);

        Map<String, Object> normalized = validator.normalizeAnswers(List.of(agreement), Map.of("agreement", true));
        assertEquals(true, normalized.get("agreement"));
    }

    @Test
    void normalizeAnswersValidatesDictionaryValuesAndMapsFailureToForcedFormCode() {
        ForcedFormFieldDefinition radio = field("source", "radio", "来源", true);
        radio.setDictType("zsjos_source");
        doThrow(new IllegalStateException("dict value disabled"))
                .when(dictDataApi).validateDictDataList("zsjos_source", List.of("offline"));

        assertServiceException(() -> validator.normalizeAnswers(List.of(radio), Map.of("source", "offline")),
                ZsjosErrorCodeConstants.FORCED_FORM_DICT_INVALID);

        verify(dictDataApi).validateDictDataList("zsjos_source", List.of("offline"));
    }

    @Test
    void normalizeAnswersAcceptsMultiSelectAndBuildsDictionarySnapshotLabels() {
        ForcedFormFieldDefinition tags = field("tags", "multi-select", "标签", false);
        tags.setDictType("zsjos_tags");
        when(dictDataApi.getDictDataList("zsjos_tags")).thenReturn(List.of(
                dict("a", "标签A"),
                dict("b", "标签B")));

        Map<String, Object> normalized = validator.normalizeAnswers(List.of(tags), Map.of("tags", List.of("a", "b")));
        assertIterableEquals(List.of("a", "b"), (List<?>) normalized.get("tags"));

        Map<String, Object> snapshot = validator.buildDictSnapshot(List.of(tags), normalized);
        @SuppressWarnings("unchecked")
        Map<String, Object> tagsSnapshot = (Map<String, Object>) snapshot.get("tags");
        assertEquals("zsjos_tags", tagsSnapshot.get("dictType"));
        assertIterableEquals(List.of("标签A", "标签B"), (List<?>) tagsSnapshot.get("labels"));
    }

    @Test
    void normalizeAnswersRejectsRequiredAttachmentOverflow() {
        ForcedFormFieldDefinition attachment = field("files", "attachment", "附件", true);
        attachment.setMaxCount(1);

        assertServiceException(() -> validator.normalizeAnswers(List.of(attachment), Map.of()),
                ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
        assertServiceException(() -> validator.normalizeAnswers(List.of(attachment), Map.of("files", List.of("a", "b"))),
                ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
    }

    private static ForcedFormFieldDefinition field(String key, String type, String label, boolean required) {
        ForcedFormFieldDefinition field = new ForcedFormFieldDefinition();
        field.setKey(key);
        field.setType(type);
        field.setLabel(label);
        field.setRequired(required);
        return field;
    }

    private static DictDataRespDTO dict(String value, String label) {
        DictDataRespDTO item = new DictDataRespDTO();
        item.setValue(value);
        item.setLabel(label);
        item.setStatus(0);
        return item;
    }

    private static String json(ForcedFormFieldDefinition... fields) {
        return JsonUtils.toJsonString(List.of(fields));
    }
}
