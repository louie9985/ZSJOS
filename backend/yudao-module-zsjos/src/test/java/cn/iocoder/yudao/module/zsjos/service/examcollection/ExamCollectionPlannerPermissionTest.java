package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCancellationService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

class ExamCollectionPlannerPermissionTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void plannerReadRequiresOwnLiveTaskAndNeverGrantsManagementOrCancellation(){
        TenantContextHolder.setTenantId(1L);var provider=new ExamCollectionPlannerPermissionProvider();
        var collections=mock(ExamCollectionTaskMapper.class);var tasks=mock(BusinessTaskMapper.class);var permissions=mock(PermissionApi.class);
        ReflectionTestUtils.setField(provider,"collections",collections);ReflectionTestUtils.setField(provider,"tasks",tasks);ReflectionTestUtils.setField(provider,"permissions",permissions);
        var collection=new ExamCollectionTaskDO();collection.setId(1L);collection.setTenantId(1L);collection.setStatus(OPEN);when(collections.selectById(1L)).thenReturn(collection);
        var task=new BusinessTaskDO();task.setBizId(1L);task.setBizType(BIZ_TYPE);task.setTaskType(TASK_TYPE);task.setAssigneeId(7L);task.setTenantId(1L);task.setStatus("pending");
        when(tasks.selectMyPendingByBusiness(anyLong(),eq(BIZ_TYPE),eq(1L))).thenReturn(List.of(task));
        assertFalse(provider.hasPermission(1L,"query",7L));
        when(permissions.hasAnyPermissions(anyLong(),eq("zsjos:business-task:query"))).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"query",7L));assertFalse(provider.hasPermission(1L,"query",8L));assertFalse(provider.hasPermission(1L,"complete",7L));
        assertFalse(BusinessTaskCancellationService.supports(task));
        collection.setStatus(COMPLETED);assertFalse(provider.hasPermission(1L,"query",7L));collection.setStatus(OPEN);
        collection.setTenantId(2L);assertFalse(provider.hasPermission(1L,"query",7L));collection.setTenantId(1L);
        task.setTenantId(2L);assertFalse(provider.hasPermission(1L,"query",7L));task.setTenantId(1L);
        task.setDeleted(true);assertFalse(provider.hasPermission(1L,"query",7L));
    }
}
