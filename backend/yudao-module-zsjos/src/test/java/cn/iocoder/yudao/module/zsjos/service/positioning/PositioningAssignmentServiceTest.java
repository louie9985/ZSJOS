package cn.iocoder.yudao.module.zsjos.service.positioning;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplyReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class PositioningAssignmentServiceTest {
 @InjectMocks PositioningAssignmentService service;
 @Mock PositioningServiceCardMapper masterMapper;
 @Mock PositioningCardMapper cardMapper;
 @Mock PositioningCardSubmissionMapper submissionMapper;
 @Mock PositioningApplicationMapper applicationMapper;
 @Mock PositioningApplicationLogMapper logMapper;
 @Mock ServiceRelationMapper relationMapper;
 @Mock MediaAccountMapper accountMapper;
 @Mock MediaAccountObjectPermissionProvider accountPermission;
 @Mock PositioningCardObjectPermissionProvider cardPermission;
 @Mock PermissionApi permissionApi;
 @BeforeEach void tenant(){TenantContextHolder.setTenantId(1L);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 PositioningApplyReqVO request(){return new PositioningApplyReqVO(10L,100L,0,"test-key");}
 void allow(){when(permissionApi.hasAnyPermissions(99L,"zsjos:positioning-card:apply")).thenReturn(true);when(accountPermission.hasPermission(10L,"positioning-apply",99L)).thenReturn(true);}
 MediaAccountDO account(){return new MediaAccountDO().setId(10L).setStudentPersonId(20L).setCreateServiceRelationId(30L);}
 PositioningCardSubmissionDO submission(){return new PositioningCardSubmissionDO().setId(100L).setCardId(1L).setStudentPersonId(20L).setServiceRelationId(30L).setStatus("confirmed");}
 void target(PositioningCardSubmissionDO row){allow();when(accountMapper.selectByIdForUpdate(10L,1L)).thenReturn(account());when(submissionMapper.selectById(100L)).thenReturn(row);}
 @Test void appliesConfirmedVersionAndRecordsAudit(){target(submission());service.apply(request(),99L);verify(applicationMapper).insert(argThat((PositioningApplicationDO x)->x.getSubmissionId()==100L&&x.getVersion()==1));verify(logMapper).insert(argThat((PositioningApplicationLogDO x)->x.getPreviousSubmissionId()==null&&x.getAppliedBy()==99L));}
 @Test void rejectsCrossStudent(){target(submission().setStudentPersonId(21L));assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(applicationMapper);}
 @Test void rejectsCrossCourse(){target(submission().setServiceRelationId(31L));assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(applicationMapper);}
 @Test void rejectsDraft(){target(submission().setStatus("operator_feasibility"));assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(applicationMapper);}
 @Test void missingTenantVisibleSubmissionCannotBeApplied(){target(null);assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(applicationMapper);}
 @Test void rejectsPermissionBeforeQuery(){assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(accountMapper,applicationMapper);}
 @Test void rejectsStaleApplicationVersion(){target(submission());when(applicationMapper.find(10L)).thenReturn(new PositioningApplicationDO().setVersion(2));assertThrows(ServiceException.class,()->service.apply(request(),99L));verify(applicationMapper,never()).updateById(any(PositioningApplicationDO.class));}
 @Test void retriesIdenticalRequestWithoutReapplying(){allow();when(accountMapper.selectByIdForUpdate(10L,1L)).thenReturn(account());when(logMapper.find("test-key")).thenReturn(new PositioningApplicationLogDO().setAccountId(10L).setSubmissionId(100L).setAppliedBy(99L).setExpectedVersion(0));service.apply(request(),99L);verifyNoInteractions(submissionMapper,applicationMapper);}
 @Test void refusesIdempotencyKeyWithChangedPayload(){allow();when(accountMapper.selectByIdForUpdate(10L,1L)).thenReturn(account());when(logMapper.find("test-key")).thenReturn(new PositioningApplicationLogDO().setAccountId(10L).setSubmissionId(101L).setAppliedBy(99L).setExpectedVersion(0));assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(applicationMapper);}
 @Test void historicalConfirmedVersionRemainsSelectable(){assertTrue(PositioningAssignmentService.confirmed(submission().setStatus("superseded")));}
 @Test void multipleCardsNeverChooseNewestAutomatically(){when(cardMapper.selectByService(30L)).thenReturn(List.of(new PositioningCardDO().setId(1L),new PositioningCardDO().setId(2L)));assertNull(service.masterId(30L));verify(masterMapper,never()).insert(any(PositioningServiceCardDO.class));}
 @Test void singleExistingCardIsMaster(){when(cardMapper.selectByService(30L)).thenReturn(List.of(new PositioningCardDO().setId(1L)));assertEquals(1L,service.masterId(30L));}
 @Test void nonMasterCannotContinueEditing(){when(masterMapper.find(30L)).thenReturn(new PositioningServiceCardDO().setCardId(1L));assertThrows(ServiceException.class,()->service.requireMaster(new PositioningCardDO().setId(2L).setServiceRelationId(30L)));}
 @Test void sameConfirmedVersionCanBeAppliedByTwoAccounts(){
   target(submission());
   when(permissionApi.hasAnyPermissions(99L,"zsjos:positioning-card:apply")).thenReturn(true);
   when(accountPermission.hasPermission(11L,"positioning-apply",99L)).thenReturn(true);
   when(accountMapper.selectByIdForUpdate(11L,1L)).thenReturn(account().setId(11L));
   service.apply(request(),99L);
   service.apply(new PositioningApplyReqVO(11L,100L,0,"second-account"),99L);
   verify(applicationMapper,times(2)).insert(argThat((PositioningApplicationDO x)->x.getSubmissionId()==100L));
 }
 @Test void switchingVersionRecordsPreviousVersion(){
   target(submission());
   when(applicationMapper.find(10L)).thenReturn(new PositioningApplicationDO().setId(1L).setAccountId(10L).setSubmissionId(90L).setVersion(2));
   service.apply(new PositioningApplyReqVO(10L,100L,2,"switch"),99L);
   verify(applicationMapper).updateById(argThat((PositioningApplicationDO x)->x.getVersion()==3&&x.getSubmissionId()==100L));
   verify(logMapper).insert(argThat((PositioningApplicationLogDO x)->x.getPreviousSubmissionId()==90L&&x.getSubmissionId()==100L));
 }
 @Test void objectPermissionStillRequiredWithButtonPermission(){
   when(permissionApi.hasAnyPermissions(99L,"zsjos:positioning-card:apply")).thenReturn(true);
   assertThrows(ServiceException.class,()->service.apply(request(),99L));verifyNoInteractions(accountMapper);
 }
 @Test void onlyResponsibleDirectorCanSelectMaster(){
   when(relationMapper.selectByIdForUpdate(30L,1L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO().setContentDirectorUserId(88L));
   assertThrows(ServiceException.class,()->service.selectMaster(30L,1L,99L));verifyNoInteractions(masterMapper);
 }
 @Test void selectsExplicitMasterWithoutChangingHistoricalCards(){
   when(relationMapper.selectByIdForUpdate(30L,1L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO().setPersonId(20L).setContentDirectorUserId(99L));
   var card=new PositioningCardDO().setId(1L).setServiceRelationId(30L).setStudentPersonId(20L).setDirectorUserId(99L);
   when(cardMapper.selectById(1L)).thenReturn(card);
   when(cardMapper.selectByService(30L)).thenReturn(List.of(card,new PositioningCardDO().setId(2L)));
   service.selectMaster(30L,1L,99L);
   verify(masterMapper).insert(argThat((PositioningServiceCardDO x)->x.getCardId()==1L));
   verify(cardMapper,never()).updateById(any(PositioningCardDO.class));
 }
}
