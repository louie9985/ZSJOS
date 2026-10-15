package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactReadPolicy;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentServiceSummaryTest {
    private BusinessTaskDO task(long id, String type, String status) {
        var task = new BusinessTaskDO(); task.setId(id); task.setTaskType(type); task.setStatus(status); return task;
    }
    @Test void taskPriorityIsStageThenNewestIdAndIgnoresClosedTasks() {
        var older = task(1, "student_first_contact", "pending");
        var newest = task(2, "student_first_contact", "pending");
        assertSame(newest, StudentContactReadPolicy.currentTask(List.of(task(99, "student_contact", "pending"), older, newest, task(100, "student_first_contact", "completed"))));
        assertNull(StudentContactReadPolicy.currentTask(List.of(task(9, "student_first_contact_assistance", "pending"))));
    }
    @Test void newDateSortsAreDatabaseWideNullLastAndStable() {
        for (String field : List.of("acceptedAt", "examDate", "latestContactAt", "taskDueAt", "pausedAt", "completedAt", "terminatedAt")) {
            var query = new AdvancedFilterQuery("1=1", Map.of("sortField", field, "sortOrder", "ascend"));
            String sql = StudentServicePageSql.page(Map.of("query", query));
            assertTrue(sql.contains("IS NULL) ASC")); assertTrue(sql.contains(",sr.id DESC LIMIT"));
            assertTrue(sql.contains("sr.tenant_id=#{query.parameters.tenantId}"));
        }
        String task = StudentServiceSummarySql.task("due_at");
        assertTrue(task.contains("bt.id DESC LIMIT 1")); assertTrue(task.contains("bt.biz_id=sr.id"));
        assertThrows(IllegalArgumentException.class, () -> StudentServiceSummarySql.task("payload"));
    }
}
