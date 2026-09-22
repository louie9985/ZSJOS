package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PurchaseIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderRevisionMapperTest {
    @Test
    void paymentUniqueKeyAndOfflineSnapshotAreTenantScopedAndRollbackTogether() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:revision" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var keepAlive = source.getConnection()) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE zsjos_order(id BIGINT PRIMARY KEY, tenant_id BIGINT, source_payment_order_id BIGINT, deleted INT DEFAULT 0, UNIQUE(tenant_id,source_payment_order_id))");
            jdbc.execute("CREATE TABLE zsjos_purchase_intent(id BIGINT PRIMARY KEY, tenant_id BIGINT, current_order_id BIGINT, version INT, collection_mode VARCHAR(30), status VARCHAR(30), item_snapshot_json VARCHAR(2000), total_amount DECIMAL(18,2), deleted INT DEFAULT 0)");
            jdbc.execute("INSERT INTO zsjos_order VALUES(100,1,900,0),(200,2,900,0)");
            jdbc.execute("INSERT INTO zsjos_purchase_intent VALUES(800,1,100,1,'offline_paid','submitted','old',500,0),(801,2,200,1,'offline_paid','submitted','other',500,0)");
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
            }));
            config.addInterceptor(interceptor);
            config.addMapper(SalesOrderMapper.class); config.addMapper(PurchaseIntentMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            TenantContextHolder.setTenantId(1L);
            try (var session = factory.openSession()) {
                var orders = session.getMapper(SalesOrderMapper.class);
                var intents = session.getMapper(PurchaseIntentMapper.class);
                assertEquals(0, orders.releasePaymentForSuccessor(200L, 900L));
                assertEquals(1, orders.releasePaymentForSuccessor(100L, 900L));
                try (var st = session.getConnection().createStatement()) {
                    st.executeUpdate("INSERT INTO zsjos_order VALUES(101,1,900,0)");
                }
                var intent = new PurchaseIntentDO().setId(800L).setCurrentOrderId(100L).setVersion(1);
                assertEquals(1, intents.reviseOfflineSnapshot(intent, "new", new BigDecimal("800")));
                assertEquals(0, intents.reviseOfflineSnapshot(intent, "stale", new BigDecimal("900")));
                assertEquals(0, intents.reviseOfflineSnapshot(new PurchaseIntentDO().setId(801L).setCurrentOrderId(200L).setVersion(1), "cross-tenant", BigDecimal.ONE));
                session.rollback();
            }
            assertEquals(900L, jdbc.queryForObject("SELECT source_payment_order_id FROM zsjos_order WHERE id=100", Long.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_order WHERE id=101", Integer.class));
            assertEquals("old", jdbc.queryForObject("SELECT item_snapshot_json FROM zsjos_purchase_intent WHERE id=800", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT version FROM zsjos_purchase_intent WHERE id=800", Integer.class));
            try (var session = factory.openSession()) {
                assertEquals(1, session.getMapper(SalesOrderMapper.class).releasePaymentForSuccessor(100L, 900L));
                try (var st = session.getConnection().createStatement()) { st.executeUpdate("INSERT INTO zsjos_order VALUES(101,1,900,0)"); }
                session.commit();
            }
            assertNull(jdbc.queryForObject("SELECT source_payment_order_id FROM zsjos_order WHERE id=100", Long.class));
            assertEquals(900L, jdbc.queryForObject("SELECT source_payment_order_id FROM zsjos_order WHERE id=101", Long.class));
            assertEquals(900L, jdbc.queryForObject("SELECT source_payment_order_id FROM zsjos_order WHERE id=200", Long.class));
        } finally { TenantContextHolder.clear(); }
    }
}
