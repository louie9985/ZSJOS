package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real service transaction annotation, lock lifetime and outbox rollback with JDBC fixtures. */
class FeedbackUrgeTransactionTest {
    private FeedbackApprovalService service;
    private JdbcTemplate jdbc;
    private final AtomicBoolean failNotification = new AtomicBoolean();
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:urge-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE feedback(id bigint primary key); INSERT INTO feedback VALUES(1)");
        jdbc.execute("CREATE TABLE approval_round(id bigint primary key,last_urged_at timestamp); INSERT INTO approval_round VALUES(2,null)");
        jdbc.execute("CREATE TABLE history(request_key varchar(128) primary key,fingerprint varchar(128),operation varchar(30))");
        jdbc.execute("CREATE TABLE outbox(event_key varchar(256) primary key)");
        var target=new FeedbackApprovalService();
        var feedback=mock(FeedbackMapper.class);var rounds=mock(FeedbackRoundMapper.class);var history=mock(WorkOrderHistoryMapper.class);
        var bpm=mock(BpmProcessProgressApi.class);var permissions=mock(PermissionApi.class);var users=mock(AdminUserApi.class);var notify=mock(NotifyBusinessEventApi.class);
        ReflectionTestUtils.setField(target,"feedbackMapper",feedback);ReflectionTestUtils.setField(target,"roundMapper",rounds);
        ReflectionTestUtils.setField(target,"historyMapper",history);ReflectionTestUtils.setField(target,"progressApi",bpm);
        ReflectionTestUtils.setField(target,"permissionApi",permissions);ReflectionTestUtils.setField(target,"userApi",users);ReflectionTestUtils.setField(target,"notifyApi",notify);
        when(feedback.selectByIdForUpdate(1L)).thenAnswer(call->{
            jdbc.queryForObject("SELECT id FROM feedback WHERE id=1 FOR UPDATE",Long.class);
            var row=new FeedbackDO();row.setId(1L);row.setWorkOrderId(1L);row.setSubmitterUserId(11L);row.setSubmitterSubjectType("ADMIN");
            row.setStatus("APPROVING");row.setFeedbackType("REQUIREMENT");row.setApprovalRoundNo(2);row.setVersion(1);row.setProcessInstanceId("p");return row;
        });
        when(rounds.selectByFeedbackId(1L)).thenAnswer(call->jdbc.query("SELECT last_urged_at FROM approval_round WHERE id=2",(rs,n)->{
            var round=new FeedbackRoundDO();round.setId(2L);round.setRoundNo(2);round.setProcessInstanceId("p");
            var timestamp=rs.getTimestamp(1);round.setLastUrgedAt(timestamp==null?null:timestamp.toLocalDateTime());return round;
        }));
        when(rounds.updateById(any(FeedbackRoundDO.class))).thenAnswer(call->jdbc.update("UPDATE approval_round SET last_urged_at=? WHERE id=2",call.getArgument(0,FeedbackRoundDO.class).getLastUrgedAt()));
        when(history.selectByOrderAndKey(eq(1L),anyString())).thenAnswer(call->{
            var rows=jdbc.query("SELECT fingerprint,operation FROM history WHERE request_key=?",(rs,n)->{
                var h=new WorkOrderHistoryDO();h.setRequestFingerprint(rs.getString(1));h.setOperation(rs.getString(2));return h;
            },call.getArgument(1,String.class));return rows.isEmpty()?null:rows.getFirst();
        });
        when(history.insert(any(WorkOrderHistoryDO.class))).thenAnswer(call->{var h=call.getArgument(0,WorkOrderHistoryDO.class);
            return jdbc.update("INSERT INTO history VALUES(?,?,?)",h.getIdempotencyKey(),h.getRequestFingerprint(),h.getOperation());});
        when(permissions.hasAnyPermissions(11L,FeedbackApprovalService.URGE_PERMISSION)).thenReturn(true);
        var task=new BpmProcessProgressDTO.PendingTask("t","审核","review",22L,"审批人",null);
        when(bpm.getProgress("p",11L)).thenReturn(new BpmProcessProgressDTO(1,List.of(),List.of(task)));
        when(bpm.getCurrentTasks(Set.of("p"))).thenReturn(Map.of("p",List.of(task)));
        var user=new AdminUserRespDTO();user.setId(22L);user.setStatus(0);when(users.getUserMap(Set.of(22L))).thenReturn(Map.of(22L,user));
        when(notify.publishDurable(any())).thenAnswer(call->{
            jdbc.update("INSERT INTO outbox VALUES(?)",call.getArgument(0,NotifyBusinessEvent.class).getSourceEventKey());
            if(failNotification.get())throw new IllegalStateException("injected outbox failure");return 1;
        });
        var proxy=new ProxyFactory(target);
        var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(new DataSourceTransactionManager(source));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());proxy.addAdvice(interceptor);
        service=(FeedbackApprovalService)proxy.getProxy();
    }
    private boolean urge(String key) {
        TenantContextHolder.setTenantId(1L);
        try { var request=new FeedbackActionVO.UrgeReq();request.setVersion(1);request.setRoundNo(2);request.setIdempotencyKey(key);service.urge(1L,request,11L);return true; }
        catch(cn.iocoder.yudao.framework.common.exception.ServiceException error){return false;}
        finally {TenantContextHolder.clear();}
    }
    @Test void concurrentDifferentRequestsOnlyOneConsumesCooldown() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<Boolean> first=()->{barrier.await();return urge("first");};Callable<Boolean> second=()->{barrier.await();return urge("second");};
            var outcomes=pool.invokeAll(List.of(first,second));
            assertEquals(1,outcomes.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM history",Integer.class));
    }
    @Test void notificationFailureRollsBackOutboxAndCooldownThenSameKeyCanRetry() {
        failNotification.set(true);assertThrows(IllegalStateException.class,()->urge("retry"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
        assertNull(jdbc.queryForObject("SELECT last_urged_at FROM approval_round",java.sql.Timestamp.class));
        failNotification.set(false);assertTrue(urge("retry"));assertTrue(urge("retry"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
    }
}
