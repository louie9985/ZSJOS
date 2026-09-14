package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmUserTaskMetadataRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialSchemaService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialTypeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.BPM_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_CONFIG_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReviewConfigServiceTest {

    private static final Long USER_ID = 7L;
    private static final String TYPE_CODE = "production_content";

    @InjectMocks private ContentReviewConfigService service;
    @Mock private ContentReviewConfigMapper configMapper;
    @Mock private BpmDefinitionReadApi definitionReadApi;
    @Mock private MaterialTypeMapper materialTypeMapper;
    @Mock private MaterialTypeService materialTypeService;
    @Mock private MaterialSchemaService materialSchemaService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void updateConfigAcceptsCompatibleSourcesAndValidatedDefaults() {
        List<MaterialFieldDefinition> fields = List.of(
                field("body", FIELD_RICH_TEXT, true, null),
                field("account_types", FIELD_DICT_MULTI, true, DICT_PERSONA_TYPE),
                field("video", FIELD_VIDEO, true, null),
                field("publish_date", FIELD_DATE, false, null));
        stubUpdate(fields);
        when(configMapper.updateConfig(any(ContentReviewConfigDO.class), anyInt())).thenReturn(1);
        Map<String, String> mapping = Map.of(
                "body", "scriptText",
                "account_types", "accountType",
                "video", "deliverableFileIds",
                "__cover__", "coverFileId");
        Map<String, Object> defaults = Map.of("publish_date", "2026-09-08");

        service.updateConfig(request(mapping, defaults), USER_ID);

        verify(materialSchemaService).validatePartialValues(fields, defaults, USER_ID);
        verify(configMapper).updateConfig(any(ContentReviewConfigDO.class), anyInt());
    }

    @Test
    void updateConfigRejectsDictionarySourceWithDifferentDictionaryType() {
        stubUpdate(List.of(field("profession", FIELD_DICT_SINGLE, true, DICT_PROFESSION)));

        assertServiceException(() -> service.updateConfig(
                        request(Map.of("profession", "accountType"), Map.of()), USER_ID),
                CONTENT_REVIEW_CONFIG_INVALID);

        verify(configMapper, never()).updateConfig(any(), anyInt());
    }

    @Test
    void updateConfigRejectsMappingAndDefaultForSameField() {
        stubUpdate(List.of(field("body", FIELD_TEXTAREA, true, null)));

        assertServiceException(() -> service.updateConfig(
                        request(Map.of("body", "scriptText"), Map.of("body", "默认正文")), USER_ID),
                CONTENT_REVIEW_CONFIG_INVALID);

        verify(configMapper, never()).updateConfig(any(), anyInt());
    }

    @Test
    void updateConfigRejectsFileDefault() {
        stubUpdate(List.of(field("video", FIELD_VIDEO, true, null)));

        assertServiceException(() -> service.updateConfig(
                        request(Map.of(), Map.of("video", List.of(100L))), USER_ID),
                CONTENT_REVIEW_CONFIG_INVALID);

        verify(materialSchemaService, never()).validatePartialValues(any(), any(), anyLong());
    }

    @Test
    void requireReadyConfigRevalidatesMappingAgainstCurrentPublishedSchema() {
        ContentReviewConfigDO config = config();
        config.setMaterialFieldMappingJson("{\"publish_date\":\"plannedPublishAt\"}");
        config.setMaterialDefaultValuesJson("{}");
        when(configMapper.selectCurrent()).thenReturn(config);
        when(definitionReadApi.getPublishedProcessDefinition("content-review")).thenReturn(definition());
        stubSchema(List.of(field("publish_date", FIELD_DATE, true, null)));

        assertServiceException(() -> service.requireReadyConfig(USER_ID), CONTENT_REVIEW_CONFIG_INVALID);
    }

    private void stubUpdate(List<MaterialFieldDefinition> fields) {
        when(configMapper.selectCurrentForUpdate(1L)).thenReturn(config());
        when(definitionReadApi.getPublishedProcessDefinition("content-review")).thenReturn(definition());
        stubSchema(fields);
    }

    private void stubSchema(List<MaterialFieldDefinition> fields) {
        MaterialTypeDO type = new MaterialTypeDO();
        type.setId(20L);
        type.setCode(TYPE_CODE);
        type.setStatus(0);
        type.setAllowAutoCollect(true);
        MaterialSchemaVersionDO schema = new MaterialSchemaVersionDO();
        schema.setId(30L);
        schema.setFieldsJson("[]");
        when(materialTypeMapper.selectByCode(TYPE_CODE)).thenReturn(type);
        when(materialTypeService.requirePublishedSchema(type)).thenReturn(schema);
        when(materialSchemaService.parseFields("[]")).thenReturn(fields);
    }

    private ContentReviewConfigSaveReqVO request(Map<String, String> mapping, Map<String, Object> defaults) {
        ContentReviewConfigSaveReqVO request = new ContentReviewConfigSaveReqVO();
        request.setProcessDefinitionKey("content-review");
        request.setDirectorTaskKey("director");
        request.setFinalTaskKey("final");
        request.setProductionMaterialTypeCode(TYPE_CODE);
        request.setMaterialFieldMapping(mapping);
        request.setMaterialDefaultValues(defaults);
        request.setVersion(0);
        return request;
    }

    private ContentReviewConfigDO config() {
        ContentReviewConfigDO config = new ContentReviewConfigDO();
        config.setId(10L);
        config.setProcessDefinitionKey("content-review");
        config.setDirectorTaskKey("director");
        config.setFinalTaskKey("final");
        config.setProductionMaterialTypeCode(TYPE_CODE);
        config.setMaterialFieldMappingJson("{}");
        config.setMaterialDefaultValuesJson("{}");
        config.setVersion(0);
        return config;
    }

    private BpmProcessDefinitionMetadataRespDTO definition() {
        BpmUserTaskMetadataRespDTO director = new BpmUserTaskMetadataRespDTO();
        director.setKey("director");
        director.setExecutionMode("SINGLE");
        director.setNextUserTaskKeys(List.of("final"));
        BpmUserTaskMetadataRespDTO finalTask = new BpmUserTaskMetadataRespDTO();
        finalTask.setKey("final");
        finalTask.setExecutionMode("SINGLE");
        finalTask.setNextUserTaskKeys(List.of());
        BpmProcessDefinitionMetadataRespDTO definition = new BpmProcessDefinitionMetadataRespDTO();
        definition.setKey("content-review");
        definition.setCategory(BPM_CATEGORY);
        definition.setSuspended(false);
        definition.setSimpleSequentialApproval(true);
        definition.setUserTasks(List.of(director, finalTask));
        return definition;
    }

    private MaterialFieldDefinition field(String key, String type, boolean required, String dictType) {
        MaterialFieldDefinition field = new MaterialFieldDefinition();
        field.setKey(key);
        field.setLabel(key);
        field.setType(type);
        field.setRequired(required);
        field.setDictType(dictType);
        return field;
    }
}
