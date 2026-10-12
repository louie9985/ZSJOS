package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.ConversionSnapshotMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversionSnapshotMapperTest {
    @Test void snapshotsRespectTenantDeletionAndSelectedIds() throws Exception {
        var ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:conversion-snapshots-"+UUID.randomUUID()+";MODE=MySQL","sa","");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("snapshots",new JdbcTransactionFactory(),ds));config.addMapper(ConversionSnapshotMapper.class);
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession()) {
            try(var sql=session.getConnection().createStatement()) {
                sql.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonExtract'");
                sql.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonUnquote'");
                sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,lead_category_label_snapshot VARCHAR,source_channel_label_snapshot VARCHAR)");
                sql.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT)");
                sql.execute("CREATE TABLE zsjos_order_item(id BIGINT,order_id BIGINT,tenant_id BIGINT,deleted INT,product_ref VARCHAR,product_snapshot VARCHAR)");
                sql.execute("INSERT INTO zsjos_lead VALUES(1,1,0,'KZ-1','分类快照','渠道快照'),(2,2,0,'FOREIGN',NULL,NULL),(3,1,1,'DELETED',NULL,NULL),(4,1,0,'OUTSIDE',NULL,NULL)");
                sql.execute("INSERT INTO zsjos_order VALUES(10,1,0),(20,2,0),(30,1,1),(40,1,0)");
                sql.execute("INSERT INTO zsjos_order_item VALUES(1,10,1,0,'p1','{\"name\":\"商品快照\"}'),(2,10,1,0,'p2',NULL),(3,20,2,0,'foreign','{}'),(4,30,1,0,'deleted-order','{}'),(5,10,1,1,'deleted-item','{}'),(6,40,1,0,'outside','{}'),(7,10,2,0,'foreign-item','{}')");
            }
            var mapper=session.getMapper(ConversionSnapshotMapper.class);
            var leads=mapper.leads(1L,List.of(1L,2L,3L));assertEquals(1,leads.size());assertEquals("KZ-1",leads.getFirst().getLeadNo());assertEquals("渠道快照",leads.getFirst().getChannelLabel());
            var products=mapper.products(1L,List.of(10L,20L,30L));assertEquals(2,products.size());assertEquals("商品快照",products.getFirst().getLabel());assertNull(products.getLast().getSnapshot());
            assertEquals("FOREIGN",mapper.leads(2L,List.of(1L,2L,3L)).getFirst().getLeadNo());
        }
    }
}
