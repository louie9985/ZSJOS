package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialReviewVisibilityTest {
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialFileMapper materialFileMapper;
    @Mock PermissionApi permissionApi;
    @Mock BpmProcessTaskApi processTaskApi;
    @Mock AdminUserApi adminUserApi;
    @InjectMocks MaterialService service;

    @Test void pendingReviewUsesActualBpmAssigneesAndHandlesUnassignedTasks() {
        var version = new MaterialVersionDO().setId(3L).setStatus("IN_APPROVAL").setProcessInstanceId("p")
                .setValuesJson("{}").setFieldSnapshotJson("[]").setDictSnapshotJson("{}");
        when(processTaskApi.getPendingTasks("p")).thenReturn(List.of(
                new BpmPendingTaskRespDTO("a","review",7L,null),
                new BpmPendingTaskRespDTO("b","review",null,null)));
        when(adminUserApi.getUserMap(Set.of(7L))).thenReturn(Map.of(7L,new AdminUserRespDTO().setNickname("审核员")));
        assertEquals(List.of("审核员","待分配或审核人不可用"),service.toVersionResp(version).getPendingApproverNames());
    }

    @Test void onlyConfiguredStarterCanSeeCancelEvenWhenAnotherUserCanManage() {
        var material = new MaterialDO().setId(1L).setOwnerUserId(7L).setCurrentDraftVersionId(3L).setStatus("IN_APPROVAL");
        var draft = new MaterialVersionDO().setId(3L).setStatus("IN_APPROVAL").setSubmittedByUserId(7L);
        when(versionMapper.selectById(3L)).thenReturn(draft);
        assertFalse(service.availableActions(material,draft,7L,false).contains("CANCEL"));
        when(permissionApi.hasAnyPermissions(7L,"bpm:process-instance:cancel")).thenReturn(true);
        assertTrue(service.availableActions(material,draft,7L,false).contains("CANCEL"));
        assertFalse(service.availableActions(material,draft,8L,true).contains("CANCEL"));
        draft.setStatus("DRAFT");
        assertFalse(service.availableActions(material,draft,7L,false).contains("CANCEL"));
    }
}
