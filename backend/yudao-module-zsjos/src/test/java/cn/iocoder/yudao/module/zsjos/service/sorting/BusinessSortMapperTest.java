package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.WithdrawalMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderManagementScope;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.baomidou.mybatisplus.annotation.DbType;
import net.sf.jsqlparser.expression.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessSortMapperTest {
    @Test void realMapperPaginationKeepsTenantVisibilityFiltersAndNullOrdering() throws Exception {
        String mysql = System.getenv("BUSINESS_SORT_TEST_MYSQL_URL");
        var sourceConfig = new DriverManagerDataSource(mysql == null ? "jdbc:h2:mem:sort"+UUID.randomUUID()+";MODE=MySQL" : mysql,
                mysql == null ? "sa" : System.getenv("BUSINESS_SORT_TEST_MYSQL_USER"), mysql == null ? "" : System.getenv("BUSINESS_SORT_TEST_MYSQL_PASSWORD"));
        try(var keepAlive=sourceConfig.getConnection()) {
            // MySQL uses connection-local temporary tables; persistent business tables are never changed.
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(keepAlive, true);
            String temporary = mysql == null ? "" : "TEMPORARY ";
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("sort",new JdbcTransactionFactory(),source));
            var interceptor=new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                public Expression getTenantId() { return new LongValue(TenantContextHolder.getRequiredTenantId()); }
            }));
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(mysql == null ? DbType.H2 : DbType.MYSQL));config.addInterceptor(interceptor);
            config.addMapper(SalesOrderMapper.class);config.addMapper(WithdrawalMapper.class);
            config.addMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackSearchMapper.class);
            var factory=new MybatisSqlSessionFactoryBuilder().build(config);var jdbc=new JdbcTemplate(source);
            table(jdbc,SalesOrderDO.class,temporary);table(jdbc,WithdrawalDO.class,temporary);
            jdbc.execute("CREATE "+temporary+"TABLE zsjos_cashback(id BIGINT,tenant_id BIGINT,deleted INT,type VARCHAR(30),amount DECIMAL(12,2),base_amount DECIMAL(12,2),rate_snapshot DECIMAL(12,4),generated_at TIMESTAMP,available_at TIMESTAMP)");
            jdbc.execute("INSERT INTO zsjos_cashback VALUES(1,1,0,'deal',10,10,0.1,NULL,NULL),(2,1,0,'deal',2,2,0.2,NULL,NULL),(3,1,0,'valid',1,1,0.9,NULL,NULL),(4,2,0,'deal',0,0,0,NULL,NULL),(5,1,1,'deal',0,0,0,NULL,NULL)");
            for(int i=1;i<=45;i++) {
                jdbc.update("INSERT INTO zsjos_order(id,tenant_id,deleted,status,submitter_user_id,submitted_at,effective_at,update_time) VALUES(?,1,0,'effective',7,?,NULL,CURRENT_TIMESTAMP)",i,java.time.LocalDateTime.of(2026,1,1,0,0).plusDays(46-i));
                jdbc.update("INSERT INTO zsjos_withdrawal(id,tenant_id,deleted,status,applicant_user_id,application_amount,submitted_at) VALUES(?,1,0,'approved',7,?,CURRENT_TIMESTAMP)",i,46-i);
            }
            jdbc.update("INSERT INTO zsjos_order(id,tenant_id,deleted,status,submitter_user_id,submitted_at) VALUES(90,2,0,'effective',7,NULL),(91,1,0,'effective',8,NULL),(92,1,1,'effective',7,NULL)");
            jdbc.update("INSERT INTO zsjos_withdrawal(id,tenant_id,deleted,status,applicant_user_id,application_amount) VALUES(90,2,0,'approved',7,0),(91,1,0,'approved',8,0),(92,1,1,'approved',7,0)");
            TenantContextHolder.setTenantId(1L);
            try(var session=factory.openSession()) {
                var orders=session.getMapper(SalesOrderMapper.class);var withdrawals=session.getMapper(WithdrawalMapper.class);
                var cashbacks=session.getMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackSearchMapper.class);
                var cashbackQuery=new cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery("c.tenant_id=1 AND c.deleted=0",Map.of());
                assertEquals(List.of(2L,1L,3L),cashbacks.sortedPage(cashbackQuery,BusinessSortSql.cashback("baseAmount","ascend"),0,20).stream().map(cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO::getId).toList());
                assertEquals(List.of(2L),cashbacks.sortedPage(cashbackQuery,BusinessSortSql.cashback("amount","ascend"),1,1).stream().map(cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO::getId).toList());
                var scope=new SalesOrderManagementScope(false,true,Set.of(),Set.of(7L));
                var orderReq=new SalesOrderMyPageReqVO();orderReq.setPageNo(2);orderReq.setPageSize(20);orderReq.setSortField("submittedAt");orderReq.setSortOrder("ascend");
                var orderPage=orders.selectManagementPage(scope,orderReq,null);
                assertEquals(45L,orderPage.getTotal());assertEquals(25L,orderPage.getList().getFirst().getId());
                assertEquals(6L,orderPage.getList().getLast().getId());
                orderReq.setPageNo(1);orderReq.setSortField("effectiveAt");
                assertEquals(List.of(3L,2L,1L),orders.selectManagementPage(scope,orderReq,List.of(1L,2L,3L,90L,91L,92L)).getList().stream().map(SalesOrderDO::getId).toList());
                var req=new WithdrawalPageReqVO();req.setPageNo(2);req.setPageSize(20);req.setSortField("applicationAmount");req.setSortOrder("ascend");
                var page=withdrawals.selectPageByApplicant(req,7L);
                assertEquals(45L,page.getTotal());assertEquals(25L,page.getList().getFirst().getId());
                req.setPageNo(1);req.setSortOrder("descend");
                assertEquals(List.of(1L,2L,3L),withdrawals.selectPageByApplicant(req,7L,List.of(1L,2L,3L,90L,91L,92L)).getList().stream().map(WithdrawalDO::getId).toList());
                req.setSortField(null);req.setSortOrder(null);
                assertEquals(45L,withdrawals.selectPageByApplicant(req,7L).getList().getFirst().getId());
            }
        } finally { TenantContextHolder.clear(); }
    }

    private void table(JdbcTemplate jdbc, Class<?> type, String temporary) {
        var info=TableInfoHelper.getTableInfo(type);List<String> columns=new ArrayList<>();columns.add("id BIGINT PRIMARY KEY");
        info.getFieldList().forEach(field->{
            Class<?> javaType=field.getPropertyType();
            String sqlType=javaType==Boolean.class||javaType==boolean.class?"INT":Number.class.isAssignableFrom(javaType)?"DECIMAL(20,4)":javaType==java.time.LocalDateTime.class?"TIMESTAMP":"TEXT";
            columns.add(field.getColumn()+" "+sqlType);
        });
        jdbc.execute("CREATE "+temporary+"TABLE "+info.getTableName()+"("+String.join(",",columns)+")");
    }
}
