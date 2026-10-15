package cn.iocoder.yudao.module.zsjos.service.calendarrestday;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendarrestday.CalendarRestDayMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Expression;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="REST_DAY_TEST_DB",matches="rest_days_[a-zA-Z0-9_]+")
class CalendarRestDayMySqlTest {
    static CalendarRestDayService service;
    static AtomicLong ids=new AtomicLong(0);
    LocalDate date;
    @BeforeAll static void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+System.getenv("REST_DAY_TEST_DB")+"?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai","root",System.getenv("REST_DAY_TEST_PASSWORD"));
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(CalendarRestDayMapper.class);
        var plugin=new MybatisPlusInterceptor(); plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(){
            public Expression getTenantId(){return new LongValue(TenantContextHolder.getRequiredTenantId());}
        })); config.addInterceptor(plugin);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        var target=new CalendarRestDayService();
        ReflectionTestUtils.setField(target,"mapper",new SqlSessionTemplate(factory.getObject()).getMapper(CalendarRestDayMapper.class));
        ReflectionTestUtils.setField(target,"access",mock(CalendarRestDayPermissionProvider.class));
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        service=(CalendarRestDayService)proxy.getProxy();
    }
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);date=LocalDate.of(2035,1,1).plusDays(ids.incrementAndGet()*10);}
    @AfterEach void after(){TenantContextHolder.clear();}
    CalendarRestDayChangeVO change(LocalDate day,boolean rest,long version){return new CalendarRestDayChangeVO().setDate(day).setRest(rest).setVersion(version);}
    List<CalendarRestDayRespVO> save(CalendarRestDayChangeVO... changes){return service.update(1L,7L,new CalendarRestDayBatchReqVO().setChanges(List.of(changes)));}
    @Test void sharedReadsCancelRemarkAndTenantIsolation(){
        save(change(date,true,0));
        assertTrue(service.list(1L,8L,date,date).getFirst().getRest());
        assertEquals(1L,save(change(date,true,0)).getFirst().getVersion());
        assertFalse(save(change(date,false,1)).getFirst().getRest());
        assertEquals(3L,save(change(date,true,2)).getFirst().getVersion());
        TenantContextHolder.setTenantId(2L);
        assertTrue(service.list(2L,8L,date,date).isEmpty());
    }
    @Test void conflictRollsBackEarlierDatesAndEnsuredRows(){
        save(change(date.plusDays(1),true,0));
        assertThrows(ServiceException.class,()->save(change(date,true,0),change(date.plusDays(1),false,0)));
        assertTrue(service.list(1L,7L,date,date).isEmpty());
        assertTrue(service.list(1L,7L,date.plusDays(1),date.plusDays(1)).getFirst().getRest());
    }
    @Test void concurrentFirstWritesAreIdempotent() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Long> task=()->{TenantContextHolder.setTenantId(1L);start.await();try{return save(change(date,true,0)).getFirst().getVersion();}finally{TenantContextHolder.clear();}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertEquals(1L,a.get(20,TimeUnit.SECONDS));assertEquals(1L,b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,service.list(1L,7L,date,date).size());
    }
    @Test void disjointDatesDoNotOverwriteEachOther() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<2;i++){final var day=date.plusDays(i);futures.add(pool.submit(()->{TenantContextHolder.setTenantId(1L);try{start.await();save(change(day,true,0));}catch(InterruptedException e){throw new RuntimeException(e);}finally{TenantContextHolder.clear();}}));}
            start.countDown();for(var future:futures)future.get(20,TimeUnit.SECONDS);
        }
        assertEquals(2,service.list(1L,7L,date,date.plusDays(1)).size());
    }
}
