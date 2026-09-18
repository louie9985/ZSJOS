package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentSubjectMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.ProductPaymentSubjectMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductSkuMapper;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSubjectTenantTest {
    @Test
    void skuAssociationAndDefaultQueriesRespectTenantAndLogicalDeletion() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:routing" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        // 持有连接以保持本次测试数据库；全部数据仅为内存隔离夹具。
        try (var connection = source.getConnection()) {
            var jdbc = new JdbcTemplate(source);
            String audit = ", tenant_id BIGINT, deleted INT DEFAULT 0, creator VARCHAR(20), updater VARCHAR(20), create_time TIMESTAMP, update_time TIMESTAMP)";
            jdbc.execute("CREATE TABLE zsjos_product_sku(id BIGINT PRIMARY KEY, spu_id BIGINT, sku_ref VARCHAR(50), sku_name VARCHAR(50), attr_values_json VARCHAR(100), attr_values_hash VARCHAR(100), price DECIMAL, retail_price DECIMAL, min_deal_price DECIMAL, min_deal_type VARCHAR(20), min_deal_rate DECIMAL, exam_fee DECIMAL, price_unit VARCHAR(20), pricing_note VARCHAR(20), status INT, sort INT, remark VARCHAR(50)" + audit);
            jdbc.execute("CREATE TABLE zsjos_product_payment_subject(id BIGINT PRIMARY KEY, product_id BIGINT, payment_subject_id BIGINT" + audit);
            jdbc.execute("CREATE TABLE zsjos_payment_subject(id BIGINT PRIMARY KEY, subject_code VARCHAR(50), subject_name VARCHAR(50), cusid VARCHAR(50), appid VARCHAR(50), orgid VARCHAR(50), merchant_private_key VARCHAR(100), platform_public_key VARCHAR(100), rsa_type VARCHAR(20), status INT, is_default BOOLEAN, sort INT, remark VARCHAR(50)" + audit);
            jdbc.execute("INSERT INTO zsjos_product_sku(id,spu_id,sku_ref,tenant_id,deleted) VALUES(1,101,'sku_shared',1,0),(2,202,'sku_shared',2,0),(3,303,'sku_deleted',1,1)");
            jdbc.execute("INSERT INTO zsjos_product_payment_subject(id,product_id,payment_subject_id,tenant_id,deleted) VALUES(1,101,10,1,0),(2,101,20,2,0),(3,202,30,1,1)");
            jdbc.execute("INSERT INTO zsjos_payment_subject(id,subject_code,status,is_default,tenant_id,deleted) VALUES(10,'default-one',0,TRUE,1,0),(20,'default-two',0,TRUE,2,0),(30,'deleted',0,TRUE,1,1),(40,'disabled',1,TRUE,1,0)");
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
            }));
            config.addInterceptor(interceptor);
            config.addMapper(ZsjosProductSkuMapper.class); config.addMapper(ProductPaymentSubjectMapper.class);
            config.addMapper(PaymentSubjectMapper.class);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                var skus = session.getMapper(ZsjosProductSkuMapper.class);
                var relations = session.getMapper(ProductPaymentSubjectMapper.class);
                var subjects = session.getMapper(PaymentSubjectMapper.class);
                TenantContextHolder.setTenantId(1L);
                assertEquals(101L, skus.selectBySkuRef("sku_shared").getSpuId());
                assertNull(skus.selectBySkuRef("sku_deleted"));
                assertEquals(10L, relations.selectByProductIds(List.of(101L)).getFirst().getPaymentSubjectId());
                assertTrue(relations.selectByProductIds(List.of(202L)).isEmpty());
                assertEquals(10L, subjects.selectDefault().getId());
                assertNull(subjects.selectById(20L)); assertNull(subjects.selectById(30L));
                TenantContextHolder.setTenantId(2L); session.clearCache();
                assertEquals(202L, skus.selectBySkuRef("sku_shared").getSpuId());
                assertEquals(20L, relations.selectByProductIds(List.of(101L)).getFirst().getPaymentSubjectId());
                assertEquals(20L, subjects.selectDefault().getId());
                assertNull(subjects.selectById(10L));
            }
        } catch (java.sql.SQLException ex) { throw new IllegalStateException(ex); }
        finally { TenantContextHolder.clear(); }
    }
}
