package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnscheduledDeliveryClassServiceTest {
    private final DeliveryClassMapper mapper = mock(DeliveryClassMapper.class);
    private final DeliveryClassService qualification = mock(DeliveryClassService.class);
    private final UnscheduledDeliveryClassService service = new UnscheduledDeliveryClassService();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(service,"classes",mapper);
        ReflectionTestUtils.setField(service,"classService",qualification);
        ReflectionTestUtils.setField(service,"depts",mock(DeptApi.class));
        when(qualification.validateHomeroom(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setNickname("规划师"));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void createsOwnedUndatedClassAndReusesIt() {
        var row = service.ensure(20L);
        assertEquals("未确定考期",row.getClassName()); assertEquals(20L,row.getHomeroomUserId());
        assertEquals("UNSCHEDULED",row.getClassKind()); assertNull(row.getExamScheduleSnapshot());
        assertTrue(DeliveryClassKind.assignable(row)); assertFalse(DeliveryClassKind.pending(row));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);
        assertSame(row,service.ensure(20L));
        verify(mapper,times(1)).insert(any(DeliveryClassDO.class));
        verify(mapper,never()).updateById(any(DeliveryClassDO.class));
    }
    @Test void concurrentWinnerIsReadWithCurrentLockingRead() {
        var winner = new DeliveryClassDO().setId(88L).setClassKind("UNSCHEDULED").setHomeroomUserId(20L);
        when(mapper.insert(any(DeliveryClassDO.class))).thenThrow(new DuplicateKeyException("race"));
        when(mapper.selectPlannerDefaultForUpdate("UNSCHEDULED",20L,1L)).thenReturn(winner);
        assertSame(winner,service.ensure(20L));
    }
    @Test void ownedDefaultNeverGetsGlobalPendingReadException() {
        var provider = new DeliveryClassObjectPermissionProvider();
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        var scope = mock(DeliveryClassScopeService.class);
        ReflectionTestUtils.setField(provider,"mapper",mapper);
        ReflectionTestUtils.setField(provider,"permissionApi",permissions);
        ReflectionTestUtils.setField(provider,"scopeService",scope);
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
        var row = new DeliveryClassDO().setId(2L).setClassKind("UNSCHEDULED").setSystemClass(true)
                .setHomeroomUserId(20L).setDeptId(80L);
        when(mapper.selectById(2L)).thenReturn(row);
        assertTrue(provider.hasPermission(2L,"read",20L));
        assertFalse(provider.hasPermission(2L,"read",30L));
        when(scope.containsRead(30L,80L)).thenReturn(true);
        assertTrue(provider.hasPermission(2L,"read",30L));
    }
    @Test void initializerUsesTenantOneAndHonorsMaintenance() {
        var initializer = new cn.iocoder.yudao.module.zsjos.job.studentdelivery.UnscheduledDeliveryClassInitializer();
        var classes = mock(UnscheduledDeliveryClassService.class);
        var maintenance = mock(cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi.class);
        ReflectionTestUtils.setField(initializer,"classes",classes);
        ReflectionTestUtils.setField(initializer,"maintenance",maintenance);
        TenantContextHolder.clear();
        doAnswer(call -> { assertEquals(1L,TenantContextHolder.getRequiredTenantId()); return null; }).when(classes).reconcile();
        initializer.initialize(); verify(classes).reconcile(); assertNull(TenantContextHolder.getTenantId());
        when(maintenance.isEnabled()).thenReturn(true);
        initializer.reconcile(); verifyNoMoreInteractions(classes);
    }
    @Test void inactivePlannerCannotCreateAndDepartmentChangeKeepsIdentity() {
        when(qualification.validateHomeroom(20L)).thenThrow(new IllegalStateException("inactive"));
        assertThrows(IllegalStateException.class,() -> service.ensure(20L)); verifyNoInteractions(mapper);
        doReturn(new AdminUserRespDTO().setId(20L).setNickname("新姓名").setDeptId(90L)).when(qualification).validateHomeroom(20L);
        var old = new DeliveryClassDO().setId(88L).setHomeroomUserId(20L).setClassKind("UNSCHEDULED").setVersion(2);
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(old);
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        service.ensure(20L);
        assertEquals(88L,old.getId()); assertEquals(90L,old.getDeptId()); assertEquals(3,old.getVersion());
        verify(mapper).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)); verify(mapper,never()).insert(any(DeliveryClassDO.class));
    }
}
