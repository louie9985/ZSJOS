package cn.iocoder.yudao.module.zsjos.service.material;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class MaterialApprovalResultTest {
    @InjectMocks MaterialService service;
    @Mock MaterialMapper materialMapper;
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialApprovalRoundMapper approvalRoundMapper;
    @BeforeEach void setup() {TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear() {TenantContextHolder.clear();}
    @ParameterizedTest @ValueSource(booleans={true,false})
    void cancellationRestoresDraftAndPreservesPublishedVersion(boolean published) {
        var material = new MaterialDO().setId(1L).setCurrentDraftVersionId(3L)
                .setCurrentEffectiveVersionId(published ? 2L : null);
        var version = new MaterialVersionDO().setId(3L).setMaterialId(1L).setStatus("IN_APPROVAL")
                .setProcessInstanceId("p").setProcessDefinitionId("d").setProcessDefinitionVersion(1);
        var round = new MaterialApprovalRoundDO().setId(4L).setMaterialVersionId(3L)
                .setProcessDefinitionId("d").setProcessDefinitionVersion(1).setProcessDefinitionKey("zsjos_viral_account_review")
                .setBusinessKey("material-version:3");
        when(approvalRoundMapper.selectByProcessInstanceId("p")).thenReturn(round);
        when(versionMapper.selectById(3L)).thenReturn(version);
        when(materialMapper.selectByIdForUpdate(1L,1L)).thenReturn(material);
        when(versionMapper.selectByIdForUpdate(3L,1L)).thenReturn(version);
        when(approvalRoundMapper.selectByProcessInstanceIdForUpdate("p",1L)).thenReturn(round);
        when(versionMapper.transition(3L,"IN_APPROVAL","DRAFT",null,null,null)).thenReturn(1);
        when(materialMapper.finishRejectedVersion(material,published ? "EFFECTIVE" : "DRAFT")).thenReturn(1);
        var event = new BpmProcessInstanceStatusEvent(this).setId("p").setBusinessKey("material-version:3")
                .setProcessDefinitionId("d").setProcessDefinitionVersion(1).setProcessDefinitionKey("zsjos_viral_account_review")
                .setStatus(BpmProcessInstanceStatusEnum.CANCEL.getStatus()).setReason("修改内容").setEventKey("cancel-event");
        service.handleProcessResult(event);
        service.handleProcessResult(event);
        assertEquals("CANCELLED",round.getStatus());
        assertEquals("修改内容",round.getResultReason());
        verify(versionMapper,times(1)).transition(3L,"IN_APPROVAL","DRAFT",null,null,null);
        verify(materialMapper,never()).activateVersion(any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void bpmCompletionActivatesOrRejectsMaterialAndDuplicateEventDoesNothing(boolean approve) {
        var material = new MaterialDO().setId(1L).setCurrentDraftVersionId(3L);
        var version = new MaterialVersionDO().setId(3L).setMaterialId(1L).setStatus("IN_APPROVAL")
                .setProcessInstanceId("p").setProcessDefinitionId("d").setProcessDefinitionVersion(1).setTitle("frozen");
        var round = new MaterialApprovalRoundDO().setId(4L).setMaterialVersionId(3L)
                .setProcessDefinitionId("d").setProcessDefinitionVersion(1).setProcessDefinitionKey("zsjos_viral_account_review")
                .setBusinessKey("material-version:3");
        when(approvalRoundMapper.selectByProcessInstanceId("p")).thenReturn(round);
        when(versionMapper.selectById(3L)).thenReturn(version);
        when(materialMapper.selectByIdForUpdate(1L,1L)).thenReturn(material);
        when(versionMapper.selectByIdForUpdate(3L,1L)).thenReturn(version);
        when(approvalRoundMapper.selectByProcessInstanceIdForUpdate("p",1L)).thenReturn(round);
        when(versionMapper.transition(eq(3L),eq("IN_APPROVAL"),anyString(),any(),any(),any())).thenReturn(1);
        if(approve) when(materialMapper.activateVersion(eq(material),eq(3L),eq("frozen"),isNull(),isNull())).thenReturn(1);
        else when(materialMapper.finishRejectedVersion(material,"REJECTED")).thenReturn(1);
        var event = new BpmProcessInstanceStatusEvent(this).setId("p").setBusinessKey("material-version:3")
                .setProcessDefinitionId("d").setProcessDefinitionVersion(1).setProcessDefinitionKey("zsjos_viral_account_review")
                .setStatus((approve ? BpmProcessInstanceStatusEnum.APPROVE : BpmProcessInstanceStatusEnum.REJECT).getStatus())
                .setReason("decision").setEventKey("event");
        service.handleProcessResult(event);
        assertEquals(approve ? "EFFECTIVE" : "REJECTED",round.getStatus());
        service.handleProcessResult(event);
        verify(versionMapper,times(1)).transition(eq(3L),eq("IN_APPROVAL"),eq(approve ? "EFFECTIVE" : "REJECTED"),any(),any(),any());
        verify(approvalRoundMapper,times(1)).updateById(round);
    }
}
