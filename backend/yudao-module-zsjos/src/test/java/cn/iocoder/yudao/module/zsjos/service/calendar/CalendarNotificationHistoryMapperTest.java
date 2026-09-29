package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyRecipientDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyRecipientMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarNotificationHistoryMapperTest {
    @Test void historyPagingFiltersTenantTypeArrangementStatusAndLogicalDeletionBeforeCounting() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:calendar_history" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var plugins = new MybatisPlusInterceptor(); plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(plugins);
        config.addMapper(CalendarNotifyBatchMapper.class); config.addMapper(CalendarNotifyRecipientMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        var jdbc = new JdbcTemplate(source);
        for (var type : List.of(CalendarNotifyBatchDO.class, CalendarNotifyRecipientDO.class)) {
            var info = TableInfoHelper.getTableInfo(type);
            var ddl = new StringBuilder("CREATE TABLE ").append(info.getTableName()).append("(id BIGINT PRIMARY KEY");
            for (var field : info.getFieldList()) ddl.append(", ").append(field.getColumn()).append(' ')
                    .append(field.getPropertyType() == String.class ? "VARCHAR(2000)"
                            : field.getPropertyType() == LocalDateTime.class ? "TIMESTAMP" : "BIGINT");
            jdbc.execute(ddl.append(')').toString());
        }
        for (int id = 1; id <= 7; id++) {
            jdbc.update("INSERT INTO zsjos_calendar_notify_batch(id,tenant_id,calendar_type,calendar_id,status,deleted) VALUES(?,?,?,?,?,?)",
                    id, id == 3 ? 11 : 10, id == 4 ? "EXAM" : "COURSE", id == 5 ? 8 : 7, id == 6 ? "FAILED" : "SUBMITTED", id == 7 ? 1 : 0);
            jdbc.update("INSERT INTO zsjos_calendar_notify_recipient(id,batch_id,tenant_id,user_id,user_type,nickname_snapshot,status,deleted) VALUES(?,?,?,?,?,?,?,?)",
                    id, id == 4 ? 2 : 1, id == 3 ? 11 : 10, id, 2, "历史姓名" + id, "PENDING", id >= 5 ? 1 : 0);
        }
        try (var session = factory.openSession()) {
            var batchReq = new CalendarNotifyBatchPageReqVO(); batchReq.setCalendarType("COURSE");
            batchReq.setCalendarId(7L); batchReq.setStatus("SUBMITTED"); batchReq.setPageSize(1);
            var batches = session.getMapper(CalendarNotifyBatchMapper.class);
            var first = batches.selectHistoryPage(batchReq, 10L);
            assertEquals(2L, first.getTotal()); assertEquals(2L, first.getList().get(0).getId());
            batchReq.setPageNo(2);
            assertEquals(1L, batches.selectHistoryPage(batchReq, 10L).getList().get(0).getId());
            var recipientReq = new PageParam(); recipientReq.setPageSize(1); recipientReq.setPageNo(2);
            var recipients = session.getMapper(CalendarNotifyRecipientMapper.class).selectBatchPage(1L, 10L, recipientReq);
            assertEquals(2L, recipients.getTotal()); assertEquals("历史姓名2", recipients.getList().get(0).getNicknameSnapshot());
            // Historical union must filter both sides of the join, including mismatched tenant references.
            for (int id = 8; id <= 14; id++) {
                jdbc.update("INSERT INTO zsjos_calendar_notify_recipient(id,batch_id,tenant_id,user_id,user_type,nickname_snapshot,status,deleted) VALUES(?,?,?,?,?,?,?,?)",
                        id, id == 8 ? 1 : id == 9 ? 7 : id == 10 ? 4 : id == 11 ? 5 : id == 12 ? 3 : 1,
                        id == 13 ? 11 : 10, 1, id == 14 ? 1 : 2, "later", "SUCCEEDED", 0);
            }
            var history = session.getMapper(CalendarNotifyRecipientMapper.class).selectHistoricalRecipients(10L, "COURSE", 7L);
            assertEquals(List.of(8L, 4L, 2L, 1L), history.stream().map(CalendarNotifyRecipientDO::getId).toList());
            assertEquals(List.of(1L, 4L, 2L, 1L), history.stream().map(CalendarNotifyRecipientDO::getUserId).toList());
        }
    }
}
