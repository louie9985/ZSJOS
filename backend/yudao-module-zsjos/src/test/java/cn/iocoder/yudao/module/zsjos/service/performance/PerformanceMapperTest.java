package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFactMapper;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceMapperTest {
 @Test void statementsRetainScopeAndTenant(){var config=new Configuration();new MapperAnnotationBuilder(config,PerformanceFactMapper.class).parse();for(String scope:List.of("SELF","USER","DEPT","CENTER"))for(String method:List.of("orders","receipts","products","tasks","assignments","followUps")){var sql=config.getMappedStatement(PerformanceFactMapper.class.getName()+"."+method).getBoundSql(Map.of("tenant",991L,"type",scope,"id",12L,"users",List.of(12L))).getSql();assertTrue(sql.contains("tenant_id"),sql);assertFalse(sql.contains("IN ()"),sql);if(List.of("orders","receipts","products").contains(method)){if("DEPT".equals(scope))assertTrue(sql.contains("a.dept_id=?"));if("CENTER".equals(scope))assertTrue(sql.contains("a.center_id=?"));}}}
}
