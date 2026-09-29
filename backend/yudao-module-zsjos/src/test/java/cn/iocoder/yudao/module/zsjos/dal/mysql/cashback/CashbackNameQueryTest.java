package cn.iocoder.yudao.module.zsjos.dal.mysql.cashback;

import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CashbackNameQueryTest {
    @Test void nameOrNumberMatchesStayInsideTenantPersonalFiltersAndPagination() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:names"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new LongValue(1); }
        }));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2)); config.addInterceptor(plugins);
        config.addMapper(CashbackMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config); var jdbc = new JdbcTemplate(source);
        var ddl = new StringBuilder("CREATE TABLE zsjos_cashback(id BIGINT PRIMARY KEY");
        for (var field : TableInfoHelper.getTableInfo(CashbackDO.class).getFieldList()) {
            var t=field.getPropertyType(); String sql=t==String.class?"VARCHAR(2000)":t==LocalDateTime.class?"TIMESTAMP":t==java.math.BigDecimal.class?"DECIMAL(12,2)":"BIGINT";
            ddl.append(", ").append(field.getColumn()).append(" ").append(sql);
        }
        jdbc.execute(ddl.append(")").toString());
        jdbc.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR(100))");
        jdbc.execute("INSERT INTO zsjos_lead VALUES(10,1,0,'LEAD-ABC'),(20,2,0,'LEAD-FOREIGN'),(30,1,1,'LEAD-DELETED')");
        for (int i=1;i<=7;i++) jdbc.update("INSERT INTO zsjos_cashback(id,tenant_id,deleted,beneficiary_user_id,status,type,cashback_no,lead_id) VALUES(?,?,?,?,?,?,?,?)",
                i,i==5?2:1,i==6?1:0,i==4?8:7,i==7?"blocked":"available","valid","CB-"+i,i<=3?Integer.valueOf(i*10):null);
        try(var session=factory.openSession()) {
            var mapper=session.getMapper(CashbackMapper.class); var req=new CashbackPageReqVO(); req.setPageSize(1); req.setKeyword(" 姓名 ");
            req.setType("valid"); req.setStatus("available");
            var names=Set.of(1L,2L,3L,4L,5L,6L,7L);
            var result=mapper.selectCashbackPage(req,7L,null,names);
            assertEquals(3L,result.getTotal()); assertEquals(3L,result.getList().getFirst().getId());
            req.setPageNo(2); assertEquals(2L,mapper.selectCashbackPage(req,7L,null,names).getList().getFirst().getId());
            req.setPageNo(1); assertEquals(1L,mapper.selectCashbackPage(req,7L,List.of(2L),names).getTotal());
            assertEquals(0L,mapper.selectCashbackPage(req,7L,List.of(),names).getTotal());
            assertEquals(0L,mapper.selectCashbackPage(req,7L,null,Set.of()).getTotal());
            req.setKeyword("CB-1"); assertEquals(1L,mapper.selectCashbackPage(req,7L).getTotal());
            req.setKeyword("LEAD-ABC"); assertEquals(1L,mapper.selectCashbackPage(req,7L).getTotal());
            for(String term:List.of("LEAD-FOREIGN","LEAD-DELETED")) { req.setKeyword(term); assertEquals(0L,mapper.selectCashbackPage(req,7L).getTotal()); }
            req.setKeyword("  "); assertEquals(3L,mapper.selectCashbackPage(req,7L).getTotal());
        }
    }
}
