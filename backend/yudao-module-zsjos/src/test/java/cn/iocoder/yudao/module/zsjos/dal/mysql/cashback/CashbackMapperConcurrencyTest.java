package cn.iocoder.yudao.module.zsjos.dal.mysql.cashback;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CashbackMapperConcurrencyTest {
    @Test void applicationWaitsForBlockAndStaleSettlementCannotRestoreIt() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:cashback" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(CashbackMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(config);
        var info = TableInfoHelper.getTableInfo(CashbackDO.class);
        var ddl = new StringBuilder("CREATE TABLE zsjos_cashback (id BIGINT PRIMARY KEY");
        for (var field : info.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(2000)" : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == java.math.BigDecimal.class ? "DECIMAL(12,2)" : type == Boolean.class ? "INT" : "BIGINT";
            ddl.append(", ").append(field.getColumn()).append(" ").append(sqlType);
        }
        new JdbcTemplate(source).execute(ddl.append(")").toString());
        new JdbcTemplate(source).update("INSERT INTO zsjos_cashback(id,tenant_id,status,version,deleted) VALUES (1,9,'available',0,0)");
        CountDownLatch attempting = new CountDownLatch(1);
        try (var owner = factory.openSession(false); var executor = Executors.newSingleThreadExecutor()) {
            var mapper = owner.getMapper(CashbackMapper.class);
            assertEquals("available", mapper.selectByIdForUpdate(1L,9L).getStatus());
            assertEquals(1, mapper.transitionStatus(1L,0,"available","blocked"));
            Future<String> applicant = executor.submit(() -> {
                try (var session = factory.openSession(false)) {
                    attempting.countDown();
                    var row = session.getMapper(CashbackMapper.class).selectByIdForUpdate(1L,9L);
                    session.rollback();
                    return row.getStatus();
                }
            });
            assertTrue(attempting.await(2,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> applicant.get(150,TimeUnit.MILLISECONDS));
            owner.commit();
            assertEquals("blocked", applicant.get(3,TimeUnit.SECONDS));
            assertEquals(0, mapper.transition(1L,0,"pending_settlement","available",LocalDateTime.now()));
            assertEquals(0, mapper.transitionStatus(1L,0,"available","withdrawing"));
            assertEquals("blocked", mapper.selectByIdForUpdate(1L,9L).getStatus());
            assertNull(mapper.selectByIdForUpdate(1L,10L));
            owner.rollback();
        }
    }
}
