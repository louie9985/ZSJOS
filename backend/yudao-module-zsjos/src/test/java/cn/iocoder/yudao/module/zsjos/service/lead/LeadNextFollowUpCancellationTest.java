package cn.iocoder.yudao.module.zsjos.service.lead;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class LeadNextFollowUpCancellationTest {
 @InjectMocks LeadFollowUpServiceImpl service;
 @Mock LeadMapper leadMapper;
 @Mock OpportunityMapper opportunityMapper;
 @Mock LeadCollaborationService collaborationService;
 @Mock LeadLifecycleTaskService lifecycleTaskService;
 @BeforeEach void tenant() {
 TenantContextHolder.setTenantId(1L);
 var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test");
 com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LeadDO.class);
 com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO.class);
 }
 @AfterEach void clear() { TenantContextHolder.clear(); }
 LeadDO lead(String status) { LeadDO lead = new LeadDO(); lead.setId(7L); lead.setStatus(status); when(leadMapper.selectByIdForUpdate(7L,1L)).thenReturn(lead); return lead; }
 @Test void cancelsWonAndRetainsReason() {
  lead("won"); assertTrue(service.cancelNextFollowUp(7L,9L," 已成交 "));
  verify(lifecycleTaskService).cancelFollowUpReminders(eq(7L),any(),eq("已成交"));
  verify(leadMapper).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  verify(opportunityMapper).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
 }
 @Test void rejectsUnwon() {
  lead("valid"); assertThrows(ServiceException.class,()->service.cancelNextFollowUp(7L,9L,"取消"));
  verifyNoInteractions(lifecycleTaskService,opportunityMapper);
 }
 @Test void rejectsMissingReason() {
  assertThrows(ServiceException.class,()->service.cancelNextFollowUp(7L,9L," "));
  verifyNoInteractions(leadMapper,lifecycleTaskService,opportunityMapper);
 }
 @Test void rejectsUnwritable() {
  LeadDO lead=lead("won"); doThrow(new IllegalStateException("denied")).when(collaborationService).requireCanWriteForUpdate(lead,9L,false);
  assertThrows(IllegalStateException.class,()->service.cancelNextFollowUp(7L,9L,"取消"));
  verifyNoInteractions(lifecycleTaskService,opportunityMapper);
 }
}
