package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PointsProcessResultTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void repeatedApprovalQueuesOnceAndNeverCallsExternalApi() {
        TenantContextHolder.setTenantId(1L);
        var service=new PointsRequestService();var mapper=mock(PointsRequestMapper.class);var platform=mock(PointsPlatformClient.class);
        ReflectionTestUtils.setField(service,"requests",mapper);ReflectionTestUtils.setField(service,"platform",platform);
        var row=new PointsRequestDO();row.setId(9L);row.setApprovalStatus("pending");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);
        when(mapper.lock(9L,1L)).thenReturn(row);
        service.processResult("process",BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        service.processResult("process",BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        assertEquals("approved",row.getApprovalStatus());assertEquals("queued",row.getIssueStatus());
        verify(mapper,times(1)).updateById(row);verifyNoInteractions(platform);
    }
    @Test void rejectionCannotIssue() {
        TenantContextHolder.setTenantId(1L);var service=new PointsRequestService();var mapper=mock(PointsRequestMapper.class);
        ReflectionTestUtils.setField(service,"requests",mapper);var row=new PointsRequestDO();row.setId(9L);row.setApprovalStatus("pending");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);when(mapper.lock(9L,1L)).thenReturn(row);
        service.processResult("process",BpmProcessInstanceStatusEnum.REJECT.getStatus());
        assertEquals("rejected",row.getApprovalStatus());assertEquals("waiting",row.getIssueStatus());assertNull(row.getNextAttemptAt());
    }
}
