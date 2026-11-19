package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.AutoDeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.YearMonth;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoDeliveryClassServiceTest {
    @InjectMocks AutoDeliveryClassService service;
    @Mock DeliveryClassMapper classes;
    @Mock AutoDeliveryClassMapper autoClasses;
    @Mock DeliveryClassNumberService numbers;
    @Mock DeptApi depts;

    private final AdminUserRespDTO homeroom = new AdminUserRespDTO().setId(31L).setNickname("班主任甲").setDeptId(7L);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void reusesServingClassForSameProductMonthAndHomeroom() {
        var existing = autoClass(801L);
        when(autoClasses.selectByKey(1L, 401L, "2027-03", 31L)).thenReturn(existing);
        when(classes.selectByIdForUpdate(801L, 1L)).thenReturn(existing);

        assertSame(existing, service.ensure(401L, "产品A", "2027-03", homeroom));

        verify(classes, never()).insert(any(DeliveryClassDO.class));
    }

    @Test
    void createsNormalClassWithHomeroomDepartmentSnapshotWhenMissing() {
        when(numbers.next()).thenReturn("BJ1");
        when(depts.getDept(7L)).thenReturn(new DeptRespDTO().setId(7L).setName("销售一部"));

        var created = service.ensure(401L, "产品A", "2027-03", homeroom);

        var row = ArgumentCaptor.forClass(DeliveryClassDO.class);
        verify(classes).insert(row.capture());
        assertSame(row.getValue(), created);
        assertEquals("产品A 2027年3月", created.getClassName());
        assertEquals("NORMAL", created.getClassKind());
        assertFalse(created.getSystemClass());
        assertEquals(401L, created.getProductId());
        assertEquals("2027-03", created.getExamMonth());
        assertEquals("2027-03-01~2027-03-31", created.getExamScheduleSnapshot());
        assertEquals(31L, created.getHomeroomUserId());
        assertEquals(7L, created.getDeptId());
        assertEquals("销售一部", created.getDeptNameSnapshot());
        assertEquals("SERVING", created.getStatus());
    }

    @Test
    void staleSnapshotOfCompletedClassCreatesNewServingClass() {
        var stale = autoClass(801L);
        when(autoClasses.selectByKey(1L, 401L, "2027-03", 31L)).thenReturn(stale);
        when(classes.selectByIdForUpdate(801L, 1L)).thenReturn(autoClass(801L).setStatus("COMPLETED"));
        when(numbers.next()).thenReturn("BJ2");

        var created = service.ensure(401L, "产品A", "2027-03", homeroom);

        assertNotSame(stale, created);
        verify(classes).insert(created);
    }

    @Test
    void concurrentCreatorWinsAndLoserAdoptsCommittedClass() {
        var winner = autoClass(802L);
        when(numbers.next()).thenReturn("BJ3");
        when(classes.insert(any(DeliveryClassDO.class))).thenThrow(new DuplicateKeyException("uk_delivery_auto_class"));
        when(autoClasses.selectByKeyForUpdate(1L, 401L, "2027-03", 31L)).thenReturn(winner);

        assertSame(winner, service.ensure(401L, "产品A", "2027-03", homeroom));

        verify(classes, times(1)).insert(any(DeliveryClassDO.class));
    }

    @Test
    void classNumberCollisionRetriesWithNewNumber() {
        when(numbers.next()).thenReturn("BJ4", "BJ5");
        when(classes.insert(any(DeliveryClassDO.class)))
                .thenThrow(new DuplicateKeyException("uk_delivery_class_no")).thenReturn(1);

        var created = service.ensure(401L, "产品A", "2027-03", homeroom);

        assertEquals("BJ5", created.getClassNo());
        verify(classes, times(2)).insert(any(DeliveryClassDO.class));
    }

    @Test
    void exhaustedNumberRetriesReportVersionConflict() {
        when(numbers.next()).thenReturn("BJ6");
        when(classes.insert(any(DeliveryClassDO.class))).thenThrow(new DuplicateKeyException("uk_delivery_class_no"));

        var error = assertThrows(ServiceException.class, () -> service.ensure(401L, "产品A", "2027-03", homeroom));

        assertEquals(DELIVERY_CLASS_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void classNameKeepsMonthSuffixWithinColumnLimit() {
        String name = AutoDeliveryClassService.className("长".repeat(120), YearMonth.of(2027, 12));
        assertEquals(100, name.length());
        assertTrue(name.endsWith(" 2027年12月"));
    }

    private static DeliveryClassDO autoClass(Long id) {
        return new DeliveryClassDO().setId(id).setClassKind("NORMAL").setStatus("SERVING").setProductId(401L)
                .setExamMonth("2027-03").setHomeroomUserId(31L);
    }
}
