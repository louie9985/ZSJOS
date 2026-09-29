package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NotifyDeliveryEvidenceMapperTest {
    @Test void evidenceQueriesEnforceTenantSceneRuleEventIdentityAndDeletionWithActualSql() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:delivery_evidence" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(NotifyBusinessOutboxMapper.class); config.addMapper(NotifyMessageMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE system_notify_business_outbox(id BIGINT PRIMARY KEY,tenant_id BIGINT,scene_code VARCHAR(64),source_event_key VARCHAR(128),target_rule_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE system_notify_message(id BIGINT PRIMARY KEY,tenant_id BIGINT,scene_code VARCHAR(64),source_event_key VARCHAR(128),notify_rule_id BIGINT,user_id BIGINT,user_type INT,create_time TIMESTAMP,deleted INT)");
        for (int id = 1; id <= 8; id++) {
            long tenant = id == 3 ? 11 : 10; String scene = id == 4 ? "other" : "calendar";
            String event = id == 5 ? "other" : "event"; int deleted = id == 6 ? 1 : 0;
            jdbc.update("INSERT INTO system_notify_business_outbox VALUES(?,?,?,?,?,?)", id, tenant, scene, event, id == 7 ? 12 : 11, deleted);
            jdbc.update("INSERT INTO system_notify_message VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
                    id, tenant, scene, event, id == 7 ? 12 : 11, id == 8 ? 2 : 1, id == 2 ? 3 : 2, deleted);
        }
        try (var session = factory.openSession()) {
            var outboxes = session.getMapper(NotifyBusinessOutboxMapper.class);
            assertEquals(List.of(2L, 7L), outboxes.selectEventEvidence(10L, "calendar", "event", 1, 2)
                    .stream().map(r -> r.getId()).toList());
            var messages = session.getMapper(NotifyMessageMapper.class);
            var admin = messages.selectDeliveryEvidence(10L, "calendar", "event", 11L, List.of(NotifyRecipientDTO.admin(1L)));
            assertEquals(List.of(1L), admin.stream().map(r -> r.getId()).toList());
            assertNull(admin.getFirst().getTemplateContent());
            assertEquals(List.of(2L), messages.selectDeliveryEvidence(10L, "calendar", "event", 11L,
                    List.of(NotifyRecipientDTO.partner(1L))).stream().map(r -> r.getId()).toList());
        }
    }
}
