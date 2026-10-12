package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PointsObjectPermissionProviderTest {
    @Test void approvalOrManagementNeverGrantsSecretAccess() {
        var provider=new PointsObjectPermissionProvider();var mapper=mock(PointsRequestMapper.class);
        var progress=mock(BpmProcessProgressApi.class);var permissions=mock(PermissionApi.class);
        ReflectionTestUtils.setField(provider,"requests",mapper);ReflectionTestUtils.setField(provider,"progress",progress);ReflectionTestUtils.setField(provider,"permissions",permissions);
        var row=new PointsRequestDO();row.setApplicantUserId(11L);row.setProcessInstanceId("process");when(mapper.selectById(1L)).thenReturn(row);
        when(progress.isParticipant("process",22L)).thenReturn(true);
        when(permissions.hasAnyPermissions(33L,PointsRequestService.PERM+"manage")).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"reveal",11L));assertTrue(provider.hasPermission(1L,"read",22L));
        assertTrue(provider.hasPermission(1L,"manage",33L));assertFalse(provider.hasPermission(1L,"reveal",22L));
        assertFalse(provider.hasPermission(1L,"reveal",33L));assertFalse(provider.hasPermission(1L,"cancel",33L));
        assertFalse(provider.hasPermission(1L,"read",44L));assertFalse(provider.hasPermission(2L,"read",11L));
    }
}
