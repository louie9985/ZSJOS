package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeachingServiceCloseNotifyTest {
    private TeachingServiceCloseNotifyPublisher publisher;
    private NotifyBusinessEventApi notifications;
    private AdminUserApi users;
    private PermissionApi permissions;
    private StudentServiceObjectPermissionProvider objectPermissions;
    private DeptRespDTO department;
    private AdminUserRespDTO leader;
    private ServiceRelationDO relation;
    private final TeachingServiceCloseNotifySceneProvider provider = new TeachingServiceCloseNotifySceneProvider();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        publisher=new TeachingServiceCloseNotifyPublisher();notifications=mock(NotifyBusinessEventApi.class);
        users=mock(AdminUserApi.class);permissions=mock(PermissionApi.class);objectPermissions=mock(StudentServiceObjectPermissionProvider.class);
        var departments=mock(DeptApi.class);
        Map.of("notifications",notifications,"users",users,"departments",departments,"permissions",permissions,"servicePermissions",objectPermissions)
                .forEach((key,value) -> ReflectionTestUtils.setField(publisher,key,value));
        var planner=new AdminUserRespDTO();planner.setId(20L);planner.setDeptId(80L);planner.setNickname("学习规划师甲");
        leader=new AdminUserRespDTO();leader.setId(30L);leader.setStatus(0);
        department=new DeptRespDTO();department.setId(80L);department.setLeaderUserId(30L);
        when(users.getUser(20L)).thenReturn(planner);when(users.getUser(30L)).thenReturn(leader);
        when(departments.getDept(80L)).thenReturn(department);
        when(permissions.hasAnyPermissions(30L,"zsjos:student:query-my")).thenReturn(true);
        when(objectPermissions.hasPermission(10L,"read",30L)).thenReturn(true);
        relation=new ServiceRelationDO();relation.setId(10L);relation.setOwnerUserId(20L);relation.setVersion(4);
        relation.setStudentNameSnapshot("服务快照姓名");relation.setServiceSnapshot("{\"name\":\"营养师课程\"}");
        relation.setTeachingClosedAt(LocalDateTime.of(2026,10,24,12,30));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private NotifyBusinessEvent publish() {
        publisher.publish(relation,20L,"学员确认关闭");
        var event=ArgumentCaptor.forClass(NotifyBusinessEvent.class);verify(notifications).publish(event.capture());return event.getValue();
    }
    @Test void snapshotsConfiguredSupervisorAndBusinessContentWithServiceDeepLink() {
        var event=publish();
        assertEquals(1L,event.getTenantId());assertEquals("student_service",event.getBizType());assertEquals(10L,event.getBizId());
        assertEquals("teaching-service-closed:10:v4",event.getSourceEventKey());
        assertEquals(Set.of(NotifyRecipientDTO.admin(30L)),provider.resolveRecipients(event,Set.of(TeachingServiceCloseNotifySceneProvider.SUPERVISOR)));
        relation.setStudentNameSnapshot("后续更名");department.setLeaderUserId(40L);
        var variables=provider.resolveVariables(event,NotifyRecipientDTO.admin(30L));
        assertEquals("服务快照姓名",variables.get("student.name"));assertEquals("营养师课程",variables.get("course.name"));
        assertEquals("学习规划师甲",variables.get("planner.name"));assertEquals("学员确认关闭",variables.get("close.reason"));
        assertEquals("2026-10-24 12:30:00",variables.get("close.time"));
    }
    @Test void disabledSupervisorCannotBecomeRecipient() {
        leader.setStatus(1);var event=publish();assertNull(event.getPayload().get("supervisorUserId"));
        assertTrue(provider.resolveRecipients(event,Set.of(TeachingServiceCloseNotifySceneProvider.SUPERVISOR)).isEmpty());
    }
    @Test void selfAndMissingDepartmentLeaderHaveNoFallbackRecipient() {
        department.setLeaderUserId(20L);assertNull(publish().getPayload().get("supervisorUserId"));
        reset(notifications);department.setLeaderUserId(null);assertNull(publish().getPayload().get("supervisorUserId"));
    }
    @Test void supervisorMustHavePageAndExactServiceReadAccess() {
        when(permissions.hasAnyPermissions(30L,"zsjos:student:query-my")).thenReturn(false);
        assertNull(publish().getPayload().get("supervisorUserId"));
        reset(notifications);when(permissions.hasAnyPermissions(30L,"zsjos:student:query-my")).thenReturn(true);
        when(objectPermissions.hasPermission(10L,"read",30L)).thenReturn(false);
        assertNull(publish().getPayload().get("supervisorUserId"));
    }
    @Test void absentNamesRemainMissingAndRolesDoNotExpandToOthers() {
        relation.setStudentNameSnapshot(null);relation.setServiceSnapshot("broken-json");var event=publish();
        assertEquals("",event.getPayload().get("student.name"));assertEquals("",event.getPayload().get("course.name"));
        assertTrue(provider.resolveRecipients(event,Set.of("planner")).isEmpty());
        assertEquals(TeachingServiceCloseNotifySceneProvider.SCENE,provider.getScenes().getFirst().getCode());
    }
}
