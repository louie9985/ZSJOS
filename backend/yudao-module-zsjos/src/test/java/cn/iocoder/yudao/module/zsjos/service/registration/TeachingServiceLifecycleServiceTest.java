package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.TeachingServiceCommandReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeachingServiceLifecycleServiceTest {
    private TeachingServiceLifecycleService service;
    private ServiceRelationMapper relations;
    private DeliveryClassMapper classes;
    private TeachingServiceEventMapper events;
    private StudentContactExtensionMapper extensions;
    private ClassTransferRequestMapper transfers;
    private BusinessTaskCommandService tasks;
    private BpmProcessInstanceApi bpm;
    private PermissionApi permissions;
    private DeliveryClassScopeService scope;
    private TeachingServiceCloseNotifyPublisher closeNotifications;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var data = new DriverManagerDataSource("jdbc:h2:mem:teaching_close_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        dataSource=data; jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE services(id BIGINT PRIMARY KEY, closed BOOLEAN, class_id BIGINT, previous_class_id BIGINT, owner_id BIGINT, version INT, restored BOOLEAN)");
        jdbc.execute("INSERT INTO services VALUES(1,FALSE,10,NULL,20,3,FALSE),(2,FALSE,11,NULL,20,3,FALSE)");
        jdbc.execute("CREATE TABLE tasks(id BIGINT PRIMARY KEY, relation_id BIGINT, state VARCHAR(30))");
        jdbc.execute("INSERT INTO tasks VALUES(1,1,'pending'),(2,2,'pending')");
        jdbc.execute("CREATE TABLE notifications(event_key VARCHAR(100) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE events(request_key VARCHAR(100) PRIMARY KEY, fingerprint VARCHAR(100), action VARCHAR(30))");
        relations = mock(ServiceRelationMapper.class); classes = mock(DeliveryClassMapper.class); events = mock(TeachingServiceEventMapper.class);
        extensions = mock(StudentContactExtensionMapper.class); transfers = mock(ClassTransferRequestMapper.class);
        tasks = mock(BusinessTaskCommandService.class); bpm = mock(BpmProcessInstanceApi.class); permissions = mock(PermissionApi.class);
        scope = mock(DeliveryClassScopeService.class);
        var users = mock(AdminUserApi.class); var user = new AdminUserRespDTO(); user.setId(20L); user.setDeptId(80L); user.setNickname("规划师");
        when(users.getUser(anyLong())).thenReturn(user);
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
        when(scope.containsRead(anyLong(),eq(80L))).thenReturn(true);
        when(relations.selectByIdForUpdate(anyLong(),eq(1L))).thenAnswer(call -> row(call.getArgument(0)));
        when(relations.selectById(anyLong())).thenAnswer(call -> row(call.getArgument(0)));
        when(relations.updateById(any(ServiceRelationDO.class))).thenAnswer(call -> {
            ServiceRelationDO r=call.getArgument(0);
            return jdbc.update("UPDATE services SET closed=?,class_id=?,previous_class_id=?,owner_id=?,version=?,restored=? WHERE id=?",
                r.getTeachingClosed(),r.getClassId(),r.getTeachingPreviousClassId(),r.getOwnerUserId(),r.getVersion(),r.getTeachingRestored(),r.getId());
        });
        when(events.byKey(anyString())).thenAnswer(call -> jdbc.query("SELECT * FROM events WHERE request_key=?",(rs,n) -> {
            var e=new TeachingServiceEventDO();e.setFingerprint(rs.getString("fingerprint"));return e;
        },call.getArgument(0,String.class)).stream().findFirst().orElse(null));
        when(events.insert(any(TeachingServiceEventDO.class))).thenAnswer(call -> {
            TeachingServiceEventDO e=call.getArgument(0);return jdbc.update("INSERT INTO events VALUES(?,?,?)",e.getIdempotencyKey(),e.getFingerprint(),e.getAction());
        });
        when(extensions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        when(tasks.cancelTeachingService(anyLong(),any(),anyString())).thenAnswer(call -> jdbc.update("UPDATE tasks SET state='cancelled' WHERE relation_id=?",call.getArgument(0,Long.class)));
        var normal=new DeliveryClassDO().setId(10L).setSystemClass(false).setClassKind("NORMAL").setHomeroomUserId(20L).setDeptId(80L).setStatus("SERVING");
        when(classes.selectByIdForUpdate(eq(10L),eq(1L))).thenReturn(normal);
        when(classes.selectById(anyLong())).thenReturn(normal);
        var exception = mock(ExceptionDeliveryClassService.class);
        when(exception.ensure(20L)).thenReturn(new DeliveryClassDO().setId(99L).setClassKind("EXCEPTION").setSystemClass(true));
        closeNotifications=mock(TeachingServiceCloseNotifyPublisher.class);
        doAnswer(call -> { ServiceRelationDO r=call.getArgument(0);jdbc.update("INSERT INTO notifications VALUES(?)","teaching-service-closed:"+r.getId()+":v"+r.getVersion());return null; })
            .when(closeNotifications).publish(any(),anyLong(),anyString());
        var target=new TeachingServiceLifecycleService();
        Map.ofEntries(Map.entry("relations",relations),Map.entry("classes",classes),Map.entry("events",events),
            Map.entry("extensions",extensions),Map.entry("transfers",transfers),Map.entry("tasks",tasks),Map.entry("processes",bpm),
            Map.entry("permissions",permissions),Map.entry("scope",scope),Map.entry("users",users),Map.entry("exceptionClasses",exception),
            Map.entry("closeNotifications",closeNotifications),Map.entry("classService",mock(DeliveryClassService.class))).forEach((name,value) -> ReflectionTestUtils.setField(target,name,value));
        var proxy=new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));
        service=(TeachingServiceLifecycleService)proxy.getProxy();
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private ServiceRelationDO row(Long id) {
        return jdbc.query("SELECT * FROM services WHERE id=?",(rs,n) -> {
            var r=new ServiceRelationDO();r.setId(rs.getLong("id"));r.setTenantId(1L);r.setTeachingDelivery(true);r.setStatus("active");
            r.setTeachingClosed(rs.getBoolean("closed"));r.setClassId(rs.getLong("class_id"));r.setOwnerUserId(rs.getLong("owner_id"));
            r.setVersion(rs.getInt("version"));r.setTeachingRestored(rs.getBoolean("restored"));
            r.setTeachingPreviousClassId(rs.getObject("previous_class_id",Long.class));return r;
        },id).stream().findFirst().orElse(null);
    }
    private TeachingServiceCommandReqVO command(int version,String key) {
        var r=new TeachingServiceCommandReqVO();r.setVersion(version);r.setReason("确认关闭或恢复");r.setIdempotencyKey(key);return r;
    }
    @Test void restoredServiceCanNotifyAgainOnANewCloseRound() {
        service.close(1L,20L,command(3,"close-round-1"));service.restore(1L,30L,command(4,"restore-round"));
        service.close(1L,20L,command(5,"close-round-2"));
        assertEquals(List.of("teaching-service-closed:1:v4","teaching-service-closed:1:v6"),
            jdbc.queryForList("SELECT event_key FROM notifications ORDER BY event_key",String.class));
    }

    @Test void closePublishesOnceAndRestoreDoesNotSendCloseNotification() {
        var req=command(3,"notify-close");service.close(1L,20L,req);service.close(1L,20L,req);
        service.restore(1L,30L,command(4,"notify-restore"));
        verify(closeNotifications,times(1)).publish(any(),eq(20L),eq(req.getReason()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class));
    }
    @Test void notificationOutboxFailureRollsBackCloseHistoryAndTasks() {
        doAnswer(call -> { jdbc.update("INSERT INTO notifications VALUES('controlled-failure')");throw new IllegalStateException("outbox unavailable"); })
            .when(closeNotifications).publish(any(),anyLong(),anyString());
        assertThrows(RuntimeException.class,() -> service.close(1L,20L,command(3,"notify-failure")));
        assertFalse(row(1L).getTeachingClosed());
        assertEquals("pending",jdbc.queryForObject("SELECT state FROM tasks WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class));
    }

    @Test void closeMovesOnlyCurrentCourseAndCancelsWithoutDeleting() {
        service.close(1L,20L,command(3,"c1"));
        assertTrue(row(1L).getTeachingClosed());assertEquals(99L,row(1L).getClassId());assertEquals(10L,row(1L).getTeachingPreviousClassId());
        assertEquals("active",row(1L).getStatus());assertFalse(row(2L).getTeachingClosed());
        assertEquals(List.of("cancelled","pending"),jdbc.queryForList("SELECT state FROM tasks ORDER BY id",String.class));
    }
    @Test void restoreKeepsCancelledTasksAndOriginalStageLifecycle() {
        service.close(1L,20L,command(3,"c1"));service.restore(1L,30L,command(4,"r1"));
        assertFalse(row(1L).getTeachingClosed());assertTrue(row(1L).getTeachingRestored());assertEquals(10L,row(1L).getClassId());
        assertEquals("cancelled",jdbc.queryForObject("SELECT state FROM tasks WHERE id=1",String.class));
        verify(tasks,never()).create(any());
    }
    @Test void restoreAcceptsOriginalUnscheduledClassWithoutRecreatingTasks() {
        var target = new DeliveryClassDO().setId(10L).setSystemClass(true).setClassKind("UNSCHEDULED")
            .setHomeroomUserId(20L).setDeptId(80L).setStatus("SERVING");
        when(classes.selectByIdForUpdate(10L,1L)).thenReturn(target);
        service.close(1L,20L,command(3,"close-default"));
        service.restore(1L,30L,command(4,"restore-default"));
        assertEquals(10L,row(1L).getClassId()); assertEquals(20L,row(1L).getOwnerUserId());
        assertFalse(row(1L).getTeachingClosed()); verify(tasks,never()).create(any());
    }
    @Test void sameKeyReplaysButDifferentPayloadFails() {
        var req=command(3,"c1");service.close(1L,20L,req);service.close(1L,20L,req);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
        req.setReason("另一个原因");assertThrows(RuntimeException.class,() -> service.close(1L,20L,req));
    }
    @Test void staleVersionAndOtherOwnerCannotMutate() {
        assertThrows(RuntimeException.class,() -> service.close(1L,20L,command(1,"stale")));
        assertThrows(RuntimeException.class,() -> service.close(1L,21L,command(3,"other")));
        assertFalse(row(1L).getTeachingClosed());verifyNoInteractions(tasks);
    }
    @Test void disabledPermissionCannotMutateEvenWithoutControllerProxy() {
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(false);
        assertThrows(RuntimeException.class,() -> service.close(1L,20L,command(3,"denied")));
        assertFalse(row(1L).getTeachingClosed());
    }
    @Test void invalidTargetAndOutOfScopeRestoreKeepServiceClosed() {
        service.close(1L,20L,command(3,"c1"));var req=command(4,"r1");req.setTargetClassId(123L);
        assertThrows(RuntimeException.class,() -> service.restore(1L,30L,req));
        when(scope.containsRead(30L,80L)).thenReturn(false);
        assertThrows(RuntimeException.class,() -> service.restore(1L,30L,command(4,"r2")));assertTrue(row(1L).getTeachingClosed());
    }
    @Test void actualFlowableCancellationRollsBackWithBusinessTransaction() {
        var config=new org.flowable.spring.SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);config.setTransactionManager(new DataSourceTransactionManager(dataSource));
        config.setDatabaseSchemaUpdate("true");config.setAsyncExecutorActivate(false);
        var engine=config.buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addString("teaching.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="teaching-test">
                  <process id="teachingTest" isExecutable="true"><startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/><userTask id="review" name="review"/>
                    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
            var instance=engine.getRuntimeService().startProcessInstanceByKey("teachingTest");
            var extension=new StudentContactExtensionDO();extension.setId(44L);extension.setVersion(0);extension.setProcessInstanceId(instance.getId());
            when(extensions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(extension));
            when(extensions.transitionPending(anyLong(),anyInt(),anyString(),anyString(),isNull(),any())).thenReturn(1);
            doAnswer(call -> { engine.getRuntimeService().deleteProcessInstance(call.getArgument(1,String.class),"service close");return null; })
                .when(bpm).terminateProcessInstanceByBusiness(anyLong(),anyString(),anyString(),anyString());
            doThrow(new IllegalStateException("controlled task cancellation failure")).when(tasks).cancelTeachingService(anyLong(),any(),anyString());
            assertThrows(RuntimeException.class,() -> service.close(1L,20L,command(3,"engine-rollback")));
            assertFalse(row(1L).getTeachingClosed());
            assertEquals(1,engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).count());
            assertEquals(1,engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).count());
        } finally { engine.close(); }
    }

    @Test void bpmFailureRollsBackRelationRequestTasksAndHistory() {
        var pending=new StudentContactExtensionDO();pending.setId(44L);pending.setVersion(0);pending.setProcessInstanceId("fixture-process");
        when(extensions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(pending));
        when(extensions.transitionPending(anyLong(),anyInt(),anyString(),anyString(),isNull(),any())).thenReturn(1);
        doThrow(new IllegalStateException("BPM unavailable")).when(bpm).terminateProcessInstanceByBusiness(anyLong(),anyString(),anyString(),anyString());
        assertThrows(RuntimeException.class,() -> service.close(1L,20L,command(3,"failure")));
        assertFalse(row(1L).getTeachingClosed());assertEquals(10L,row(1L).getClassId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events",Integer.class));
        assertEquals("pending",jdbc.queryForObject("SELECT state FROM tasks WHERE id=1",String.class));
    }
    @Test void cancelledClosedServiceNeverOffersRestoreToHistoricalReaders() {
        var cancelled=row(1L);cancelled.setTeachingClosed(true);cancelled.setStatus("cancelled");
        when(relations.selectById(1L)).thenReturn(cancelled);
        assertFalse(service.detail(1L,30L).getAvailableActions().contains("RESTORE_SERVICE"));
    }
    @Test void commandUniqueConstraintIsTranslatedAndEntireTransactionRollsBack() {
        when(events.insert(any(TeachingServiceEventDO.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry for key 'uk_teaching_event_key'"));
        var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.close(1L,20L,command(3,"parallel-key")));
        assertEquals(TeachingServiceLifecycle.KEY_CONFLICT.getCode(),error.getCode());
        assertFalse(row(1L).getTeachingClosed());assertEquals("pending",jdbc.queryForObject("SELECT state FROM tasks WHERE id=1",String.class));
        assertFalse(TeachingServiceLifecycleService.isCommandKeyConflict(new org.springframework.dao.DuplicateKeyException("PRIMARY")));
    }

}
