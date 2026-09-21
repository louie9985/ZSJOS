package cn.iocoder.yudao.module.zsjos.service.material;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialApprovalRoundDO;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MaterialApprovalObjectPermissionProviderTest {
    @InjectMocks MaterialApprovalObjectPermissionProvider provider;
    @Mock BpmProcessTaskApi taskApi;
    @Mock MaterialApprovalRoundMapper roundMapper;
    @Mock cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    void round() {
        when(roundMapper.selectList(any(SFunction.class),eq(3L))).thenReturn(List.of(new MaterialApprovalRoundDO()
                .setProcessInstanceId("p").setProcessDefinitionKey("zsjos_viral_account_review")));
    }
    @Test void aRoleWithoutAnAssignedTaskCannotReadOrDecide() {
        round();when(taskApi.getTodoTaskPage(eq(9L),any())).thenReturn(PageResult.empty());
        when(taskApi.getDoneTaskPage(eq(9L),any())).thenReturn(PageResult.empty());
        assertFalse(provider.hasPermission(3L,"read",9L));
        assertFalse(provider.hasPermission(3L,"decide",9L));
        verify(taskApi,times(2)).getTodoTaskPage(eq(9L),argThat(q->q.getProcessInstanceIds().equals(List.of("p"))));
    }
    @Test void ownHistoricalTaskGrantsReadButNotDecision() {
        round();when(taskApi.getTodoTaskPage(eq(9L),any())).thenReturn(PageResult.empty());
        when(taskApi.getDoneTaskPage(eq(9L),any())).thenReturn(new PageResult<>(List.of(new BpmTaskRespDTO()),1L));
        assertTrue(provider.hasPermission(3L,"read",9L));
        assertFalse(provider.hasPermission(3L,"decide",9L));
    }
    @Test void invalidActionsAndUnauthenticatedUsersNeverQueryBpm() {
        assertFalse(provider.hasPermission(3L,"manage",9L));
        assertFalse(provider.hasPermission(3L,"read",null));
        verifyNoInteractions(taskApi,roundMapper);
    }
    @Test void administratorReadsHistoryWithoutAcquiringDecision() {
        round(); when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        assertTrue(provider.hasPermission(3L,"read",9L));
        verifyNoInteractions(taskApi);
        when(taskApi.getTodoTaskPage(eq(9L),any())).thenReturn(PageResult.empty());
        assertFalse(provider.hasPermission(3L,"decide",9L));
    }
}
