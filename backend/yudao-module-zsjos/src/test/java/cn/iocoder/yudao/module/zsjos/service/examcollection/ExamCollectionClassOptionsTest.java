package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ExamCollectionClassOptionsTest {
    ExamCollectionRosterService service;
    DeliveryClassMapper classes;
    DeliveryClassGroupMapper groups;
    PermissionApi permissions;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); service = new ExamCollectionRosterService();
        classes = mock(DeliveryClassMapper.class); groups = mock(DeliveryClassGroupMapper.class); permissions = mock(PermissionApi.class);
        ReflectionTestUtils.setField(service,"classes",classes); ReflectionTestUtils.setField(service,"groups",groups);
        ReflectionTestUtils.setField(service,"permissions",permissions);
        when(permissions.hasAnyPermissions(null,ExamCollectionConstants.QUERY)).thenReturn(true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    DeliveryClassDO clazz(long id, Long groupId) {
        var row = new DeliveryClassDO().setId(id).setGroupId(groupId).setClassName("同名小班")
                .setClassKind("NORMAL").setSystemClass(false).setStatus("SERVING").setHomeroomUserId(7L);
        row.setTenantId(1L); return row;
    }
    DeliveryClassGroupDO group(long id, int day) {
        var row = new DeliveryClassGroupDO(); row.setId(id); row.setName("同名班级组");row.setTenantId(1L);
        row.setCreateTime(LocalDateTime.of(2026,11,day,0,0));return row;
    }
    @Test void authoritativeGroupsAreBatchedAndOrderedBeforeIndependentClasses() {
        var pending = clazz(40,null).setClassKind("PENDING");
        when(classes.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(pending,clazz(30,null),clazz(22,2L),clazz(21,1L),clazz(20,2L)));
        when(groups.selectByIds(anyCollection())).thenReturn(List.of(group(1,1),group(2,2)));
        var result = service.classes();
        assertEquals(List.of(22L,20L,21L,30L),result.stream().map(r->r.id()).toList());
        assertEquals(2L,result.getFirst().groupId());assertEquals("同名班级组",result.getFirst().groupName());
        assertEquals(1L,result.get(2).groupId());assertNull(result.getLast().groupId());assertNull(result.getLast().groupName());
        verify(groups,times(1)).selectByIds(argThat(ids->ids.size()==2&&ids.containsAll(List.of(1L,2L))));
    }
    @Test void emptyAndUngroupedListsNeverInventGroups() {
        when(classes.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(),List.of(clazz(4,null)));
        assertTrue(service.classes().isEmpty());assertNull(service.classes().getFirst().groupName());verifyNoInteractions(groups);
    }
    @Test void unavailableGroupOrDeniedMenuCannotExposeOtherTenantMetadata() {
        when(classes.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(clazz(4,2L)));
        var foreign = group(2,2);foreign.setTenantId(2L);when(groups.selectByIds(anyCollection())).thenReturn(List.of(foreign));
        assertThrows(ServiceException.class,()->service.classes());
        clearInvocations(classes,groups);when(permissions.hasAnyPermissions(null,ExamCollectionConstants.QUERY)).thenReturn(false);
        assertThrows(ServiceException.class,()->service.classes());verifyNoInteractions(classes,groups);
    }
    @Test void adjustmentOptionsRetainCompletedAndDeletedSelectedClassesAndDisableUnavailablePlanner() {
        var tasks = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper.class);
        var members = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper.class);
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        ReflectionTestUtils.setField(service,"tasks",tasks);ReflectionTestUtils.setField(service,"members",members);
        ReflectionTestUtils.setField(service,"users",users);ReflectionTestUtils.setField(service,"objects",mock(ExamCollectionPermissionProvider.class));
        var task = new cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionTaskDO();
        task.setId(1L);task.setTenantId(1L);task.setClassIdsJson("[4,5]");when(tasks.selectById(1L)).thenReturn(task);
        when(classes.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(clazz(6,null)));
        when(classes.selectByIds(anyCollection())).thenReturn(List.of(clazz(4,null).setClassName("历史完成班").setStatus("COMPLETED")));
        var frozen = new cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionMemberDO();frozen.setClassId(5L);frozen.setClassNameSnapshot("原冻结班名");
        when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(frozen));
        var planner = new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO();planner.setStatus(1);
        when(users.getUserMap(anyCollection())).thenReturn(java.util.Map.of(7L,planner));
        var options = service.classOptions(1L);
        assertEquals(List.of(6L,4L,5L),options.stream().map(o->o.id()).toList());
        assertTrue(options.stream().noneMatch(o->o.selectable()));
        assertEquals("历史完成班",options.get(1).name());assertEquals("原冻结班名",options.get(2).name());
    }
}
