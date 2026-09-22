package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmUserTaskMetadataRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialSchemaService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.BPM_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.MATERIAL_TYPE_PRODUCTION_CONTENT;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService.DIRECTOR_TASK_KEY;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService.FINAL_TASK_KEY;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService.PROCESS_DEFINITION_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentReviewConfigServiceTest {

    @Test
    void unpublishedAndSuspendedDefinitionsHaveDifferentRecoveryMessages() {
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class, () -> service.requireCurrentDefinition(null));
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_PROCESS_NOT_PUBLISHED.getCode(), missing.getCode());
        var definition = twoStageDefinition();
        definition.setSuspended(true);
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        ServiceException suspended = assertThrows(ServiceException.class, () -> service.requireCurrentDefinition(null));
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_PROCESS_SUSPENDED.getCode(), suspended.getCode());
    }

    @InjectMocks private ContentReviewConfigService service;
    @Mock private ContentReviewConfigMapper contentReviewConfigMapper;
    @Mock private BpmDefinitionReadApi definitionReadApi;
    @Mock private MaterialTypeMapper materialTypeMapper;
    @Mock private MaterialTypeService materialTypeService;
    @Mock private MaterialSchemaService materialSchemaService;

    /** 内置模板与固定映射必须让固定流程契约直接通过，否则运营提交仍会卡在配置校验上。 */
    @Test
    void requireReadyConfigPassesWithSeededMappingAndBundledSchema() {
        stubPublishedProductionSchema();
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY))
                .thenReturn(twoStageDefinition());
        ContentReviewConfigDO config = configWithMapping(seededMapping());
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(config);

        assertSame(config, service.requireReadyConfig(1L));
    }

    /** 存量租户的空映射会在启动时补齐，收录时才有来源可取。 */
    @Test
    void ensureDefaultConfigRepairsEmptyMappingForExistingTenant() {
        stubPublishedProductionSchema();
        ContentReviewConfigDO existing = configWithMapping(Map.of());
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(existing);

        service.ensureDefaultConfig();

        ArgumentCaptor<ContentReviewConfigDO> captor = ArgumentCaptor.forClass(ContentReviewConfigDO.class);
        verify(contentReviewConfigMapper).updateConfig(captor.capture(), eq(existing.getVersion()));
        Map<String, String> mapping = service.mapping(captor.getValue());
        assertEquals("coverFileId", mapping.get("__cover__"));
        assertEquals("title", mapping.get("content_title"));
        assertEquals("deliverableFileIds", mapping.get("deliverable_files"));
        assertEquals("accountStage", mapping.get("account_stage"));
        verify(contentReviewConfigMapper, never()).insert(any(ContentReviewConfigDO.class));
    }

    /** 管理员已配置过的映射不能被固定默认值覆盖。 */
    @Test
    void ensureDefaultConfigKeepsConfiguredMapping() {
        ContentReviewConfigDO existing = configWithMapping(Map.of("content_title", "topic"));
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(existing);

        service.ensureDefaultConfig();

        verify(contentReviewConfigMapper, never()).updateConfig(any(), any());
    }

    /** 租户删掉模板字段时，默认映射按已发布模板过滤，不能反过来把提交卡死。 */
    @Test
    void defaultMappingSkipsFieldsMissingFromPublishedSchema() {
        MaterialTypeDO type = productionType();
        when(materialTypeMapper.selectByCode(MATERIAL_TYPE_PRODUCTION_CONTENT)).thenReturn(type);
        MaterialSchemaVersionDO schema = new MaterialSchemaVersionDO();
        schema.setId(9L);
        schema.setFieldsJson("[]");
        when(materialTypeService.requirePublishedSchema(type)).thenReturn(schema);
        when(materialSchemaService.parseFields("[]")).thenReturn(List.of(
                textField("content_title")));
        ContentReviewConfigDO existing = configWithMapping(Map.of());
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(existing);

        service.ensureDefaultConfig();

        ArgumentCaptor<ContentReviewConfigDO> captor = ArgumentCaptor.forClass(ContentReviewConfigDO.class);
        verify(contentReviewConfigMapper).updateConfig(captor.capture(), eq(existing.getVersion()));
        Map<String, String> mapping = service.mapping(captor.getValue());
        assertEquals("title", mapping.get("content_title"));
        assertTrue(mapping.containsKey("__cover__"));
        assertFalse(mapping.containsKey("deliverable_files"));
        assertFalse(mapping.containsKey("account_stage"));
    }

    /**
     * SIMPLE 设计器编译时必然生成发起人提交节点，它不是业务审批节点。曾因把它计入业务节点
     * 导致本流程永远无法提交，这里锁住"三个 userTask 但只有两个业务节点"必须通过。
     */
    @Test
    void requireReadyConfigAcceptsDefinitionCarryingSimpleSubmissionTask() {
        stubPublishedProductionSchema();
        BpmProcessDefinitionMetadataRespDTO definition = twoStageDefinition();
        assertEquals(3, definition.getUserTasks().size());
        assertFalse(Boolean.TRUE.equals(definition.getSimpleSequentialApproval()));
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        ContentReviewConfigDO config = configWithMapping(seededMapping());
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(config);

        assertSame(config, service.requireReadyConfig(1L));
    }

    /** 业务审批节点多于两个仍必须拒绝——排除提交节点不等于放宽骨架约束。 */
    @Test
    void requireReadyConfigRejectsExtraApprovalTask() {
        stubPublishedProductionSchema();
        BpmProcessDefinitionMetadataRespDTO definition = twoStageDefinition();
        BpmUserTaskMetadataRespDTO extra = new BpmUserTaskMetadataRespDTO();
        extra.setKey("extraReview");
        extra.setExecutionMode("SINGLE");
        extra.setNextUserTaskKeys(List.of());
        definition.getUserTasks().add(extra);
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(configWithMapping(seededMapping()));

        assertThrows(ServiceException.class, () -> service.requireReadyConfig(1L));
    }

    /** 终审节点改为角色解析后必须仍是单人执行，否则逐条结论无处落库。 */
    @Test
    void requireReadyConfigRejectsMultiInstanceFinalTask() {
        stubPublishedProductionSchema();
        BpmProcessDefinitionMetadataRespDTO definition = twoStageDefinition();
        definition.getUserTasks().stream().filter(task -> FINAL_TASK_KEY.equals(task.getKey()))
                .findFirst().orElseThrow().setExecutionMode("PARALLEL_MULTI_INSTANCE");
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(configWithMapping(seededMapping()));

        assertThrows(ServiceException.class, () -> service.requireReadyConfig(1L));
    }

    @Test
    void refusesRejectionThatReturnsToAnInternalTask() {
        BpmProcessDefinitionMetadataRespDTO definition = twoStageDefinition();
        definition.getUserTasks().stream().filter(task -> FINAL_TASK_KEY.equals(task.getKey()))
                .findFirst().orElseThrow().setRejectEndsProcess(false);
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        assertThrows(ServiceException.class, () -> service.requireCurrentDefinition(new ContentReviewConfigDO()));
    }

    @Test
    void acceptsDesignerGeneratedIdsAndResolvesByEdgesInsteadOfListOrderOrLabels() {
        stubPublishedProductionSchema();
        var definition = twoStageDefinition();
        var start = definition.getUserTasks().get(0);
        var director = definition.getUserTasks().get(1);
        var last = definition.getUserTasks().get(2);
        director.setKey("Activity_director_generated");
        last.setKey("Activity_final_generated");
        director.setName("arbitrary first label");
        last.setName("arbitrary second label");
        start.setNextUserTaskKeys(List.of(director.getKey()));
        director.setNextUserTaskKeys(List.of(last.getKey()));
        definition.setUserTasks(List.of(last, start, director));
        when(definitionReadApi.getPublishedProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(definition);
        when(contentReviewConfigMapper.selectCurrent()).thenReturn(configWithMapping(seededMapping()));
        assertDoesNotThrow(() -> service.requireReadyConfig(1L));
        var keys = service.requireReviewTaskKeys(definition);
        assertEquals(director.getKey(), keys.director());
        assertEquals(last.getKey(), keys.finalReview());
    }

    @Test
    void resolvesOriginalAssetKeysAndRejectsBrokenEntryOrCycle() {
        var definition = twoStageDefinition();
        assertEquals(DIRECTOR_TASK_KEY, service.requireReviewTaskKeys(definition).director());
        assertEquals(FINAL_TASK_KEY, service.requireReviewTaskKeys(definition).finalReview());
        definition.getUserTasks().getFirst().setNextUserTaskKeys(List.of(FINAL_TASK_KEY));
        assertThrows(ServiceException.class, () -> service.requireReviewTaskKeys(definition));
        definition.getUserTasks().getFirst().setNextUserTaskKeys(List.of(DIRECTOR_TASK_KEY));
        definition.getUserTasks().getLast().setNextUserTaskKeys(List.of(DIRECTOR_TASK_KEY));
        assertThrows(ServiceException.class, () -> service.requireReviewTaskKeys(definition));
    }

    private Map<String, String> seededMapping() {
        return Map.ofEntries(
                Map.entry("__cover__", "coverFileId"),
                Map.entry("content_title", "title"),
                Map.entry("content_topic", "topic"),
                Map.entry("script_text", "scriptText"),
                Map.entry("deliverable_url", "deliverableUrl"),
                Map.entry("lead_resource_url", "leadResourceUrl"),
                Map.entry("planned_publish_at", "plannedPublishAt"),
                Map.entry("deliverable_files", "deliverableFileIds"),
                Map.entry("account_type", "accountType"),
                Map.entry("profession", "profession"),
                Map.entry("account_stage", "accountStage"));
    }

    private void stubPublishedProductionSchema() {
        MaterialTypeDO type = productionType();
        when(materialTypeMapper.selectByCode(MATERIAL_TYPE_PRODUCTION_CONTENT)).thenReturn(type);
        MaterialSchemaVersionDO schema = new MaterialSchemaVersionDO();
        schema.setId(7L);
        schema.setSchemaHash("hash");
        schema.setFieldsJson("[]");
        when(materialTypeService.requirePublishedSchema(type)).thenReturn(schema);
        when(materialSchemaService.parseFields("[]")).thenReturn(productionFields());
    }

    private MaterialTypeDO productionType() {
        MaterialTypeDO type = new MaterialTypeDO();
        type.setId(3L);
        type.setCode(MATERIAL_TYPE_PRODUCTION_CONTENT);
        type.setStatus(CommonStatusEnum.ENABLE.getStatus());
        type.setAllowAutoCollect(true);
        type.setCurrentSchemaVersionId(7L);
        return type;
    }

    /** 与 ProductionContentMaterialSchema 内置模板保持一致的字段集合。 */
    private List<cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition> productionFields() {
        return List.of(
                textField("content_title"),
                field("content_topic", "textarea"),
                field("script_text", "textarea"),
                field("deliverable_url", "https-link"),
                field("lead_resource_url", "https-link"),
                field("planned_publish_at", "datetime"),
                field("deliverable_files", "video"),
                dictField("account_type", "zsjos_persona_type"),
                dictField("profession", "zsjos_material_profession"),
                dictField("account_stage", "zsjos_media_account_stage"));
    }

    private cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition textField(String key) {
        return field(key, "text");
    }

    private cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition field(String key, String type) {
        return new cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition()
                .setKey(key).setLabel(key).setType(type).setRequired(false);
    }

    private cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition dictField(String key,
                                                                                            String dictType) {
        return field(key, "dict-single").setDictType(dictType);
    }

    private ContentReviewConfigDO configWithMapping(Map<String, String> mapping) {
        ContentReviewConfigDO config = new ContentReviewConfigDO();
        config.setId(1L);
        config.setVersion(0);
        config.setProductionMaterialTypeCode(MATERIAL_TYPE_PRODUCTION_CONTENT);
        config.setMaterialFieldMappingJson(JsonUtils.toJsonString(mapping));
        config.setMaterialDefaultValuesJson("{}");
        return config;
    }

    /**
     * 按 SIMPLE 设计器的真实产物构造：除两个业务审批节点外，必然还有一个发起人提交节点，
     * 且 simpleSequentialApproval 因按全部 userTask 计数而为 false。
     */
    private BpmProcessDefinitionMetadataRespDTO twoStageDefinition() {
        BpmProcessDefinitionMetadataRespDTO definition = new BpmProcessDefinitionMetadataRespDTO();
        definition.setId("definition-1");
        definition.setKey(PROCESS_DEFINITION_KEY);
        definition.setVersion(2);
        definition.setCategory(BPM_CATEGORY);
        definition.setSuspended(false);
        definition.setSimpleSequentialApproval(false);
        BpmUserTaskMetadataRespDTO submission = new BpmUserTaskMetadataRespDTO();
        submission.setKey("StartUserNode");
        submission.setExecutionMode("SINGLE");
        submission.setNextUserTaskKeys(List.of(DIRECTOR_TASK_KEY));
        BpmUserTaskMetadataRespDTO director = new BpmUserTaskMetadataRespDTO();
        director.setKey(DIRECTOR_TASK_KEY);
        director.setRejectEndsProcess(true);
        director.setExecutionMode("SINGLE");
        director.setNextUserTaskKeys(List.of(FINAL_TASK_KEY));
        BpmUserTaskMetadataRespDTO last = new BpmUserTaskMetadataRespDTO();
        last.setKey(FINAL_TASK_KEY);
        last.setRejectEndsProcess(true);
        last.setExecutionMode("SINGLE");
        last.setNextUserTaskKeys(List.of());
        definition.setUserTasks(new java.util.ArrayList<>(List.of(submission, director, last)));
        return definition;
    }
}
