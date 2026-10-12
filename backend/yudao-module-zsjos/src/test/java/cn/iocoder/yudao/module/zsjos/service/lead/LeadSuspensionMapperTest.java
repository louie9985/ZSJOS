package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LeadSuspensionMapperTest {
    @Test
    void clearsPersistedSuspensionWithStateAndRollsBackTogether() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:suspension" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var keepAlive = source.getConnection()) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY, status VARCHAR(32), suspended_at TIMESTAMP, update_time TIMESTAMP, updater VARCHAR(64), deleted BOOLEAN DEFAULT FALSE)");
            jdbc.execute("INSERT INTO zsjos_lead(id,status,suspended_at,deleted) VALUES(1,'suspended','2026-10-04 21:37:09',FALSE),(2,'suspended','2026-10-04 21:37:09',FALSE),(3,'suspended','2026-10-04 21:37:09',TRUE)");
            var config = new MybatisConfiguration();
            config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
            config.addMapper(LeadMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            try (var session = factory.openSession()) {
                assertEquals(1, session.getMapper(LeadMapper.class).updateClearingSuspension(
                        new LeadDO().setId(1L).setStatus("submitted")));
                session.rollback();
            }
            assertEquals("suspended", jdbc.queryForObject("SELECT status FROM zsjos_lead WHERE id=1", String.class));
            assertNotNull(jdbc.queryForObject("SELECT suspended_at FROM zsjos_lead WHERE id=1", java.sql.Timestamp.class));
            for (String status : new String[]{"submitted", "valid"}) {
                jdbc.update("UPDATE zsjos_lead SET status='suspended', suspended_at='2026-10-04 21:37:09' WHERE id=1");
                try (var session = factory.openSession()) {
                    var mapper = session.getMapper(LeadMapper.class);
                    assertEquals(1, mapper.updateClearingSuspension(new LeadDO().setId(1L).setStatus(status)));
                    assertEquals(0, mapper.updateClearingSuspension(new LeadDO().setId(3L).setStatus(status)));
                    session.commit();
                }
                assertEquals(status, jdbc.queryForObject("SELECT status FROM zsjos_lead WHERE id=1", String.class));
                assertNull(jdbc.queryForObject("SELECT suspended_at FROM zsjos_lead WHERE id=1", java.sql.Timestamp.class));
            }
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_lead WHERE suspended_at IS NOT NULL AND status='suspended'", Integer.class));
        }
    }
}
