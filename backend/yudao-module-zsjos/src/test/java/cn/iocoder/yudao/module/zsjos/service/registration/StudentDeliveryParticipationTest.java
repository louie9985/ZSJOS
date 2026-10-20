package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentDeliveryParticipationTest {
    private final ServiceRelationMapper relations = mock(ServiceRelationMapper.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private ServiceRelationDO relation(long id, boolean teaching) {
        var r = new ServiceRelationDO(); r.setId(id); r.setPersonId(9L); r.setTeachingDelivery(teaching);
        r.setStatus("active"); r.setAcceptanceStatus("accepted"); r.setContentDirectorUserId(7L); r.setOperatorUserId(8L);
        return r;
    }
    @Test void mediaOnlyRelationRetainsDirectorOperatorActionsAndRejectsTeachingCommands() {
        var provider = new StudentServiceObjectPermissionProvider();
        ReflectionTestUtils.setField(provider,"relationMapper",relations);
        ReflectionTestUtils.setField(provider,"permissionApi",permissions);
        var r=relation(11,false);r.setOwnerUserId(7L);
        when(relations.selectById(11L)).thenReturn(r);
        for(String action:List.of("read","create-account","director-precheck","director-interview"))
            assertTrue(provider.hasPermission(11L,action,7L),action);
        assertTrue(provider.hasPermission(11L,"create-account",8L));
        when(permissions.hasAnyPermissions(7L,cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.PERMISSION_DIRECTOR_OPERATOR_ASSIGN)).thenReturn(true);
        assertTrue(provider.hasPermission(11L,"assign",7L));
        assertFalse(provider.hasPermission(11L,"create-account",99L));
        for(String action:List.of("accept","contact","delivery-stage","update-basic-info","class-transfer","direct-transfer","update-exam-date"))
            assertFalse(provider.hasPermission(11L,action,7L),action);
        r.setTeachingDelivery(true);
        assertTrue(provider.hasPermission(11L,"contact",7L));
    }
    @Test void deliveryDetailsRejectMediaOnlyBeforeProjectionEvenForTenantReader() {
        var service = new StudentServiceReadService();
        ReflectionTestUtils.setField(service,"relationMapper",relations);
        var r=relation(11,false);when(relations.selectById(11L)).thenReturn(r);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.detail(7L,11L));
        var legacy=new MyStudentServiceImpl();
        ReflectionTestUtils.setField(legacy,"relationMapper",relations);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->legacy.getMyStudentByService(7L,11L));
    }
    @Test void mixedPersonDeliveryDetailProjectsOnlyTeachingWhileMediaDetailRetainsBoth() {
        var projection = new MyStudentServiceImpl();var people=mock(PersonMapper.class);
        ReflectionTestUtils.setField(projection,"relationMapper",relations);
        ReflectionTestUtils.setField(projection,"personMapper",people);
        ReflectionTestUtils.setField(projection,"permissionApi",permissions);
        var person = new PersonDO();person.setId(9L);person.setPersonNo("XY-fixture");
        when(people.selectById(9L)).thenReturn(person);
        var a=relation(11,false);var b=relation(12,true);
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
        when(relations.selectTenantReadByPersonIds(List.of(9L),null)).thenReturn(List.of(a,b));
        when(people.existsMediaStudent(9L)).thenReturn(true);
        when(relations.selectMediaReadByPersonIds(List.of(9L),null)).thenReturn(List.of(a,b));
        assertEquals(List.of(12L),projection.getMyStudent(7L,9L).getServices().stream().map(MyStudentRespVO.ServiceVO::getServiceRelationId).toList());
        assertEquals(List.of(11L,12L),projection.getMediaStudent(7L,9L).getServices().stream().map(MyStudentRespVO.ServiceVO::getServiceRelationId).toList());
    }
    @Test void deliveryCountAndPageShareExplicitPredicateIncludingFullReaders() {
        var service=new StudentServiceReadService();
        ReflectionTestUtils.setField(service,"permissionApi",permissions);
        ReflectionTestUtils.setField(service,"filters",mock(cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService.class));
        var readScope=mock(cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService.class);
        ReflectionTestUtils.setField(service,"readScope",readScope);
        var req=new StudentServicePageReqVO();req.setReadScope("ALL");when(readScope.resolve("ALL",null,7L)).thenReturn(null);
        var query=service.query(7L,req);
        assertTrue(query.getWhereSql().contains("sr.teaching_delivery=1"));
        assertTrue(StudentServicePageSql.count(Map.of("query",query)).contains("sr.teaching_delivery=1"));
        assertTrue(StudentServicePageSql.page(Map.of("query",query)).contains("sr.teaching_delivery=1"));
    }
}
