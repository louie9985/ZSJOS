package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryClassSalesBoundaryTest {
    @InjectMocks DeliveryClassObjectPermissionProvider provider;
    @Mock DeliveryClassMapper mapper;
    @Mock PermissionApi permissions;
    @Mock DeliveryClassScopeService scope;

    /** Sales classes follow the head teacher's current department, not the stored class department snapshot. */
    @Test void salesReadFollowsHeadTeacherAndOwnClassStaysReadable() {
        var colleague = new DeliveryClassDO().setDeptId(99L).setHomeroomUserId(2L).setClassKind("NORMAL");
        var own = new DeliveryClassDO().setDeptId(null).setHomeroomUserId(1L).setClassKind("NORMAL");
        var other = new DeliveryClassDO().setDeptId(10L).setHomeroomUserId(3L).setClassKind("NORMAL");
        when(scope.salesHomerooms(1L)).thenReturn(java.util.Optional.of(Set.of(1L, 2L)));
        when(permissions.hasAnyPermissions(1L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_ALL,
                DeliveryClassService.PERMISSION_QUERY_MANAGED, DeliveryClassService.PERMISSION_QUERY_MY)).thenReturn(true);
        assertTrue(provider.hasPermission(own, "read", 1L), "own class readable even with SELF scope and no dept snapshot");
        assertFalse(provider.hasPermission(other, "read", 1L), "head teacher outside the readable sales users");
        assertFalse(provider.hasPermission(colleague, "read", 1L), "colleague class needs a managed/query capability");
        when(permissions.hasAnyPermissions(1L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_ALL,
                DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        assertTrue(provider.hasPermission(colleague, "read", 1L), "stale dept snapshot does not hide a moved head teacher's class");
    }
    @Test void queryAllListAndCountApplyHeadTeacherBoundaryBeforePagination() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixture"), DeliveryClassDO.class);
        var service = new DeliveryClassServiceImpl();
        var realMapper = mock(DeliveryClassMapper.class, CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "mapper", realMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "permissionApi", permissions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scopeService", scope);
        when(scope.salesHomerooms(1L)).thenReturn(java.util.Optional.of(Set.of(1L, 5L)));
        var query = service.scopedQuery(1L, new cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO(), true);
        assertTrue(query.getSqlSegment().contains("homeroom_user_id IN"));
        assertTrue(query.getParamNameValuePairs().containsValue(5L));
    }
    @Test void salesOptionsFilterHeadTeachersBeforeQuery() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixture-options"), DeliveryClassDO.class);
        var service = new DeliveryClassServiceImpl();
        var mapper = mock(DeliveryClassMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "mapper", mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "permissionApi", permissions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scopeService", scope);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminUserApi", mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class));
        when(scope.salesTransferHomerooms(1L)).thenReturn(java.util.Optional.of(Set.of(1L)));
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of());
        assertTrue(service.options(null, false, 1L).isEmpty());
        var query = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("homeroom_user_id IN"));
    }
    @Test void missingQualificationHasDistinctActionableError() {
        var service = new DeliveryClassServiceImpl();
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminUserApi", users);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "permissionApi", permissions);
        when(users.getUser(1L)).thenReturn(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(1L).setStatus(0));
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.validateHomeroom(1L));
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED.getCode(), error.getCode());
        assertEquals("缺少销售业务资格，请联系管理员配置", error.getMessage());
    }
}
