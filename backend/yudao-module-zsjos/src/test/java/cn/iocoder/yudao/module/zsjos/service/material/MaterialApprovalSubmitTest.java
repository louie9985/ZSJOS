package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialApprovalSubmitTest {
    @InjectMocks private MaterialService service;
    @Mock private MaterialMapper materialMapper;
    @Mock private MaterialVersionMapper versionMapper;
    @Mock private MaterialTypeMapper typeMapper;
    @Mock private MaterialSchemaVersionMapper schemaMapper;
    @Mock private MaterialSchemaService schemaService;
    @Mock private MaterialApprovalRoundMapper approvalRoundMapper;
    @Mock private BpmDefinitionReadApi definitionReadApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private void draft(String code) {
        MaterialDO material = new MaterialDO().setId(1L).setMaterialNo("MAT-TEST")
                .setMaterialTypeId(2L).setVersion(0).setCurrentDraftVersionId(3L).setStatus("DRAFT");
        MaterialVersionDO version = new MaterialVersionDO().setId(3L).setMaterialId(1L)
                .setVersion(0).setVersionNo(1).setSchemaVersionId(4L).setStatus("DRAFT")
                .setTitle("Frozen title").setSummary("Frozen summary").setSearchText("Original label")
                .setValuesJson("{}").setCoverSnapshotJson("{}");
        when(materialMapper.selectByIdForUpdate(1L, 1L)).thenReturn(material);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(version);
        when(typeMapper.selectById(2L)).thenReturn(new MaterialTypeDO().setId(2L)
                .setCode(code).setBpmProcessDefinitionKey("obsolete_binding"));
        when(schemaMapper.selectById(4L)).thenReturn(new MaterialSchemaVersionDO().setFieldsJson("[]"));
        when(schemaService.parseFields("[]")).thenReturn(List.of());
        when(schemaService.normalize(anyList(), anyMap(), eq(9L))).thenReturn(
                new MaterialSchemaService.NormalizedMaterial(
                        "viral_account".equals(code) ? Map.of("account_name", "Account") : Map.of(),
                        Map.of(), List.of(), List.of(), List.of(), ""));
    }

    @ParameterizedTest @ValueSource(strings = {"viral_account", "viral_content"})
    void submitUsesFixedKeyAndFrozenFormValues(String code) {
        draft(code);
        String key = "zsjos_" + code + "_review";
        when(definitionReadApi.getPublishedProcessDefinition(key)).thenReturn(
                new BpmProcessDefinitionMetadataRespDTO().setId("def").setKey(key)
                        .setVersion(2).setCategory("zsjos_material").setSuspended(false));
        when(versionMapper.submit(anyLong(), anyInt(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyLong(), any())).thenReturn(1);
        when(materialMapper.updateDraftPointer(anyLong(), anyInt(), anyLong(), anyString())).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(9L), any())).thenAnswer(call -> {
            BpmProcessInstanceCreateReqDTO req = call.getArgument(1);
            assertEquals(key, req.getProcessDefinitionKey());
            assertEquals("material-version:3", req.getBusinessKey());
            assertTrue(req.getStartUserSelectAssignees().isEmpty());
            assertEquals("Original label", req.getVariables().get("materialContent"));
            assertEquals("Frozen title", req.getVariables().get("materialTitle"));
            return req.getPredefinedProcessInstanceId();
        });
        service.submit(1L, new MaterialSubmitReqVO().setExpectedVersion(0)
                .setStartUserSelectAssignees(Map.of("viralReview", List.of(999L))), 9L);
        verify(processInstanceApi).createProcessInstance(eq(9L), any());
        verify(definitionReadApi, never()).getPublishedProcessDefinition("obsolete_binding");
    }

    @ParameterizedTest @ValueSource(strings = {"viral_account", "viral_content"})
    void absentDefinitionNeverStartsOrPersistsApproval(String code) {
        draft(code);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.submit(1L, new MaterialSubmitReqVO().setExpectedVersion(0), 9L));
        verifyNoInteractions(processInstanceApi, approvalRoundMapper);
    }
}
