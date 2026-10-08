package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadQueryMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MediaLeadPageQueryTest {
 @Test void pagesUseFrozenScopeTenantDeletionAndFirstEffectivePurchase() throws Exception {
  var source=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:media-page-"+UUID.randomUUID()+";MODE=MySQL","sa","");
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("media-page",new JdbcTransactionFactory(),source));config.addMapper(MediaLeadQueryMapper.class);
  try(var session=new SqlSessionFactoryBuilder().build(config).openSession()) {
   try(var sql=session.getConnection().createStatement()) {
    sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,submitted_at TIMESTAMP,contribution_user_id_snapshot BIGINT,contribution_dept_id_snapshot BIGINT,contribution_user_name_snapshot VARCHAR,status VARCHAR,source_channel_label_snapshot VARCHAR,lead_category_label_snapshot VARCHAR)");
    sql.execute("INSERT INTO zsjos_lead VALUES(1,9,0,'LD-1','2025-01-02',1,10,'冻结贡献人','valid','历史渠道','历史分类'),(2,9,0,'LD-2','2025-01-02',1,10,'冻结贡献人','invalid',NULL,NULL),(3,99,0,'FOREIGN','2025-01-02',1,10,'foreign','valid',NULL,NULL),(4,9,1,'DELETED','2025-01-02',1,10,'deleted','valid',NULL,NULL),(5,9,0,'OUTSIDE','2025-01-02',2,20,'other','valid',NULL,NULL),(6,9,0,'FUTURE','2025-02-02',1,10,'future','valid',NULL,NULL),(7,9,0,'MISSING','2025-01-02',NULL,10,NULL,'valid',NULL,NULL)");
    sql.execute("CREATE TABLE zsjos_order(tenant_id BIGINT,deleted INT,lead_id BIGINT,status VARCHAR,order_type VARCHAR,effective_at TIMESTAMP,total_amount DECIMAL(18,2) DEFAULT 1280)");
    sql.execute("INSERT INTO zsjos_order(tenant_id,deleted,lead_id,status,order_type,effective_at) VALUES(9,0,1,'effective','first_purchase','2025-01-04'),(9,0,1,'effective','first_purchase','2025-01-03'),(9,0,1,'effective','repurchase','2025-01-02'),(99,0,1,'effective','first_purchase','2025-01-01'),(9,1,1,'effective','first_purchase','2025-01-01'),(9,0,2,'effective','first_purchase','2025-02-01')");
   }
   var mapper=session.getMapper(MediaLeadQueryMapper.class);var start=LocalDateTime.parse("2025-01-01T00:00:00");var end=start.plusMonths(2);var now=start.plusDays(20);
   for(String type:List.of("USER","DEPT","CENTER")) {
    long id=type.equals("USER")?1L:10L;assertEquals(2,mapper.countDetails(9L,type,id,List.of(10L),start,end,now));
    var first=mapper.pageDetails(9L,type,id,List.of(10L),start,end,now,0,1).getFirst();assertEquals("LD-2",first.getLeadNo());assertNull(first.getOrderEffectiveAt());
    var second=mapper.pageDetails(9L,type,id,List.of(10L),start,end,now,1,1).getFirst();assertEquals("LD-1",second.getLeadNo());assertEquals(start.plusDays(2),second.getOrderEffectiveAt());assertEquals("历史渠道",second.getChannelLabel());assertEquals("冻结贡献人",second.getContributorName());
   }
   assertEquals(0,mapper.countDetails(999L,"USER",1L,List.of(),start,end,now));
   assertEquals(0,mapper.countDetails(9L,"CENTER",10L,List.of(),start,end,now));
   assertEquals(0,mapper.countDetails(9L,"UNKNOWN",10L,List.of(10L),start,end,now));
  }
 }
}
