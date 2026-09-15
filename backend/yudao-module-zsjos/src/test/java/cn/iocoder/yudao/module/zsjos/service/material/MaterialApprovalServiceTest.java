package cn.iocoder.yudao.module.zsjos.service.material;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.*;
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
class MaterialApprovalServiceTest {
    @InjectMocks MaterialApprovalService service;
    @Mock BpmProcessTaskApi taskApi;
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialMapper materialMapper;
    @Mock MaterialTypeMapper typeMapper;
    @Mock MaterialApprovalRoundMapper roundMapper;
    @Mock MaterialService materialService;
    MaterialVersionDO version;
    MaterialDO material;
    BpmTaskRespDTO task;
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void data(String type) {
        task = new BpmTaskRespDTO().setId("task").setProcessInstanceId("process")
                .setBusinessKey("material-version:3").setProcessDefinitionKey("zsjos_"+type+"_review");
        version = new MaterialVersionDO().setId(3L).setMaterialId(1L).setProcessInstanceId("process")
                .setTitle("submitted").setStatus("IN_APPROVAL");
        material = new MaterialDO().setId(1L).setMaterialTypeId(2L).setCurrentDraftVersionId(3L).setMaterialNo("MAT-test");
        when(versionMapper.selectById(3L)).thenReturn(version);
        when(materialMapper.selectById(1L)).thenReturn(material);
        when(typeMapper.selectById(2L)).thenReturn(new MaterialTypeDO().setId(2L).setCode(type));
        when(roundMapper.selectByProcessInstanceId("process")).thenReturn(new MaterialApprovalRoundDO()
                .setMaterialVersionId(3L).setProcessDefinitionKey(task.getProcessDefinitionKey()).setBusinessKey(task.getBusinessKey()));
    }
    @ParameterizedTest @ValueSource(strings={"viral_account","viral_content"})
    void ownerReadsSubmittedSnapshot(String type) {
        data(type); when(taskApi.getTodoTask(9L,"task")).thenReturn(task);
        MaterialVersionRespVO snapshot = new MaterialVersionRespVO().setTitle("snapshot");
        when(materialService.toVersionResp(version)).thenReturn(snapshot);
        assertSame(snapshot,service.get(3L,"task",false,9L).getSnapshot());
    }
    @Test void otherUserCannotRead() {
        assertThrows(ServiceException.class,()->service.get(3L,"task",false,10L));
        verifyNoInteractions(versionMapper,materialService);
    }
    @Test void cannotSubstituteVersion() {
        when(taskApi.getTodoTask(9L,"task")).thenReturn(new BpmTaskRespDTO().setBusinessKey("material-version:4"));
        assertThrows(ServiceException.class,()->service.get(3L,"task",false,9L));
        verifyNoInteractions(versionMapper,materialService);
    }
    @Test void oldTaskNeverDisplaysEditedSnapshot() {
        data("viral_account"); version.setStatus("DRAFT");when(taskApi.getDoneTask(9L,"task")).thenReturn(task);
        ServiceException e=assertThrows(ServiceException.class,()->service.get(3L,"task",true,9L));
        assertEquals(MaterialApprovalErrors.STALE_SNAPSHOT.getCode(),e.getCode());verifyNoInteractions(materialService);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void decisionUsesBpmAndDoesNotUpdateBusinessStateDirectly(boolean approve) {
        data("viral_content");when(taskApi.getTodoTask(9L,"task")).thenReturn(task);
        when(materialMapper.selectByIdForUpdate(1L,1L)).thenReturn(material);
        when(versionMapper.selectByIdForUpdate(3L,1L)).thenReturn(version);
        service.decide(3L,"task","reviewed",approve,9L);
        if(approve) verify(taskApi).approveTask(eq(9L),argThat(d->d.getTaskId().equals("task") && d.getReason().equals("reviewed")));
        else verify(taskApi).rejectTask(eq(9L),any());
        verify(versionMapper,never()).updateById(any(MaterialVersionDO.class));verify(materialMapper,never()).updateById(any(MaterialDO.class));
    }
    @Test void repeatedDecisionCannotMutate() {
        when(versionMapper.selectById(3L)).thenReturn(new MaterialVersionDO().setMaterialId(1L));
        assertThrows(ServiceException.class,()->service.decide(3L,"finished-task","reviewed",true,9L));
        verify(taskApi,never()).approveTask(anyLong(),any());verify(taskApi,never()).rejectTask(anyLong(),any());
    }
    @Test void differentApprovalRoundDenied() {
        data("viral_account");when(taskApi.getTodoTask(9L,"task")).thenReturn(task);
        when(roundMapper.selectByProcessInstanceId("process")).thenReturn(new MaterialApprovalRoundDO().setMaterialVersionId(77L));
        assertThrows(ServiceException.class,()->service.get(3L,"task",false,9L));verifyNoInteractions(materialService);
    }
}
