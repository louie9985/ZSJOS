package cn.iocoder.yudao.module.zsjos.service.lead;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SubordinateTransferTransactionTest {
 @Test void salesTransferRollsBackLeadHistoryTasksAndCommandOnFailure() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);
  for(String table:List.of("lead_fixture","history_fixture","task_fixture","command_fixture"))jdbc.execute("CREATE TABLE "+table+"(id INT)");
  var target=new SubordinateSalesCommandService();
  var leads=mock(LeadMapper.class);var commands=mock(SubordinateSalesCommandMapper.class);
  var permission=mock(LeadObjectPermissionService.class);var scope=mock(SubordinateSalesScope.class);
  var qualification=mock(LeadQualificationService.class);var audit=mock(SubordinateSalesAuditLogMapper.class);
  ReflectionTestUtils.setField(target,"leadMapper",leads);ReflectionTestUtils.setField(target,"commandMapper",commands);
  ReflectionTestUtils.setField(target,"permissionService",permission);ReflectionTestUtils.setField(target,"scope",scope);
  ReflectionTestUtils.setField(target,"qualificationService",qualification);ReflectionTestUtils.setField(target,"auditLogMapper",audit);
  var lead=new LeadDO().setId(1L).setOwnerUserId(20L).setOwnerIdentity("sales").setStatus("suspended").setAssignmentStatus("owned");
  when(leads.selectByIdForUpdate(1L,1L)).thenReturn(lead);when(permission.getManagedUserIds(10L)).thenReturn(Set.of(20L,30L));
  var member=new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO();member.setId(30L);
  when(scope.managed(10L)).thenReturn(List.of(member));
  var assignment=mock(LeadAssignmentService.class);ReflectionTestUtils.setField(target,"assignmentService",assignment);when(assignment.isEligibleSalesUser(30L)).thenReturn(true);
  when(commands.insertIgnore(eq(1L),any())).thenAnswer(call->{jdbc.update("INSERT INTO command_fixture VALUES(1)");return 1;});
  when(commands.complete(anyLong(),anyLong(),anyString(),anyString())).thenReturn(1);
  doAnswer(call->{for(String table:List.of("lead_fixture","history_fixture","task_fixture"))jdbc.update("INSERT INTO "+table+" VALUES(1)");throw new IllegalStateException("fixture failure after writes");})
    .when(qualification).transfer(eq(1L),eq(10L),any());
  var factory=new ProxyFactory(target);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
  var service=(SubordinateSalesCommandService)factory.getProxy();
  TenantContextHolder.setTenantId(1L);
  try {
   assertThrows(IllegalStateException.class,()->service.transferOne(1L,30L,10L,"fixture","failed"));
   for(String table:List.of("lead_fixture","history_fixture","task_fixture","command_fixture"))assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
   doAnswer(call->{for(String table:List.of("lead_fixture","history_fixture","task_fixture"))jdbc.update("INSERT INTO "+table+" VALUES(1)");return null;})
     .when(qualification).transfer(eq(1L),eq(10L),any());
   service.transferOne(1L,30L,10L,"fixture","success");
   for(String table:List.of("lead_fixture","history_fixture","task_fixture","command_fixture"))assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
  } finally {TenantContextHolder.clear();}
 }
}
