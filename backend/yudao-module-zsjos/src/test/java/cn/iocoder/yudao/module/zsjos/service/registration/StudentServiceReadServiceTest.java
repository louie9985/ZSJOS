package cn.iocoder.yudao.module.zsjos.service.registration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StudentServiceReadServiceTest {
    @InjectMocks StudentServiceReadService service;
    @Mock StudentServicePageMapper pageMapper;
    @Mock ServiceRelationMapper relationMapper;
    @Mock MyStudentServiceImpl projection;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi userApi;
    @Mock DeliveryClassScopeService classScope;
    @Mock BusinessReadScopeService readScope;
    @Mock AdvancedFilterService filters;
    @Mock StudentServiceSummaryReader summaryReader;
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void after(){TenantContextHolder.clear();}
    @Test void twoCoursesOfSamePersonRemainTwoRowsAndDatabaseTotal(){
        var a=new ServiceRelationDO();a.setId(11L);a.setPersonId(9L);
        var b=new ServiceRelationDO();b.setId(12L);b.setPersonId(9L);
        when(pageMapper.count(any())).thenReturn(2L);when(pageMapper.page(any())).thenReturn(List.of(a,b));
        for(var r:List.of(a,b)){var p=new MyStudentRespVO();p.setPersonId(9L);
            var v=new MyStudentRespVO.ServiceVO();v.setServiceRelationId(r.getId());p.setServices(List.of(v));
            when(projection.convert(7L,9L,List.of(r))).thenReturn(p);}
        var result=service.page(7L,new StudentServicePageReqVO());
        assertEquals(2,result.getTotal());assertEquals(List.of(11L,12L),result.getList().stream().map(StudentServiceRespVO::getServiceRelationId).toList());
        assertTrue(result.getList().stream().allMatch(r->r.getPersonId()==9L));
    }
    @Test void currentServicePredicatesAreBoundBeforePagination(){
        var req=new StudentServicePageReqVO();req.setClassId(31L);req.setPersonId(9L);req.setPageNo(2);req.setPageSize(10);
        var q=service.query(7L,req);assertTrue(q.getWhereSql().contains("sr.class_id="));
        assertEquals(31L,q.getParameters().get("classId"));assertEquals(10L,q.getParameters().get("offset"));
        assertEquals(1L,q.getParameters().get("tenantId"));assertEquals(7L,q.getParameters().get("subject"));
        assertTrue(q.getWhereSql().contains("sr.acceptance_status"));
    }
    @Test void explicitAllRequiresExistingScopeResolver(){
        var req=new StudentServicePageReqVO();req.setReadScope("ALL");
        when(readScope.resolve("ALL",null,7L)).thenThrow(new SecurityException());
        assertThrows(SecurityException.class,()->service.page(7L,req));verifyNoInteractions(pageMapper);
    }
    @Test void invisibleSiblingCannotBeReadThroughPerson(){
        var r=new ServiceRelationDO();r.setId(12L);r.setPersonId(9L);when(relationMapper.selectById(12L)).thenReturn(r);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.detail(7L,12L));
        verifyNoInteractions(projection);
    }
    @Test void managerScopeUsesServiceOwnerAndBoundDepartmentMembers(){
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(classScope.resolve(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of(31L)));
        when(userApi.getUserListByDeptIds(Set.of(31L))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(8L)));
        var q=service.query(7L,new StudentServicePageReqVO());
        assertTrue(q.getWhereSql().contains("sr.owner_user_id IN"));
        assertFalse(q.getWhereSql().contains("sr.content_director_user_id"));
        assertEquals(8L,q.getParameters().get("owner1"));
    }
}
