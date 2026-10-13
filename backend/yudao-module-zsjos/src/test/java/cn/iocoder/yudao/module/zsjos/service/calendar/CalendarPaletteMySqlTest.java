package cn.iocoder.yudao.module.zsjos.service.calendar;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarColorSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarPaletteMapper;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="CALENDAR_COLOR_TEST_DB", matches="cal_colors_[a-zA-Z0-9_]+")
class CalendarPaletteMySqlTest {
 static CalendarPaletteService service;
 static AtomicLong ids=new AtomicLong(System.currentTimeMillis());
 long user;
 @BeforeAll static void setup() throws Exception {
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+System.getenv("CALENDAR_COLOR_TEST_DB")+"?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai","root",System.getenv("CALENDAR_COLOR_TEST_PASSWORD"));
  var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(CalendarPaletteMapper.class);
  var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(){
   public Expression getTenantId(){return new LongValue(TenantContextHolder.getRequiredTenantId());}
  }));config.addInterceptor(plugin);
  var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);
  var target=new CalendarPaletteService();
  ReflectionTestUtils.setField(target,"mapper",new SqlSessionTemplate(factory.getObject()).getMapper(CalendarPaletteMapper.class));
  ReflectionTestUtils.setField(target,"access",mock(CalendarPalettePermissionProvider.class));
  var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
  proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
  service=(CalendarPaletteService)proxy.getProxy();
 }
 @BeforeEach void before(){TenantContextHolder.setTenantId(1L);user=ids.incrementAndGet();}
 @AfterEach void after(){TenantContextHolder.clear();}
 CalendarColorSaveReqVO req(String name){return new CalendarColorSaveReqVO().setName(name).setColor("#aabbcc");}
 @Test void persistsAcrossReadsAndSeparatesUsersAndCalendarTypes(){
  var added=service.mutate("PERSONAL",user,null,req("会议"),false).colors().get(0);
  assertEquals("#AABBCC",service.get("PERSONAL",user).colors().get(0).color());
  assertTrue(service.get("PERSONAL",user+1000).colors().isEmpty());
  assertTrue(service.get("COURSE",user).colors().stream().noneMatch(c -> c.id().equals(added.id())));
  assertThrows(ServiceException.class,()->service.mutate("COURSE",user,added.id(),null,true));
  service.mutate("PERSONAL",user,added.id(),req("培训"),false);
  assertEquals("培训",service.get("PERSONAL",user).colors().get(0).name());
  service.mutate("PERSONAL",user,added.id(),null,true);
  assertTrue(service.get("PERSONAL",user).colors().isEmpty());
 }
 @Test void concurrentLastSlotHasExactlyOneWinner() throws Exception {
  for(int i=0;i<19;i++)service.mutate("PERSONAL",user,null,req("颜色"+i),false);
  var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> task=()->{TenantContextHolder.setTenantId(1L);start.await();try{service.mutate("PERSONAL",user,null,req(Thread.currentThread().getName()),false);return true;}catch(ServiceException e){assertEquals(CalendarColors.LIMIT.getCode(),e.getCode());return false;}finally{TenantContextHolder.clear();}};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
  }
  assertEquals(20,service.get("PERSONAL",user).colors().size());
 }
 @Test void sharedEntriesCanBeEditedByAnotherUserAndEmptyDoesNotReseed() {
  for(var item:service.get("COURSE",user).colors()) service.mutate("COURSE",user,item.id(),null,true);
  assertTrue(service.get("COURSE",user+1).colors().isEmpty());
  assertTrue(service.get("COURSE",user+1).presets().isEmpty());
  var added=service.mutate("COURSE",user,null,req("公共颜色"),false).colors().get(0);
  service.mutate("COURSE",user+1,added.id(),req("修改共享颜色"),false);
  assertEquals("修改共享颜色",service.get("COURSE",user).colors().get(0).name());
  service.mutate("COURSE",user+1,added.id(),null,true);
  assertTrue(service.get("COURSE",user).colors().isEmpty());
 }
}
