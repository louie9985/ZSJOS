package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_FORM_INCOMPATIBLE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_TITLE_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_VALUE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackDynamicFormServiceTest {

    @Mock private BpmDefinitionReadApi definitionReadApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private FileApi fileApi;
    @InjectMocks private FeedbackDynamicFormService service;

    @Test
    void parsesSupportedFieldsAndReportsExactIncompatibleField() {
        BpmFormMetadataRespDTO form = form(List.of(
                "{\"type\":\"input\",\"field\":\"title\",\"title\":\"问题标题\",\"validate\":[{\"required\":true}]}",
                "{\"type\":\"select\",\"field\":\"supportType\",\"title\":\"支持类型\",\"dictType\":\"zsjos_feedback_support_type\"}",
                "{\"type\":\"number\",\"field\":\"amount\",\"title\":\"数量\"}"));

        FeedbackDynamicFormService.ParsedForm parsed = service.parse(form);

        assertEquals(List.of("title"), parsed.requiredTextFieldKeys());
        assertEquals(List.of("数量(number)"), parsed.incompatibleFields());
        when(definitionReadApi.getForm(1L)).thenReturn(form);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.requireCompatibleForm(1L, FeedbackConstants.TYPE_SUPPORT, "title"));
        assertEquals(FEEDBACK_FORM_INCOMPATIBLE.getCode(), error.getCode());
    }

    @Test
    void nonSurveyTitleMustBeRequiredSingleLineText() {
        BpmFormMetadataRespDTO form = form(List.of(
                "{\"type\":\"textarea\",\"field\":\"title\",\"title\":\"标题\",\"validate\":[{\"required\":true}]}"));
        when(definitionReadApi.getForm(1L)).thenReturn(form);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.requireCompatibleForm(1L, FeedbackConstants.TYPE_BUG, "title"));

        assertEquals(FEEDBACK_TITLE_FIELD_INVALID.getCode(), error.getCode());
    }

    @Test
    void surveyAcceptsRequiredFivePointRatingAndRejectsFraction() {
        BpmFormMetadataRespDTO form = form(List.of(
                "{\"type\":\"rate\",\"field\":\"rating\",\"title\":\"总体满意度\",\"props\":{\"max\":5},\"validate\":[{\"required\":true}]}"));
        when(definitionReadApi.getForm(1L)).thenReturn(form);
        FeedbackDynamicFormService.ParsedForm parsed = service.requireCompatibleForm(
                1L, FeedbackConstants.TYPE_SURVEY, "rating");

        assertEquals(List.of("rating"), parsed.requiredRatingFieldKeys());
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.normalizeValues(parsed, Map.of("rating", 4.5), 11L));
        assertEquals(FEEDBACK_VALUE_INVALID.getCode(), error.getCode());
    }

    @Test
    void normalizesDictionaryAndOwnedAttachmentSnapshots() {
        BpmFormMetadataRespDTO form = form(List.of(
                "{\"type\":\"input\",\"field\":\"title\",\"title\":\"标题\",\"validate\":[{\"required\":true}]}",
                "{\"type\":\"select\",\"field\":\"supportType\",\"title\":\"支持类型\",\"dictType\":\"zsjos_feedback_support_type\",\"validate\":[{\"required\":true}]}",
                "{\"type\":\"upload\",\"field\":\"files\",\"title\":\"附件\"}"));
        FeedbackDynamicFormService.ParsedForm parsed = service.parse(form);
        DictDataRespDTO dict = new DictDataRespDTO();
        dict.setDictType("zsjos_feedback_support_type");
        dict.setValue("network_communication");
        dict.setLabel("网络与通信");
        dict.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_feedback_support_type")).thenReturn(List.of(dict));
        when(fileApi.getFileInfo(9L)).thenReturn(new FileInfoRespDTO(9L, 1L, "网络截图.png",
                "zsjos/feedback/11/network.png", "https://files/network.png", "image/png", 128L, "11"));

        FeedbackDynamicFormService.NormalizedValues result = service.normalizeValues(parsed, Map.of(
                "title", "无法访问内网",
                "supportType", "network_communication",
                "files", List.of(9L)), 11L);

        assertEquals(List.of(9L), result.attachmentIds());
        assertEquals("网络与通信", ((Map<?, ?>) result.values().get("supportType")).get("label"));
        List<?> files = (List<?>) result.values().get("files");
        assertEquals("网络截图.png", ((Map<?, ?>) files.getFirst()).get("name"));
        assertTrue(String.valueOf(((Map<?, ?>) files.getFirst()).get("url")).contains("network.png"));
    }

    private BpmFormMetadataRespDTO form(List<String> fields) {
        BpmFormMetadataRespDTO form = new BpmFormMetadataRespDTO();
        form.setId(1L);
        form.setName("测试表单");
        form.setStatus(0);
        form.setFields(fields);
        return form;
    }
}
