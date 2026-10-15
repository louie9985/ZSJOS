package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.DeliveryClassController;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeliveryClassFiltersTest {
    @Test void projectsUseOnlyRecordedCategoriesAndPaths() {
        assertNull(DeliveryClassProjects.parse(null));
        assertNull(DeliveryClassProjects.parse("{}"));
        assertNull(DeliveryClassProjects.parse("invalid"));
        assertNull(DeliveryClassProjects.parse("{\"categoryId\":0}"));
        var project = DeliveryClassProjects.parse("{\"categoryId\":12,\"categoryName\":\"历史项目\",\"categoryPath\":[{\"name\":\"健康\"},{\"name\":\"历史项目\"}]}");
        assertEquals(12L, project.id()); assertEquals("健康 / 历史项目", project.path());
        assertEquals("项目名称未记录", DeliveryClassProjects.parse("{\"categoryId\":12}").name());
    }

    @Test void candidatesSharePersonalScopeAndNeverReadWriteCandidates() {
        var mapper = mock(DeliveryClassMapper.class);
        var query = new LambdaQueryWrapperX<DeliveryClassDO>();
        when(mapper.classQuery(any(), eq(Set.of()), eq(7L), eq(false), eq(false))).thenReturn(query);
        when(mapper.selectList(query)).thenReturn(List.of());
        var service = new DeliveryClassServiceImpl(); ReflectionTestUtils.setField(service, "mapper", mapper);
        var context = service.filterContext(7L, new DeliveryClassPageReqVO(), false);
        assertTrue(context.projects().isEmpty()); assertTrue(context.homerooms().isEmpty()); assertNull(context.pendingClass());
        verify(mapper).classQuery(any(), eq(Set.of()), eq(7L), eq(false), eq(false));
    }

    @Test void managedCandidatesPreserveDepartmentScopeAndPendingSeparately() {
        var mapper = mock(DeliveryClassMapper.class);
        var permissions = mock(PermissionApi.class);
        var scopes = mock(DeliveryClassScopeService.class);
        when(scopes.resolve(7L)).thenReturn(new DeliveryClassScopeService.Scope(false, Set.of(20L)));
        var query = new LambdaQueryWrapperX<DeliveryClassDO>();
        when(mapper.classQuery(any(), eq(Set.of(20L)), isNull(), eq(false), eq(true))).thenReturn(query);
        var row = new DeliveryClassDO().setId(10L).setSystemClass(false).setHomeroomUserId(8L).setHomeroomUserNameSnapshot("历史老师"); row.setTenantId(1L);
        var pending = new DeliveryClassDO().setId(1L).setSystemClass(true); pending.setTenantId(1L);
        when(mapper.selectList(query)).thenReturn(List.of(pending, row));
        when(mapper.selectProjectSnapshots(1L,List.of(10L))).thenReturn(List.of(
                Map.of("classId",10L,"snapshot","{\"categoryId\":12,\"categoryName\":\"项目甲\"}"),
                Map.of("classId",10L,"snapshot","{\"categoryId\":12,\"categoryName\":\"项目甲\"}"),
                Map.of("classId",10L,"snapshot","{\"categoryId\":13,\"categoryName\":\"项目乙\"}")));
        var service = new DeliveryClassServiceImpl();
        ReflectionTestUtils.setField(service,"mapper",mapper); ReflectionTestUtils.setField(service,"permissionApi",permissions); ReflectionTestUtils.setField(service,"scopeService",scopes);
        var context=service.filterContext(7L,new DeliveryClassPageReqVO(),true);
        assertEquals(2,context.projects().size()); assertEquals(8L,context.homerooms().getFirst().id()); assertEquals(1L,context.pendingClass().getId());
    }

    @Test void explicitReadScopeCannotBypassScopeResolver() {
        var scope = mock(BusinessReadScopeService.class);
        when(scope.resolve("ALL",null,7L)).thenThrow(new IllegalArgumentException("denied"));
        var service = new DeliveryClassServiceImpl(); ReflectionTestUtils.setField(service,"readScopeService",scope);
        var req = new DeliveryClassPageReqVO(); req.setReadScope("ALL");
        assertThrows(IllegalArgumentException.class,()->service.filterContext(7L,req,true));
    }

    @Test void dateValidationAndControllerPermissions() throws Exception {
        var req=new DeliveryClassPageReqVO(); req.setExamDateStart(LocalDate.of(2026,11,1)); req.setExamDateEnd(LocalDate.of(2026,10,31));
        assertFalse(req.isExamDateRangeValid()); req.setExamDateEnd(req.getExamDateStart()); assertTrue(req.isExamDateRangeValid());
        for (var pair : List.of(List.of("page","filterContext"),List.of("myPage","myFilterContext"))) {
            assertEquals(DeliveryClassController.class.getMethod(pair.get(0),DeliveryClassPageReqVO.class).getAnnotation(PreAuthorize.class).value(),
                    DeliveryClassController.class.getMethod(pair.get(1),DeliveryClassPageReqVO.class).getAnnotation(PreAuthorize.class).value());
        }
    }
}
