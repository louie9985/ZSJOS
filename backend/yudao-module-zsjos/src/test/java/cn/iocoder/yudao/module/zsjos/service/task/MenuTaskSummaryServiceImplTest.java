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

class MenuTaskSummaryServiceImplTest {
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
        assertEquals(3, result.getTotal());
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
        var ddl = new StringBuilder("CREATE TABLE zsjos_business_task(id BIGINT PRIMARY KEY");
        for (var field : com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(BusinessTaskDO.class).getFieldList()) {
            var type = field.getPropertyType();
            ddl.append(", ").append(field.getColumn()).append(" ").append(type == String.class ? "VARCHAR(2000)"
                    : type == LocalDateTime.class ? "TIMESTAMP" : "BIGINT");
        }
        jdbc.execute(ddl.append(")").toString());
        for (int id = 1; id <= 7; id++) {
            jdbc.update("INSERT INTO zsjos_business_task(id,tenant_id,deleted,assignee_id,biz_type,biz_id,task_type,status,due_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, id == 3 ? 2 : 1, id == 4 ? 1 : 0, id == 2 ? 8 : 7,
                    BIZ_TYPE_LEAD, 42, id == 7 ? TASK_TYPE_QUALIFICATION : TASK_TYPE_FIRST_FOLLOW_UP,
                    id == 5 ? "completed" : id == 6 ? "cancelled" : TASK_STATUS_PENDING,
                    LocalDateTime.parse("2026-09-28T12:00:00"));
        }
        try (var session = factory.openSession()) {
            var actualMapper = session.getMapper(BusinessTaskMapper.class);
            var service = service("2026-09-27T04:00:00Z");
            ReflectionTestUtils.setField(service, "taskMapper", actualMapper);
            assertEquals(1, service.getMySummary(7L).getTotal());
            assertEquals(List.of(1L, 7L), actualMapper.selectMyPending(7L).stream().map(BusinessTaskDO::getId).toList());
            assertEquals(2, actualMapper.selectMyPendingCount(7L, "future", LocalDateTime.parse("2026-09-27T12:00:00")));
            assertEquals(1, service.getMySummary(8L).getTotal());
            assertEquals(0, service.getMySummary(9L).getTotal());
        }
    }
}
