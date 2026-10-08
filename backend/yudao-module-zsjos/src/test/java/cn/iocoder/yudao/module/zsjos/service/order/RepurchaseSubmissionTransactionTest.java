package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRepurchaseReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Tests the real orchestration transaction; remote order/identity collaborators persist into isolated H2. */
class RepurchaseSubmissionTransactionTest {
 private JdbcTemplate jdbc;
 private RepurchaseSubmissionService service;
 private SalesOrderService orders;
 private RepurchaseCustomerService customers;
 private AtomicInteger sent;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);
  var ds=new DriverManagerDataSource("jdbc:h2:mem:repurchase_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE person(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,person_id BIGINT)");
  var raw=new RepurchaseSubmissionService();customers=mock(RepurchaseCustomerService.class);orders=mock(SalesOrderService.class);
  var identity=mock(PersonIdentityWriteService.class);var mapper=mock(PersonMapper.class);
  when(identity.resolveOrCreate(anyString(),anyString(),isNull(),eq("active"))).thenAnswer(a->{
   assertTrue(TransactionSynchronizationManager.isActualTransactionActive());jdbc.update("INSERT INTO person VALUES(10)");
   var p=new PersonDO().setId(10L);p.setTenantId(1L);return p;
  });
  ReflectionTestUtils.setField(raw,"customers",customers);ReflectionTestUtils.setField(raw,"personMapper",mapper);
  ReflectionTestUtils.setField(raw,"identityWriter",identity);ReflectionTestUtils.setField(raw,"orders",orders);
  var factory=new ProxyFactory(raw);factory.setProxyTargetClass(true);
  factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
  service=(RepurchaseSubmissionService)factory.getProxy();sent=new AtomicInteger();
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();jdbc.execute("SHUTDOWN");}
 private SalesOrderRepurchaseReqVO request(){var r=new SalesOrderRepurchaseReqVO();r.setCustomerName("测试");r.setCustomerMobile("13800138000");return r;}
 private void order(boolean fail){when(orders.createMatchedRepurchase(eq(10L),eq(20L),any())).thenAnswer(a->{
  jdbc.update("INSERT INTO orders VALUES(99,10)");
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){sent.incrementAndGet();}});
  if(fail)throw new IllegalStateException("approval failure");return 99L;
 });}
 @Test void entirePersonAndOrderRollbackOnApprovalFailure(){order(true);assertThrows(IllegalStateException.class,()->service.submit(20L,request()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM person",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));assertEquals(0,sent.get());}
 @Test void successCommitsBothAndPublishesAfterCommit(){order(false);assertEquals(99L,service.submit(20L,request()));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM person",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));assertEquals(1,sent.get());}
 @Test void identityFailureRollsBackNewCustomer(){doThrow(new IllegalStateException("identity changed")).when(customers).requireIdentity(eq(10L),any());assertThrows(IllegalStateException.class,()->service.submit(20L,request()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM person",Integer.class));verifyNoInteractions(orders);}
}
