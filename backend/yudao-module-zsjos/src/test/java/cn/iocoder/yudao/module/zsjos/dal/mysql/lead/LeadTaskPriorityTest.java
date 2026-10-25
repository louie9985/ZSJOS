package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LeadTaskPriorityTest {
    @Test
    void priorityPrecedesPaginationAndCustomOrderWithoutBypassingFiltersOrTenant() {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:lead_priority" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(
                com.baomidou.mybatisplus.annotation.DbType.H2));
        config.addInterceptor(plugins);
        config.addMapper(LeadMapper.class);
        var factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(config);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        var ddl = new StringBuilder("CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY");
        for (var field : com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(LeadDO.class).getFieldList()) {
            var type = field.getPropertyType();
            ddl.append(", ").append(field.getColumn()).append(" ").append(type == String.class ? "VARCHAR(2000)"
                    : type == LocalDateTime.class ? "TIMESTAMP" : "BIGINT");
        }
        jdbc.execute(ddl.append(")").toString());
        for (int id = 1; id <= 22; id++) {
            jdbc.update("INSERT INTO zsjos_lead(id,lead_no,tenant_id,deleted,last_activity_at) VALUES(?,?,?,?,?)",
                    id, String.format("K%02d", id), id == 21 ? 2 : 1, id == 22 ? 1 : 0,
                    LocalDateTime.of(2026, 10, 21, 12, 0).plusMinutes(id));
        }
        var req = new LeadManagementPageReqVO(); req.setPageSize(2);
        req.setSortField("leadNo"); req.setSortOrder("ascend");
        var filter = new cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterQuery(Set.of(), Set.of(), false);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(LeadMapper.class);
            var priority = List.of(18L, 19L, 21L, 22L);
            var first = mapper.selectManagementPageByScope(req, List.of(), List.of(), true, filter, null, priority);
            assertEquals(20L, first.getTotal());
            assertEquals(List.of(18L, 19L), first.getList().stream().map(LeadDO::getId).toList());
            req.setPageNo(2);
            assertEquals(List.of(1L, 2L), mapper.selectManagementPageByScope(req, List.of(), List.of(), true,
                    filter, null, priority).getList().stream().map(LeadDO::getId).toList());
            req.setPageNo(1);
            assertEquals(List.of(1L, 2L), mapper.selectManagementPageByScope(req, List.of(), List.of(), true,
                    filter, null).getList().stream().map(LeadDO::getId).toList());
            assertEquals(List.of(19L, 1L), mapper.selectManagementPageByScope(req, List.of(), List.of(), true,
                    filter, null, List.of(19L)).getList().stream().map(LeadDO::getId).toList());
            assertEquals(List.of(1L, 2L), mapper.selectManagementPageByScope(req, List.of(), List.of(), true,
                    filter, List.of(1L, 2L), priority).getList().stream().map(LeadDO::getId).toList());
        }
    }
}
