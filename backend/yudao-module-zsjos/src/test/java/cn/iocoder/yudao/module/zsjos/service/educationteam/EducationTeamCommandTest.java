package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.educationteam.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class EducationTeamCommandTest {
 @InjectMocks EducationTeamCommandService service;@Mock EducationTeamScope scope;@Mock EducationTeamCommandMapper commandMapper;@Mock EducationTeamAuditLogMapper auditLogMapper;@Mock LeadMapper leadMapper;@Mock LeadQualificationService qualificationService;@Mock LeadTeamTransferPolicy teamTransferPolicy;@Mock LeadDispatchService dispatchService;
 @BeforeEach void before(){TenantContextHolder.setTenantId(1L);lenient().when(commandMapper.insertIgnore(eq(1L),any())).thenReturn(1);lenient().when(commandMapper.complete(eq(1L),anyLong(),anyString(),anyString())).thenReturn(1);}
 @AfterEach void after(){TenantContextHolder.clear();}
 LeadDO lead(){var row=new LeadDO();row.setId(5L);row.setOwnerUserId(2L);row.setOwnerIdentity("education");row.setStatus("suspended");row.setAssignmentStatus("owned");return row;}
 @Test void restoreUsesIndependentJournalAndPreservesEducationOwnership(){
  var row=lead();when(leadMapper.selectByIdForUpdate(5L,1L)).thenReturn(row);when(scope.managedIds(1L)).thenReturn(Set.of(2L));service.restoreOne(5L,1L,"恢复","request-key");
  var command=ArgumentCaptor.forClass(EducationTeamCommandDO.class);verify(commandMapper).insertIgnore(eq(1L),command.capture());String key=command.getValue().getIdempotencyKey();assertTrue(key.startsWith("edu:"));assertEquals(40,key.length());verify(commandMapper).complete(1L,1L,key,"true");
  verify(qualificationService).restore(eq(5L),eq(1L),argThat(req->req.getIdempotencyKey().equals(key)));verify(scope).requireEducationLead(row);assertEquals("education",row.getOwnerIdentity());
 }
 @Test void commandRejectsUnmanagedOwnerBeforeLifecycleMutation(){when(leadMapper.selectByIdForUpdate(5L,1L)).thenReturn(lead());assertThrows(RuntimeException.class,()->service.restoreOne(5L,1L,"恢复","key"));verifyNoInteractions(qualificationService);}
 @Test void commandRejectsSalesLeadEvenInsideManagedDepartment(){var row=lead();when(leadMapper.selectByIdForUpdate(5L,1L)).thenReturn(row);when(scope.managedIds(1L)).thenReturn(Set.of(2L));doThrow(new IllegalArgumentException()).when(scope).requireEducationLead(row);assertThrows(RuntimeException.class,()->service.restoreOne(5L,1L,"恢复","key"));verifyNoInteractions(qualificationService);}
 @Test void batchChecksEveryLeadBeforeMutations(){var row=lead();when(scope.managedIds(1L)).thenReturn(Set.of(2L));when(leadMapper.selectBatchIds(Set.of(5L,6L))).thenReturn(List.of(row));assertThrows(RuntimeException.class,()->service.validateManagedLeads(List.of(5L,6L),1L));verifyNoInteractions(qualificationService,dispatchService);}
 @Test void batchReplayUsesSameScopedKeyAsInsert(){service.beginBatch("batch-restore",1L,"key","fingerprint");var capture=ArgumentCaptor.forClass(EducationTeamCommandDO.class);verify(commandMapper).insertIgnore(eq(1L),capture.capture());clearInvocations(commandMapper);service.replayBatch("batch-restore",1L,"key","fingerprint");verify(commandMapper).selectByOperatorAndKey(1L,capture.getValue().getIdempotencyKey());}
}
