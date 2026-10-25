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

class BusinessTaskDiagnosisQueryTest {
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

    @Test void accountQueryFiltersBeforeReturningRowsAndRetainsFutureDeadlinesAndOrdering(){
        task(1,1,0,"media_account_diagnosis",10,"pending",20,"2026-10-28T00:00:00");
        task(2,1,0,"media_account_diagnosis",10,"pending",20,"2026-10-21T00:00:00");
        task(3,1,0,"media_account_diagnosis",11,"pending",20,"2026-10-20T00:00:00");
        task(4,1,0,"media_account_diagnosis",10,"pending",21,"2026-10-20T00:00:00");
        task(5,1,0,"other",10,"pending",20,"2026-10-20T00:00:00");
        task(6,1,0,"media_account_diagnosis",10,"completed",20,"2026-10-20T00:00:00");
        task(7,1,1,"media_account_diagnosis",10,"pending",20,"2026-10-20T00:00:00");
        task(8,2,0,"media_account_diagnosis",10,"pending",20,"2026-10-20T00:00:00");
        task(9,1,0,"media_account_diagnosis",10,"pending",20,"2099-01-01T00:00:00");
        task(10,1,0,"media_account_diagnosis",10,"pending",20,"2026-10-21T00:00:00");
        assertEquals(List.of(2L,10L,1L,9L),ids(mapper.selectMyPendingByBusiness(20L,"media_account_diagnosis",10L)));
        assertEquals(List.of(3L,2L,10L,1L,9L),ids(mapper.selectMyPendingByBusiness(20L,"media_account_diagnosis",null)));
        assertTrue(mapper.selectMyPendingByBusiness(99L,"media_account_diagnosis",10L).isEmpty());
        assertTrue(mapper.selectMyPendingByBusiness(20L,"media_account_diagnosis",99L).isEmpty());
        assertThrows(NullPointerException.class,()->mapper.selectMyPendingByBusiness(null,"media_account_diagnosis",10L));
    }

    @Test void scopedQueryPreservesExistingCurrentFollowUpAssigneeRules(){
        jdbc.execute("INSERT INTO zsjos_lead VALUES(100,1,0,20)");
        jdbc.execute("INSERT INTO zsjos_lead_public_sea_pool_cycle VALUES(1,100,1,0,'assigned',21)");
        task(11,1,0,"lead",100,"pending",20,"2026-10-21T00:00:00");
        jdbc.execute("UPDATE zsjos_business_task SET task_type='lead_first_follow_up' WHERE id=11");
        assertEquals(List.of(11L),ids(mapper.selectMyPendingByBusiness(21L,"lead",100L)));
        assertTrue(mapper.selectMyPendingByBusiness(20L,"lead",100L).isEmpty());
    }

    private void task(long id,long tenant,int deleted,String business,long account,String status,long user,String due){
        jdbc.update("INSERT INTO zsjos_business_task(id,tenant_id,deleted,biz_type,biz_id,status,assignee_id,task_type,due_at) VALUES(?,?,?,?,?,?,?,'media_account_diagnosis_7d',?)",
                id,tenant,deleted,business,account,status,user,LocalDateTime.parse(due));
    }
    private List<Long> ids(List<BusinessTaskDO> rows){return rows.stream().map(BusinessTaskDO::getId).toList();}
}
