package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationPoolPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import jakarta.validation.Validation;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationPoolPageTest {
    @Test void groupsIntersectFiltersBeforePaginationAndPreserveLegacyQueries() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:pool" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var keepAlive = source.getConnection()) {
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("pool", new JdbcTransactionFactory(), source));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
            config.addInterceptor(interceptor);
            config.addMapper(RegistrationCaseMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            var jdbc = new JdbcTemplate(source);
            var info = TableInfoHelper.getTableInfo(RegistrationCaseDO.class);
            List<String> columns = new ArrayList<>(List.of("id BIGINT PRIMARY KEY"));
            info.getFieldList().forEach(field -> {
                var type = field.getPropertyType();
                String sqlType = type == Boolean.class || type == boolean.class ? "INT" : Number.class.isAssignableFrom(type) ? "BIGINT" : type == java.time.LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(255)";
                columns.add(field.getColumn() + " " + sqlType);
            });
            jdbc.execute("CREATE TABLE zsjos_registration_case(" + String.join(",", columns) + ")");
            String[] statuses = {"pending", "processing", "completed", "cancelled"};
            for (int id = 1; id <= 48; id++) {
                jdbc.update("INSERT INTO zsjos_registration_case(id,order_id,status,tenant_id,deleted,update_time) VALUES(?,?,?,1,0,CURRENT_TIMESTAMP)", id, id, statuses[(id - 1) % 4]);
            }
            jdbc.update("INSERT INTO zsjos_registration_case(id,status,tenant_id,deleted) VALUES(99,'pending',1,1)");
            try (var session = factory.openSession()) {
                var mapper = session.getMapper(RegistrationCaseMapper.class);
                var req = new RegistrationPoolPageReqVO();
                req.setPageSize(20);
                req.setPageNo(2);
                req.setStatusGroup("pending");
                var pending = mapper.selectPoolPage(req, null, null);
                assertEquals(24L, pending.getTotal());
                assertEquals(4, pending.getList().size());
                assertTrue(pending.getList().stream().allMatch(row -> Set.of("pending", "processing").contains(row.getStatus())));
                req.setStatusGroup("processed");
                var processed = mapper.selectPoolPage(req, null, null);
                assertEquals(24L, processed.getTotal());
                assertEquals(4, processed.getList().size());
                assertTrue(processed.getList().stream().allMatch(row -> Set.of("completed", "cancelled").contains(row.getStatus())));
                req.setPageNo(1);
                assertEquals(1L, mapper.selectPoolPage(req, List.of(1L, 3L, 4L), List.of(1L, 4L)).getTotal());
                assertEquals(0L, mapper.selectPoolPage(req, List.of(), null).getTotal());
                req.setStatus("pending");
                assertEquals(0L, mapper.selectPoolPage(req, null, null).getTotal());
                req.setStatusGroup(null);
                assertEquals(12L, mapper.selectPoolPage(req, null, null).getTotal());
                req.setStatus(null);
                assertEquals(48L, mapper.selectPoolPage(req, null, null).getTotal());
                req.setStatusGroup("all");
                assertEquals(48L, mapper.selectPoolPage(req, null, null).getTotal());
            }
        }
    }

    @Test void rejectsUnknownGroupsButAcceptsOmittedLegacyParameter() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var req = new RegistrationPoolPageReqVO();
            assertTrue(validator.validate(req).isEmpty());
            for (String group : List.of("pending", "processed", "all")) {
                req.setStatusGroup(group);
                assertTrue(validator.validate(req).isEmpty());
            }
            req.setStatusGroup("unknown");
            assertFalse(validator.validate(req).isEmpty());
        }
    }
}
