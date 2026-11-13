package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTaskPendingUpdateTest {
    private Connection keepAlive;
    private SqlSession session;
    private JdbcTemplate jdbc;
    private BusinessTaskMapper mapper;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:diagnosis-task-"+UUID.randomUUID()+";MODE=MySQL","sa","");
        keepAlive=source.getConnection();jdbc=new JdbcTemplate(source);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("diagnosis-task",new JdbcTransactionFactory(),source));
        var interceptor=new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        config.addInterceptor(interceptor);config.addMapper(BusinessTaskMapper.class);
        var info=TableInfoHelper.getTableInfo(BusinessTaskDO.class);
        var columns=new ArrayList<String>();columns.add("id BIGINT PRIMARY KEY");
        for(var field:info.getFieldList()){
            var type=field.getPropertyType();
            String sqlType=type==Boolean.class?"INT":Number.class.isAssignableFrom(type)?"BIGINT":type==LocalDateTime.class?"TIMESTAMP":"VARCHAR(4000)";
            columns.add(field.getColumn()+" "+sqlType);
        }
        jdbc.execute("CREATE TABLE zsjos_business_task("+String.join(",",columns)+")");
        jdbc.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,owner_user_id BIGINT)");
        jdbc.execute("CREATE TABLE zsjos_lead_public_sea_pool_cycle(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted INT,status VARCHAR(30),collaborator_user_id BIGINT)");
        jdbc.execute("CREATE TABLE zsjos_lead_public_sea_pool_compatibility_record(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted INT,owner_user_id BIGINT,collaborator_user_id BIGINT)");
        TenantContextHolder.setTenantId(1L);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession();mapper=session.getMapper(BusinessTaskMapper.class);
    }

    @AfterEach void close() throws Exception {
        try {if(session!=null)session.close();if(keepAlive!=null)keepAlive.close();}
        finally {TenantContextHolder.clear();}
    }


    @Test void exactIdUpdateRestoresOnlyTheIntendedPendingTask() {
        jdbc.update("INSERT INTO zsjos_business_task(id,tenant_id,deleted,status,assignee_id,title_snapshot) VALUES(1,1,0,'pending',20,'old'),(2,1,0,'pending',20,'other'),(3,1,0,'completed',20,'done'),(4,2,0,'pending',20,'tenant')");
        assertEquals(1, mapper.updatePendingById(1L,10L,"new","progress",null,null));
        session.commit();
        assertEquals(10L,jdbc.queryForObject("SELECT assignee_id FROM zsjos_business_task WHERE id=1",Long.class));
        assertEquals("other",jdbc.queryForObject("SELECT title_snapshot FROM zsjos_business_task WHERE id=2",String.class));
        assertEquals(0,mapper.updatePendingById(3L,10L,"new","progress",null,null));
        assertEquals(0,mapper.updatePendingById(4L,10L,"new","progress",null,null));
    }
}
