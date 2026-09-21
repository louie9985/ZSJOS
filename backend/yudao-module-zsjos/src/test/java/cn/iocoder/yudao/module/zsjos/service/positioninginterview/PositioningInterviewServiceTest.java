package cn.iocoder.yudao.module.zsjos.service.positioninginterview;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview.vo.PositioningInterviewVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PositioningInterviewServiceTest {
 @Test void administratorReadsHistoricalDraftWithoutWriteActions(){
  relation.setStatus("completed");
  when(permissionApi.hasAnyPermissions(30L,PositioningInterviewService.QUERY)).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(30L)).thenReturn(true);
  when(relationMapper.selectById(9L)).thenReturn(relation);
  when(relationMapper.selectMediaReadByPersonIds(List.of(2L),null)).thenReturn(List.of(relation));
  when(mapper.selectOne(any())).thenReturn(row);
  Context result=service.context(9L,30L);
  assertEquals("draft",result.getStatus());
  assertEquals(List.of("VIEW_POSITIONING_INTERVIEW"),result.getAvailableActions());
  verify(mapper,never()).insert(any(PositioningInterviewDO.class));
 }
 @Test void administratorReadDoesNotAuthorizeDraftWrite(){
  when(permissionApi.hasAnyPermissions(30L,PositioningInterviewService.EDIT)).thenReturn(true);
  when(relationMapper.selectById(9L)).thenReturn(relation);
  assertThrows(ServiceException.class,()->service.save(9L,request("NOT_COMMUNICATED"),30L,false));
  verify(mapper,never()).lockStudent(anyLong(),anyLong());
 }
 @Test void administratorCannotReadAnotherTenantInterview(){
  relation.setTenantId(2L);
  when(permissionApi.hasAnyPermissions(30L,PositioningInterviewService.QUERY)).thenReturn(true);
  when(relationMapper.selectById(9L)).thenReturn(relation);
  assertThrows(ServiceException.class,()->service.context(9L,30L));
  verifyNoInteractions(mapper);
 }
 @Test void administratorDownloadsOnlyTheSelectedRelationsBoundAttachment(){
  relation.setStatus("completed"); row.setTenantId(1L);
  when(permissionApi.hasAnyPermissions(30L,PositioningInterviewService.QUERY)).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(30L)).thenReturn(true);
  when(relationMapper.selectById(9L)).thenReturn(relation);
  when(relationMapper.selectMediaReadByPersonIds(List.of(2L),null)).thenReturn(List.of(relation));
  var attachment=new PositioningInterviewAttachmentDO();attachment.setFileId(55L);attachment.setStudentPersonId(2L);attachment.setInterviewId(3L);attachment.setUploadedBy(7L);
  when(attachmentMapper.selectOne(any())).thenReturn(attachment);
  when(mapper.selectById(3L)).thenReturn(row);
  when(fileApi.presignGetUrl(55L,300)).thenReturn("https://example.test/file");
  assertEquals("https://example.test/file",service.download(9L,30L,55L).getUrl());
  row.setServiceRelationId(10L);
  assertThrows(ServiceException.class,()->service.download(9L,30L,55L));
  verify(fileApi,times(1)).presignGetUrl(55L,300);
 }
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
 @InjectMocks PositioningInterviewService service;
 @Mock PositioningInterviewMapper mapper;
 @Mock PositioningInterviewItemMapper itemMapper;
 @Mock PositioningInterviewAttachmentMapper attachmentMapper;
 @Mock ServiceRelationMapper relationMapper;
 @Mock PersonMapper personMapper;
 @Mock DirectorFormTemplateService templates;
 @Mock PermissionApi permissionApi;
 @Mock FileApi fileApi;
 ServiceRelationDO relation; PositioningInterviewDO row;
 @BeforeEach void setup(){
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
   new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), PositioningInterviewAttachmentDO.class);
  TenantContextHolder.setTenantId(1L);
  relation=new ServiceRelationDO();relation.setId(9L);relation.setPersonId(2L);relation.setTenantId(1L);relation.setStatus("active");relation.setAcceptanceStatus("accepted");relation.setContentDirectorUserId(7L);relation.setDirectorStage("positioning_interview");
  row=new PositioningInterviewDO();row.setId(3L);row.setStudentPersonId(2L);row.setServiceRelationId(9L);row.setTemplateId(10L);row.setTemplateVersionId(11L);row.setVersion(0);row.setStatus("draft");row.setTemplateSnapshotJson(JsonUtils.toJsonString(List.of(field())));
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();}
 DirectorFormTemplateVO.Field field(){var f=new DirectorFormTemplateVO.Field();f.setKey("topic");f.setTitle("访谈字段");f.setType("text");f.setEnabled(true);f.setRequired(true);f.setSystemField(false);f.setAllowRemark(true);f.setSort(1);return f;}
 Item item(String state){var i=new Item();i.setFieldKey("topic");i.setStatus(state);return i;}
 SaveReq request(String state){var r=new SaveReq();r.setVersion(0);r.setIdempotencyKey("test-command");r.setTemplateVersionId(11L);r.setCollectedAt("2026-09-10");r.setItems(List.of(item(state)));r.setAttachmentIds(List.of());return r;}
 void access(String permission){when(permissionApi.hasAnyPermissions(7L,permission)).thenReturn(true);when(relationMapper.selectById(9L)).thenReturn(relation);lenient().when(relationMapper.selectByIdForUpdate(9L,1L)).thenReturn(relation);}
 void writable(){access(PositioningInterviewService.COMPLETE);when(mapper.lockStudent(2L,1L)).thenReturn(2L);when(relationMapper.selectByIdForUpdate(9L,1L)).thenReturn(relation);when(mapper.selectOne(any())).thenReturn(row);}
 @Test void draftAllowsNoAnswers(){assertEquals(0,PositioningInterviewService.validateItems(List.of(field()),List.of(),false).size());}
 @Test void completionRequiresAnswer(){assertThrows(ServiceException.class,()->PositioningInterviewService.validateItems(List.of(field()),List.of(),true));}
 @Test void notCommunicatedAccepted(){assertEquals(1,PositioningInterviewService.validateItems(List.of(field()),List.of(item("NOT_COMMUNICATED")),true).size());}
 @Test void refusalAccepted(){assertEquals(1,PositioningInterviewService.validateItems(List.of(field()),List.of(item("CLIENT_REFUSED")),true).size());}
 @Test void invalidStatusRejected(){assertThrows(ServiceException.class,()->PositioningInterviewService.validateItems(List.of(field()),List.of(item("OTHER")),false));}
 @Test void unknownKeyRejected(){var i=item("NOT_COMMUNICATED");i.setFieldKey("invented");assertThrows(ServiceException.class,()->PositioningInterviewService.validateItems(List.of(field()),List.of(i),true));}
 @Test void duplicateKeyRejected(){assertThrows(ServiceException.class,()->PositioningInterviewService.validateItems(List.of(field()),List.of(item("NOT_COMMUNICATED"),item("CLIENT_REFUSED")),true));}
 @Test void featureDenied(){assertThrows(ServiceException.class,()->service.context(9L,7L));verifyNoInteractions(relationMapper);}
 @Test void otherDirectorDenied(){access(PositioningInterviewService.QUERY);relation.setContentDirectorUserId(8L);assertThrows(ServiceException.class,()->service.context(9L,7L));verifyNoInteractions(mapper);}
 @Test void otherTenantDenied(){access(PositioningInterviewService.QUERY);relation.setTenantId(2L);assertThrows(ServiceException.class,()->service.context(9L,7L));verifyNoInteractions(mapper);}
 @Test void versionConflict(){writable();row.setVersion(1);assertThrows(ServiceException.class,()->service.save(9L,request("NOT_COMMUNICATED"),7L,true));verify(mapper,never()).update(any(),any());}
 @Test void completedImmutable(){writable();row.setStatus("completed");assertThrows(ServiceException.class,()->service.save(9L,request("NOT_COMMUNICATED"),7L,true));verify(mapper,never()).update(any(),any());}
 @Test void transcriptRequired(){writable();assertThrows(ServiceException.class,()->service.save(9L,request("NOT_COMMUNICATED"),7L,true));verify(mapper,never()).update(any(),any());}
 @Test void foreignAttachmentRejected(){writable();var r=request("NOT_COMMUNICATED");r.setAttachmentIds(List.of(55L));when(attachmentMapper.selectOne(any())).thenReturn(null);assertThrows(ServiceException.class,()->service.save(9L,r,7L,true));verify(mapper,never()).update(any(),any());}
 @Test void completionPersistsIndependentSnapshotAndStops(){
  writable();var r=request("CLIENT_REFUSED");r.setAttachmentIds(List.of(55L));
  var a=new PositioningInterviewAttachmentDO();a.setId(6L);a.setFileId(55L);a.setStudentPersonId(2L);a.setUploadedBy(7L);a.setDirectory("zsjos/positioning-interview/1/relations/9/2/7");
  when(attachmentMapper.selectOne(any())).thenReturn(a);
  var info=new FileInfoRespDTO();info.setId(55L);info.setCreator("7");info.setPath(a.getDirectory()+"/test.pdf");when(fileApi.getFileInfo(55L)).thenReturn(info);
  when(mapper.update(any(),any())).thenReturn(1);
  when(itemMapper.selectList(any())).thenReturn(List.of());
  when(attachmentMapper.selectList(any())).thenReturn(List.of(a));
  var result=service.save(9L,r,7L,true);
  assertEquals("completed",result.getStatus());assertEquals(1,result.getVersion());
  assertEquals(List.of("VIEW_POSITIONING_INTERVIEW"),result.getAvailableActions());
  assertEquals("positioning_interview_completed",relation.getDirectorStage());
  assertNull(relation.getDirectorInterviewSnapshotJson());verify(relationMapper).updateById(relation);
  verifyNoInteractions(templates);
 }
 @Test void sameKeyDifferentPayloadRejected(){writable();row.setIdempotencyKey("test-command");row.setRequestFingerprint("different");assertThrows(ServiceException.class,()->service.save(9L,request("NOT_COMMUNICATED"),7L,true));}
 @Test void exactCompletionRetryDoesNotWriteAgain(){
  writable();var request=request("NOT_COMMUNICATED");row.setStatus("completed");row.setIdempotencyKey(request.getIdempotencyKey());
  row.setRequestFingerprint(cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(request)+":true"));
  when(itemMapper.selectList(any())).thenReturn(List.of());when(attachmentMapper.selectList(any())).thenReturn(List.of());
  assertEquals("completed",service.save(9L,request,7L,true).getStatus());verify(mapper,never()).update(any(),any());verifyNoInteractions(fileApi);
 }
 @Test void savedDraftKeepsFrozenTemplate(){
  access(PositioningInterviewService.EDIT);when(mapper.lockStudent(2L,1L)).thenReturn(2L);when(mapper.selectOne(any())).thenReturn(row);
  when(mapper.update(any(),any())).thenReturn(1);when(itemMapper.selectList(any())).thenReturn(List.of());when(attachmentMapper.selectList(any())).thenReturn(List.of());
  var r=request(null);r.setCollectedAt(null);
  var result=service.save(9L,r,7L,false);assertEquals("draft",result.getStatus());assertEquals(11L,result.getTemplateVersionId());verifyNoInteractions(templates);verify(relationMapper,never()).updateById(any(ServiceRelationDO.class));
 }
 @Test void invalidCollectionDateRejected(){writable();var r=request("NOT_COMMUNICATED");r.setCollectedAt("not-a-date");assertThrows(ServiceException.class,()->service.save(9L,r,7L,true));}
 @Test void fileWithoutStudentBindingCannotBeDownloaded(){access(PositioningInterviewService.QUERY);when(attachmentMapper.selectOne(any())).thenReturn(null);assertThrows(ServiceException.class,()->service.download(9L,7L,50L));verifyNoInteractions(fileApi);}
 @Test void otherRelationBoundFileNeverGetsSigned(){
  access(PositioningInterviewService.QUERY);var a=attachment();a.setInterviewId(3L);
  when(attachmentMapper.selectOne(any())).thenReturn(a);
  row.setServiceRelationId(10L);row.setTenantId(1L);when(mapper.selectById(3L)).thenReturn(row);
  assertThrows(ServiceException.class,()->service.download(9L,7L,55L));verifyNoInteractions(fileApi);
 }
 @Test void legacyBoundFileUsesInterviewRelationEvenAfterDirectorChanges(){
  access(PositioningInterviewService.QUERY);var a=attachment();a.setInterviewId(3L);a.setUploadedBy(8L);
  row.setTenantId(1L);when(mapper.selectById(3L)).thenReturn(row);when(attachmentMapper.selectOne(any())).thenReturn(a);
  when(fileApi.presignGetUrl(55L,300)).thenReturn("signed-local-test");
  assertEquals("signed-local-test",service.download(9L,7L,55L).getUrl());
 }
 @Test void unknownLegacyTemporaryFileNeverGetsSigned(){
  access(PositioningInterviewService.QUERY);when(attachmentMapper.selectOne(any())).thenReturn(attachment());
  assertThrows(ServiceException.class,()->service.download(9L,7L,55L));verifyNoInteractions(fileApi);
 }
 @Test void foreignTemporaryDirectoryCannotBeSavedOrRemoved(){
  access(PositioningInterviewService.EDIT);when(mapper.lockStudent(2L,1L)).thenReturn(2L);when(mapper.selectOne(any())).thenReturn(row);
  var a=attachment();a.setDirectory("zsjos/positioning-interview/1/relations/10/2/7");when(attachmentMapper.selectOne(any())).thenReturn(a);
  var req=request(null);req.setAttachmentIds(List.of(55L));
  assertThrows(ServiceException.class,()->service.save(9L,req,7L,false));
  assertThrows(ServiceException.class,()->service.remove(9L,7L,55L,0,"remove"));
  verifyNoInteractions(fileApi);verify(attachmentMapper,never()).deleteById(any());
 }
 private PositioningInterviewAttachmentDO attachment(){
  var a=new PositioningInterviewAttachmentDO();a.setId(6L);a.setFileId(55L);a.setStudentPersonId(2L);a.setUploadedBy(7L);a.setDirectory("zsjos/positioning-interview/1/2/7");return a;
 }
 @Test void completedFileRemovalRejected(){access(PositioningInterviewService.EDIT);when(mapper.lockStudent(2L,1L)).thenReturn(2L);row.setStatus("completed");when(mapper.selectOne(any())).thenReturn(row);assertThrows(ServiceException.class,()->service.remove(9L,7L,50L,0,"remove-key"));verifyNoInteractions(attachmentMapper);}
 @Test void oversizedTranscriptRejected(){access(PositioningInterviewService.EDIT);when(mapper.lockStudent(2L,1L)).thenReturn(2L);when(mapper.selectOne(any())).thenReturn(row);var file=mock(org.springframework.web.multipart.MultipartFile.class);when(file.getOriginalFilename()).thenReturn("transcript.pdf");when(file.getSize()).thenReturn(21L*1024*1024);assertThrows(ServiceException.class,()->service.upload(9L,7L,file));verifyNoInteractions(fileApi);}
}
