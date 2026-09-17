package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in read-only verification against the configured development MySQL; no fixture writes. */
@EnabledIfEnvironmentVariable(named = "ZSJOS_CLASS_MYSQL_VERIFY", matches = "true")
class DeliveryClassMysqlOptionsTest {
    @Test
    void optionsMatchDatabaseServingClassesWithTenantInterceptor() throws Exception {
        long tenant = Long.parseLong(System.getenv("ZSJOS_CLASS_MYSQL_TENANT"));
        var datasource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                System.getenv("ZSJOS_CLASS_MYSQL_URL"), System.getenv("ZSJOS_CLASS_MYSQL_USER"),
                System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("class-options-read-only", new JdbcTransactionFactory(), datasource));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public LongValue getTenantId() { return new LongValue(tenant); }
        }));
        config.addInterceptor(plugins);
        config.addMapper(DeliveryClassMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            session.getConnection().setReadOnly(true);
            var service = new DeliveryClassServiceImpl();
            ReflectionTestUtils.setField(service, "mapper", session.getMapper(DeliveryClassMapper.class));
            List<Long> expected = new ArrayList<>();
            try (var stmt = session.getConnection().prepareStatement("SELECT id FROM zsjos_delivery_class WHERE tenant_id=? AND deleted=0 AND status='SERVING' ORDER BY system_class DESC,class_name,id")) {
                stmt.setLong(1, tenant);
                var rs = stmt.executeQuery();
                while (rs.next()) expected.add(rs.getLong(1));
            }
            assertFalse(expected.isEmpty(), "Choose a development tenant with serving classes for this regression");
            var all = service.options(null, true);
            assertEquals(expected, all.stream().map(row -> row.getId()).toList());
            assertEquals(all.stream().filter(row -> !Boolean.TRUE.equals(row.getSystemClass())).map(row -> row.getId()).toList(),
                    service.options(null, false).stream().map(row -> row.getId()).toList());
            assertEquals(all.stream().filter(row -> Boolean.TRUE.equals(row.getSystemClass())).map(row -> row.getId()).toList(),
                    service.options(-1L, true).stream().map(row -> row.getId()).toList());
            assertEquals(List.of(), service.options(-1L, false));
        }
    }
}
