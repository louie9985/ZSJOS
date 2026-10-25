package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MenuTaskSummaryServiceImplTest {
    public static int jsonValid(String value){
        if(value==null)return 0;
        try {cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(value,java.util.Map.class);return 1;}catch(RuntimeException invalid){return 0;}
    }
    public static String jsonExtract(String value,String path){
        if(value==null)return null;
        var object=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(value,java.util.Map.class);
        return object==null?null:java.util.Objects.toString(object.get(path.substring(2)),null);
    }
    public static String jsonUnquote(String value){return value;}
    @Test void sameWeeklyDiagnosisDeadlineCountsOnceWithoutChangingTaskIdentity(){
        var seven=task(100,TASK_TYPE_ACCOUNT_DIAGNOSIS_7D,"2026-09-27T12:00:00");
        var fourteen=task(101,TASK_TYPE_ACCOUNT_DIAGNOSIS_14D,"2026-09-27T12:00:00");
        seven.setBizType("media_account_diagnosis");fourteen.setBizType("media_account_diagnosis");
        seven.setPayload("{\"roundKey\":1}");fourteen.setPayload("{\"roundKey\":1}");
        when(mapper.selectMyPending(7L)).thenReturn(List.of(seven,fourteen));
        var summary=service("2026-09-27T04:00:00Z").getMySummary(7L);
        assertEquals(1,summary.getTotal());assertEquals(1,summary.getItems().getFirst().getCount());
    }
    private final BusinessTaskMapper mapper = mock(BusinessTaskMapper.class);

    private MenuTaskSummaryServiceImpl service(String instant) {
        var service = new MenuTaskSummaryServiceImpl(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "taskMapper", mapper);
        return service;
    }

    private BusinessTaskDO task(long id, String type, String due) {
        var task = new BusinessTaskDO();
        task.setId(id); task.setBizId(42L); task.setBizType(BIZ_TYPE_LEAD);
        task.setAssigneeId(7L); task.setTaskType(type); task.setStatus(TASK_STATUS_PENDING);
        task.setDueAt(due == null ? null : LocalDateTime.parse(due));
        return task;
    }

    @Test
    void studentRemindersUsePersonalPendingTasksAndExactServiceRelationNotPersonOrLead() {
        var first = task(1, "student_first_contact", "2026-09-27T12:00:00");
        first.setBizType("student_service"); first.setBizId(11L);
        var assist = task(2, "student_first_contact_assistance", null);
        assist.setBizType("student_service"); assist.setBizId(11L);
        var sibling = task(3, "student_contact", "2026-09-26T12:00:00");
        sibling.setBizType("student_service"); sibling.setBizId(12L);
        var lead = task(4, TASK_TYPE_FIRST_FOLLOW_UP, null); lead.setBizId(11L);
        when(mapper.selectMyPending(7L)).thenReturn(List.of(first, assist, sibling, lead));
        var service = service("2026-09-27T04:00:00Z");
        assertEquals(List.of(11L,12L), service.getMyStudentReminderIds(7L));
        var rows = service.getMyStudentReminders(7L, List.of(11L));
        assertEquals(java.util.Set.of(11L), rows.keySet());
        assertEquals(List.of(1L,2L), rows.get(11L).stream().map(t -> t.getId()).toList());
        assertEquals("学员首联", rows.get(11L).getFirst().getTitle());
        first.setStatus("completed"); assist.setStatus("cancelled");
        assertTrue(service.getMyStudentReminders(7L, List.of(11L)).isEmpty());
        assertEquals(List.of(12L), service.getMyStudentReminderIds(7L));
        assertTrue(service.getMyStudentReminders(8L, List.of(11L,12L)).isEmpty());
        assertTrue(service.getMyStudentReminders(null, List.of(11L)).isEmpty());
    }

    @Test
    void studentMenuRowsAndPriorityShareTodayOverdueAndUndatedEligibility() {
        var overdue = studentTask(1, 11, "student_contact", "2026-09-26T12:00:00");
        var today = studentTask(2, 12, "student_study_plan", "2026-09-27T12:00:00");
        var tonight = studentTask(3, 12, "student_delivery_stage", "2026-09-27T23:59:59");
        var tomorrow = studentTask(4, 13, "student_first_contact", "2026-09-28T00:00:00");
        var later = studentTask(5, 14, "student_contact", "2026-10-01T12:00:00");
        var immediate = studentTask(6, 15, "student_first_contact_assistance", null);
        var completed = studentTask(7, 16, "student_contact", "2026-09-27T10:00:00");
        completed.setStatus("completed");
        var cancelled = studentTask(8, 17, "student_contact", "2026-09-26T10:00:00");
        cancelled.setStatus("cancelled");
        when(mapper.selectMyPending(7L)).thenReturn(List.of(
                overdue, today, tonight, tomorrow, later, immediate, completed, cancelled));
        var service = service("2026-09-27T04:00:00Z");

        assertEquals(List.of(11L, 12L, 15L), service.getMyStudentReminderIds(7L));
        var rows = service.getMyStudentReminders(7L, List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L));
        assertEquals(java.util.Set.of(11L, 12L, 15L), rows.keySet());
        assertEquals(List.of(2L, 3L), rows.get(12L).stream().map(t -> t.getId()).toList());
        assertTrue(rows.get(11L).getFirst().isOverdue());
        assertFalse(rows.get(12L).getFirst().isOverdue());
        assertFalse(rows.get(15L).getFirst().isOverdue());
        var summary = service.getMySummary(7L);
        assertEquals(4, summary.getTotal());
        var item = summary.getItems().getFirst();
        assertEquals("/zsjos/my-students", item.getMenuPath());
        assertEquals(4, item.getCount());
        assertEquals("urgent", item.getSeverity());
        assertEquals("serviceRelationId=11", item.getTarget().getQuery());
        assertEquals(TASK_STATUS_PENDING, tomorrow.getStatus());
        assertEquals(TASK_STATUS_PENDING, later.getStatus());

        overdue.setStatus("completed");
        today.setStatus("completed");
        tonight.setStatus("cancelled");
        immediate.setStatus("completed");
        assertTrue(service.getMyStudentReminderIds(7L).isEmpty());
        assertTrue(service.getMyStudentReminders(7L, List.of(11L, 12L, 13L, 14L, 15L)).isEmpty());
        assertTrue(service.getMySummary(7L).getItems().isEmpty());
    }

    @Test
    void scheduledStudentTasksStartRemindingOnBeijingDueDayAcrossMonthAndYear() {
        for (String date : List.of("2026-10-01", "2027-01-01")) {
            var midnight = LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            when(mapper.selectMyPending(7L)).thenReturn(List.of(
                    studentTask(1, 11, "student_first_contact", date + "T00:00:00"),
                    studentTask(2, 11, "student_study_plan", date + "T23:59:59"),
                    studentTask(3, 11, "student_contact", date + "T12:00:00"),
                    studentTask(4, 11, "student_first_contact_assistance", date + "T12:00:00"),
                    studentTask(5, 11, "student_delivery_stage", date + "T12:00:00")));
            var before = service(midnight.minusNanos(1).toString());
            assertTrue(before.getMySummary(7L).getItems().isEmpty());
            assertTrue(before.getMyStudentReminders(7L, List.of(11L)).isEmpty());
            assertTrue(before.getMyStudentReminderIds(7L).isEmpty());

            var onDueDay = service(midnight.toString());
            assertEquals(5, onDueDay.getMySummary(7L).getTotal());
            assertEquals(5, onDueDay.getMyStudentReminders(7L, List.of(11L)).get(11L).size());
            assertEquals(List.of(11L), onDueDay.getMyStudentReminderIds(7L));
        }
    }

    private BusinessTaskDO studentTask(long id, long relationId, String type, String due) {
        var task = task(id, type, due);
        task.setBizType("student_service");
        task.setBizId(relationId);
        return task;
    }

    @Test
    void leadRemindersIntersectVisibleLeadsAndKeepAllEligibleReasonsUntilCompleted() {
        var follow = task(1, TASK_TYPE_FIRST_FOLLOW_UP, null);
        follow.setTitleSnapshot("首次联系客户");
        var qualification = task(2, TASK_TYPE_QUALIFICATION, "2026-09-26T12:00:00");
        var future = task(3, TASK_TYPE_FOLLOW_UP_REMINDER, "2026-09-28T00:00:00");
        var hidden = task(4, TASK_TYPE_SUBMITTER_ASSIST, null);
        hidden.setBizId(99L);
        var student = task(5, "student_first_contact", null);
        student.setBizType("student_service");
        when(mapper.selectMyPending(7L)).thenReturn(List.of(follow, qualification, future, hidden, student));
        var service = service("2026-09-27T04:00:00Z");
        var result = service.getMyLeadReminders(7L, List.of(42L));
        assertEquals(java.util.Set.of(42L), result.keySet());
        assertEquals(List.of(42L, 99L), service.getMyLeadReminderIds(7L));
        assertEquals(List.of(1L, 2L), result.get(42L).stream().map(t -> t.getId()).toList());
        assertEquals("首次联系客户", result.get(42L).getFirst().getTitle());
        assertFalse(result.get(42L).getFirst().isOverdue());
        assertTrue(result.get(42L).getLast().isOverdue());
        assertEquals(result, service.getMyLeadReminders(7L, List.of(42L))); // Reading does not clear obligations.
        follow.setStatus("completed");
        assertEquals(1, service.getMyLeadReminders(7L, List.of(42L)).get(42L).size());
        qualification.setStatus("cancelled");
        assertTrue(service.getMyLeadReminders(7L, List.of(42L)).isEmpty());
        assertTrue(service.getMyLeadReminders(7L, List.of()).isEmpty());
        assertTrue(service.getMyLeadReminders(null, List.of(42L)).isEmpty());
    }

    @Test
    void firstFollowUpIsImmediateWhileFutureDatedRemindersAndMissingDatesAreExcluded() {
        when(mapper.selectMyPending(7L)).thenReturn(List.of(
                task(1, TASK_TYPE_FIRST_FOLLOW_UP, "2026-09-29T12:00:00"),
                task(2, TASK_TYPE_FOLLOW_UP_REMINDER, "2026-09-28T00:00:00"),
                task(3, TASK_TYPE_QUALIFICATION, "2026-09-29T12:00:00"),
                task(4, TASK_TYPE_QUALIFICATION, null),
                task(5, TASK_TYPE_FOLLOW_UP_REMINDER, null)));
        var result = service("2026-09-27T04:00:00Z").getMySummary(7L);
        assertEquals(1, result.getTotal());
        var item = result.getItems().getFirst();
        assertEquals(List.of(TASK_TYPE_FIRST_FOLLOW_UP), item.getSourceTypes());
        assertEquals("normal", item.getSeverity());
        verify(mapper).selectMyPending(7L);
    }

    @Test
    void countsTasksOnSameLeadAndDropsCompletedAndCancelledWithoutChangingSourceTasks() {
        var follow = task(1, TASK_TYPE_FOLLOW_UP_REMINDER, "2026-09-27T20:00:00");
        var qualification = task(2, TASK_TYPE_QUALIFICATION, "2026-09-26T20:00:00");
        when(mapper.selectMyPending(7L)).thenReturn(List.of(follow, qualification));
        var service = service("2026-09-27T04:00:00Z");
        var result = service.getMySummary(7L);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getItems().getFirst().getCount());
        assertEquals("urgent", result.getItems().getFirst().getSeverity());
        assertEquals(TASK_STATUS_PENDING, follow.getStatus());
        follow.setStatus("completed");
        assertEquals(1, service.getMySummary(7L).getTotal());
        qualification.setStatus("cancelled");
        assertTrue(service.getMySummary(7L).getItems().isEmpty());
    }

    @Test
    void usesBeijingMidnightAcrossMonthAndYearEvenWithUtcClock() {
        for (String date : List.of("2026-10-01", "2027-01-01")) {
            var day = LocalDate.parse(date);
            var midnight = day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            when(mapper.selectMyPending(7L)).thenReturn(List.of(
                    task(1, TASK_TYPE_FOLLOW_UP_REMINDER, date + "T23:59:59"),
                    task(2, TASK_TYPE_QUALIFICATION, date + "T00:00:00")));
            assertEquals(0, service(midnight.minusNanos(1).toString()).getMySummary(7L).getTotal());
            var result = service(midnight.toString()).getMySummary(7L);
            assertEquals(2, result.getTotal());
            assertEquals(midnight.toEpochMilli(), result.getGeneratedAt());
            assertEquals("normal", result.getItems().getFirst().getSeverity());
        }
    }

    @Test
    void targetAndTypesComeOnlyFromEligibleTasksAndOtherTypesKeepTheirRules() {
        var future = task(1, TASK_TYPE_QUALIFICATION, "2026-09-28T12:00:00");
        future.setBizId(100L);
        var assist = task(2, TASK_TYPE_SUBMITTER_ASSIST, "2026-10-01T12:00:00");
        var student = task(3, "student_delivery_stage", "2026-10-01T12:00:00");
        student.setBizType("student_service");
        var accept = task(4, TASK_TYPE_ASSIGNMENT_ACCEPT, "2026-10-01T12:00:00");
        when(mapper.selectMyPending(7L)).thenReturn(List.of(future, assist, student, accept));
        var result = service("2026-09-27T04:00:00Z").getMySummary(7L);
        assertEquals(2, result.getTotal());
        var item = result.getItems().stream().filter(i -> i.getMenuPath().equals("/zsjos/leads/manage")).findFirst().orElseThrow();
        assertEquals("leadId=42", item.getTarget().getQuery());
        assertEquals(List.of(TASK_TYPE_SUBMITTER_ASSIST, TASK_TYPE_ASSIGNMENT_ACCEPT), item.getSourceTypes());
        assertEquals(result.getTotal(), result.getItems().stream().mapToLong(i -> i.getCount()).sum());
    }

    @Test
    void neverQueriesOtherAssigneesAndEmptySummaryHasNoBadgeItems() {
        when(mapper.selectMyPending(8L)).thenReturn(List.of());
        assertEquals(0, service("2026-09-27T04:00:00Z").getMySummary(8L).getTotal());
        verify(mapper).selectMyPending(8L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void realQueryKeepsTenantAssigneeAndDeletionBoundariesAndFutureHomepageTasks() {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:menu_reminder" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        var plugins = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    public net.sf.jsqlparser.expression.Expression getTenantId() {
                        return new net.sf.jsqlparser.expression.LongValue(1);
                    }
                }));
        config.addInterceptor(plugins);
        config.addMapper(BusinessTaskMapper.class);
        var factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(config);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE ALIAS JSON_VALID FOR 'cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryServiceImplTest.jsonValid'");
        jdbc.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryServiceImplTest.jsonExtract'");
        jdbc.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryServiceImplTest.jsonUnquote'");
        var ddl = new StringBuilder("CREATE TABLE zsjos_business_task(id BIGINT PRIMARY KEY");
        for (var field : com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(BusinessTaskDO.class).getFieldList()) {
            var type = field.getPropertyType();
            ddl.append(", ").append(field.getColumn()).append(" ").append(type == String.class ? "VARCHAR(2000)"
                    : type == LocalDateTime.class ? "TIMESTAMP" : "BIGINT");
        }
        jdbc.execute(ddl.append(")").toString());
        jdbc.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,owner_user_id BIGINT)");
        jdbc.execute("CREATE TABLE zsjos_lead_public_sea_pool_cycle(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR,collaborator_user_id BIGINT)");
        jdbc.execute("CREATE TABLE zsjos_lead_public_sea_pool_compatibility_record(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted INT,owner_user_id BIGINT,collaborator_user_id BIGINT)");
        jdbc.execute("INSERT INTO zsjos_lead VALUES(42,1,0,7),(42,2,0,7)");
        for (int id = 1; id <= 7; id++) {
            jdbc.update("INSERT INTO zsjos_business_task(id,tenant_id,deleted,assignee_id,biz_type,biz_id,task_type,status,due_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, id == 3 ? 2 : 1, id == 4 ? 1 : 0, id == 2 ? 8 : 7,
                    BIZ_TYPE_LEAD, 42, id == 7 ? TASK_TYPE_QUALIFICATION : TASK_TYPE_FIRST_FOLLOW_UP,
                    id == 5 ? "completed" : id == 6 ? "cancelled" : TASK_STATUS_PENDING,
                    LocalDateTime.parse("2026-09-28T12:00:00"));
        }
        for (int id = 8; id <= 14; id++) {
            jdbc.update("INSERT INTO zsjos_business_task(id,tenant_id,deleted,assignee_id,biz_type,biz_id,task_type,status,due_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, id == 10 ? 2 : 1, id == 11 ? 1 : 0, id == 9 ? 8 : 7,
                    "student_service", 100, "student_contact",
                    id == 12 ? "completed" : id == 13 ? "cancelled" : TASK_STATUS_PENDING,
                    LocalDateTime.parse("2026-09-28T12:00:00"));
        }
        try (var session = factory.openSession()) {
            var actualMapper = session.getMapper(BusinessTaskMapper.class);
            var service = service("2026-09-27T04:00:00Z");
            ReflectionTestUtils.setField(service, "taskMapper", actualMapper);
            assertEquals(1, service.getMySummary(7L).getTotal());
            assertEquals(List.of(1L), service.getMyLeadReminders(7L, List.of(42L)).get(42L).stream().map(t -> t.getId()).toList());
            assertTrue(service.getMyLeadReminders(7L, List.of(99L)).isEmpty());
            assertTrue(service.getMyLeadReminders(9L, List.of(42L)).isEmpty());
            assertTrue(service.getMyStudentReminders(7L, List.of(100L)).isEmpty());
            assertTrue(service.getMyStudentReminderIds(7L).isEmpty());
            assertEquals(List.of(1L, 7L, 8L, 14L), actualMapper.selectMyPending(7L).stream().map(BusinessTaskDO::getId).toList());
            assertEquals(4, actualMapper.selectMyPendingCount(7L, "future", LocalDateTime.parse("2026-09-27T12:00:00")));
            assertEquals(1, service.getMySummary(8L).getTotal());
            assertEquals(0, service.getMySummary(9L).getTotal());

            var onDueDay = service("2026-09-27T16:00:00Z");
            ReflectionTestUtils.setField(onDueDay, "taskMapper", actualMapper);
            assertEquals(List.of(8L, 14L), onDueDay.getMyStudentReminders(7L, List.of(100L))
                    .get(100L).stream().map(t -> t.getId()).toList());
            assertEquals(List.of(100L), onDueDay.getMyStudentReminderIds(7L));
            assertTrue(onDueDay.getMyStudentReminders(9L, List.of(100L)).isEmpty());
            assertTrue(onDueDay.getMyStudentReminders(7L, List.of(101L)).isEmpty());
        }
    }
    @Test void diagnosisBadgeUsesTheSameSevenDayOpeningWindowAsWeeklyNodes() {
        var open=task(100,TASK_TYPE_ACCOUNT_DIAGNOSIS_7D,"2026-10-08T00:00:00");
        var future=task(101,TASK_TYPE_ACCOUNT_DIAGNOSIS_14D,"2026-10-15T00:00:00");
        var later=task(102,TASK_TYPE_ACCOUNT_DIAGNOSIS_28D,"2026-10-29T00:00:00");
        for(var task:List.of(open,future,later)) {task.setBizType("media_account_diagnosis");task.setPayload("{\"roundKey\":1}");}
        when(mapper.selectMyPending(7L)).thenReturn(List.of(open,future,later));
        assertEquals(1,service("2026-09-30T16:00:00Z").getMySummary(7L).getTotal());
        when(mapper.selectMyPending(7L)).thenReturn(List.of(future,later));
        assertEquals(0,service("2026-09-30T16:00:00Z").getMySummary(7L).getTotal());
    }

}
