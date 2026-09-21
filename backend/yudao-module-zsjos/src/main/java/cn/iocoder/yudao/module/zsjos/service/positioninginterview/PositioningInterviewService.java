package cn.iocoder.yudao.module.zsjos.service.positioninginterview;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview.vo.PositioningInterviewVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningAttachmentTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PositioningInterviewService {
 public static final String QUERY="zsjos:student:positioning-interview-query";
 public static final String EDIT="zsjos:student:positioning-interview";
 public static final String COMPLETE="zsjos:student:positioning-interview-complete";
 public static final Map<String,String> STATUSES;
 static { Map<String,String> m=new LinkedHashMap<>(); m.put("COMMUNICATED_DOCUMENT","已沟通，见文稿");m.put("NOT_COMMUNICATED","未沟通");m.put("CLIENT_REFUSED","客户拒绝回答");STATUSES=Collections.unmodifiableMap(m); }
     @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
@Resource private PositioningInterviewMapper mapper;
 @Resource private PositioningInterviewItemMapper itemMapper;
 @Resource private PositioningInterviewAttachmentMapper attachmentMapper;
 @Resource private ServiceRelationMapper relationMapper;
 @Resource private PersonMapper personMapper;
 @Resource private DirectorFormTemplateService templates;
 @Resource private PermissionApi permissionApi;
 @Resource private FileApi fileApi;

 @ZsjosPermission(bizType="student-service",bizId="#relationId",action="positioning-interview-read")
 public Context context(Long relationId,Long userId) {
  requireFeature(userId,QUERY);
  ServiceRelationDO relation=authorizedRead(relationId,userId);
  return project(relation,current(relation.getId(),relation.getPersonId()),userId);
 }
 private ServiceRelationDO authorized(Long relationId,Long userId) {
  ServiceRelationDO relation=relationMapper.selectById(relationId);
  return validateAuthorization(relation,userId);
 }
 private ServiceRelationDO authorizedRead(Long relationId,Long userId) {
  ServiceRelationDO relation=relationMapper.selectById(relationId);
  if(relation==null || !Objects.equals(relation.getTenantId(),TenantContextHolder.getRequiredTenantId())) throw exception(STUDENT_PERMISSION_DENIED);
  if(cn.iocoder.yudao.module.zsjos.service.registration.MediaStudentReadScope.canReadAll(permissionApi,userId)
    && relationMapper.selectMediaReadByPersonIds(List.of(relation.getPersonId()),null).stream().anyMatch(row->Objects.equals(row.getId(),relationId))) return relation;
  return validateAuthorization(relation,userId);
 }
 private ServiceRelationDO validateAuthorization(ServiceRelationDO relation,Long userId) {
  if(relation==null || !Objects.equals(relation.getTenantId(),TenantContextHolder.getRequiredTenantId())
    || !"active".equals(relation.getStatus()) || !"accepted".equals(relation.getAcceptanceStatus())
    || !Objects.equals(relation.getContentDirectorUserId(),userId)) throw exception(STUDENT_PERMISSION_DENIED);
  return relation;
 }
 private void requireFeature(Long userId,String permission) { if(!permissionApi.hasAnyPermissions(userId,permission)) throw exception(STUDENT_PERMISSION_DENIED); }
 private PositioningInterviewDO current(Long relationId,Long personId) {
  return mapper.selectOne(new LambdaQueryWrapper<PositioningInterviewDO>().eq(PositioningInterviewDO::getServiceRelationId,relationId)
   .eq(PositioningInterviewDO::getStudentPersonId,personId)
   .in(PositioningInterviewDO::getStatus,List.of("draft","completed")).orderByDesc(PositioningInterviewDO::getId).last("LIMIT 1"));
 }
 private boolean ready(ServiceRelationDO relation) {
  return relation.getDirectorPrecheckSnapshotJson()!=null && !relation.getDirectorPrecheckSnapshotJson().isBlank()
   || Set.of("interview","positioning_ready","positioning_interview","positioning_interview_completed").contains(Objects.toString(relation.getDirectorStage(),""));
 }
 private List<DirectorFormTemplateVO.Field> fields(PositioningInterviewDO row) {
  return JsonUtils.parseArray(row.getTemplateSnapshotJson(),DirectorFormTemplateVO.Field.class);
 }
 private Context project(ServiceRelationDO relation,PositioningInterviewDO row,Long userId) {
  Context c=new Context();c.setStudentPersonId(relation.getPersonId());c.setServiceRelationId(relation.getId());
  var person=personMapper.selectById(relation.getPersonId());
  if(person!=null){c.setStudentName(person.getName());c.setStudentNo(person.getPersonNo());}
  c.setInterviewAt(relation.getDirectorInterviewAt());c.setStatusOptions(STATUSES);
  c.setLegacyInterviewSnapshotJson(relation.getDirectorInterviewSnapshotJson());
  c.setItems(List.of());c.setAttachments(List.of());c.setVersion(0);
  c.setCollectedAt(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());
  if(row==null){
   var version=templates.requirePublished(DirectorFormTemplateService.SCENE_POSITIONING_INTERVIEW,null);
   c.setTemplateId(version.getTemplateId());c.setTemplateVersionId(version.getId());c.setFields(templates.fields(version));
   c.setStatus("empty");
  } else {
   c.setId(row.getId());c.setVersion(row.getVersion());c.setStatus(row.getStatus());
   if(row.getStatusOptionsSnapshotJson()!=null)c.setStatusOptions(JsonUtils.parseObject(row.getStatusOptionsSnapshotJson(),Map.class));
   c.setTemplateId(row.getTemplateId());c.setTemplateVersionId(row.getTemplateVersionId());c.setFields(fields(row));
   c.setCollectedAt(row.getCollectedAt()==null?null:row.getCollectedAt().toString());c.setCompletedAt(row.getCompletedAt());
   c.setItems(itemMapper.selectList(new LambdaQueryWrapper<PositioningInterviewItemDO>().eq(PositioningInterviewItemDO::getInterviewId,row.getId())
    .orderByAsc(PositioningInterviewItemDO::getSort)).stream().map(i->{Item x=new Item();x.setFieldKey(i.getFieldKey());x.setStatus(i.getConfirmationStatus());x.setRemark(i.getRemark());x.setValue(i.getFieldValue());return x;}).toList());
   c.setAttachments(attachmentMapper.selectList(new LambdaQueryWrapper<PositioningInterviewAttachmentDO>().eq(PositioningInterviewAttachmentDO::getInterviewId,row.getId())).stream().map(this::attachment).toList());
  }
  List<String> actions=new ArrayList<>();
  if(row!=null) actions.add("VIEW_POSITIONING_INTERVIEW");
  if((row==null||!"completed".equals(row.getStatus())) && ready(relation)
    && "active".equals(relation.getStatus()) && "accepted".equals(relation.getAcceptanceStatus())
    && Objects.equals(relation.getContentDirectorUserId(),userId)) {
   if(permissionApi.hasAnyPermissions(userId,EDIT))actions.add(row==null?"START_POSITIONING_INTERVIEW":"CONTINUE_POSITIONING_INTERVIEW");
   if(permissionApi.hasAnyPermissions(userId,COMPLETE))actions.add("COMPLETE_POSITIONING_INTERVIEW");
  }
  c.setAvailableActions(actions);return c;
 }
 @Transactional(rollbackFor=Exception.class)
 @ZsjosPermission(bizType="student-service",bizId="#relationId",action="director-interview")
 public Context save(Long relationId,SaveReq request,Long userId,boolean complete) {
  requireFeature(userId,complete?COMPLETE:EDIT);
  ServiceRelationDO relation=authorized(relationId,userId);
  if(request.getStudentPersonId()!=null&&!Objects.equals(request.getStudentPersonId(),relation.getPersonId())
   ||request.getServiceRelationId()!=null&&!Objects.equals(request.getServiceRelationId(),relationId))throw exception(POSITIONING_INTERVIEW_INVALID);
  if(mapper.lockStudent(relation.getPersonId(),TenantContextHolder.getRequiredTenantId())==null)throw exception(STUDENT_PERMISSION_DENIED);
  // Locking read revalidates reassignment/status after waiting for the student lock.
  relation=validateAuthorization(relationMapper.selectByIdForUpdate(relationId,TenantContextHolder.getRequiredTenantId()),userId);
  PositioningInterviewDO row=current(relation.getId(),relation.getPersonId());
  String fingerprint=DigestUtil.sha256Hex(JsonUtils.toJsonString(request)+":"+complete);
  if(row!=null&&Objects.equals(row.getIdempotencyKey(),request.getIdempotencyKey())){
   if(!Objects.equals(row.getRequestFingerprint(),fingerprint))throw exception(POSITIONING_INTERVIEW_REPLAY);
   return project(relation,row,userId);
  }
  if(!ready(relation)||(row!=null&&!"draft".equals(row.getStatus())))throw exception(POSITIONING_INTERVIEW_STATE);
  if(!Objects.equals(row==null?0:row.getVersion(),request.getVersion()))throw exception(POSITIONING_INTERVIEW_VERSION);
  if(row==null){
   var version=templates.requirePublished(DirectorFormTemplateService.SCENE_POSITIONING_INTERVIEW,null);
   if(!Objects.equals(version.getId(),request.getTemplateVersionId()))throw exception(POSITIONING_INTERVIEW_VERSION);
   row=new PositioningInterviewDO();row.setStudentPersonId(relation.getPersonId());row.setServiceRelationId(relationId);row.setDirectorUserId(userId);
   row.setTemplateId(version.getTemplateId());row.setTemplateVersionId(version.getId());row.setTemplateSnapshotJson(JsonUtils.toJsonString(templates.fields(version)));row.setStatus("draft");row.setVersion(0);
   row.setStatusOptionsSnapshotJson(JsonUtils.toJsonString(STATUSES));
  } else if(!Objects.equals(row.getTemplateVersionId(),request.getTemplateVersionId()))throw exception(POSITIONING_INTERVIEW_VERSION);
  List<DirectorFormTemplateVO.Field> fields=fields(row);
  Map<String,Item> values=validateItems(fields,request.getItems(),complete);
  LocalDate collected=null;
  try { if(request.getCollectedAt()!=null&&!request.getCollectedAt().isBlank())collected=LocalDate.parse(request.getCollectedAt()); }
  catch(RuntimeException e){throw exception(POSITIONING_INTERVIEW_INVALID);}
  if(complete&&collected==null)throw exception(POSITIONING_INTERVIEW_INVALID);
  List<PositioningInterviewAttachmentDO> files=validateFiles(request.getAttachmentIds(),relationId,relation.getPersonId(),userId,row.getId());
  if(complete&&files.isEmpty())throw exception(POSITIONING_INTERVIEW_TRANSCRIPT_REQUIRED);
  row.setCollectedAt(collected);row.setIdempotencyKey(request.getIdempotencyKey());row.setRequestFingerprint(fingerprint);
  row.setVersion(request.getVersion()+1);
  if(complete){row.setStatus("completed");row.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));row.setCompletedBy(userId);}
  if(row.getId()==null)mapper.insert(row);
  else if(mapper.update(row,new LambdaUpdateWrapper<PositioningInterviewDO>().eq(PositioningInterviewDO::getId,row.getId()).eq(PositioningInterviewDO::getVersion,request.getVersion()).eq(PositioningInterviewDO::getStatus,"draft"))!=1)throw exception(POSITIONING_INTERVIEW_VERSION);
  // Replace only draft-owned item rows; completed rows are never reopened or overwritten.
  itemMapper.delete(new LambdaQueryWrapper<PositioningInterviewItemDO>().eq(PositioningInterviewItemDO::getInterviewId,row.getId()));
  for(var field:fields){
   Item value=values.get(field.getKey());PositioningInterviewItemDO i=new PositioningInterviewItemDO();
   i.setInterviewId(row.getId());i.setFieldKey(field.getKey());i.setTitleSnapshot(field.getTitle());i.setInterviewNoteSnapshot(field.getInterviewNote());
   i.setSort(field.getSort());i.setSystemField(field.getSystemField());
   if(value!=null){i.setConfirmationStatus(value.getStatus());i.setStatusLabelSnapshot(STATUSES.get(value.getStatus()));i.setRemark(value.getRemark());}
   if("collectedAt".equals(field.getKey()))i.setFieldValue(request.getCollectedAt());
   if("studentIdentity".equals(field.getKey())){var person=personMapper.selectById(relation.getPersonId());if(person!=null)i.setFieldValue(person.getName()+" / "+person.getPersonNo());}
   itemMapper.insert(i);
  }
  Long rowId=row.getId();
  attachmentMapper.update(null,new LambdaUpdateWrapper<PositioningInterviewAttachmentDO>().eq(PositioningInterviewAttachmentDO::getInterviewId,rowId).set(PositioningInterviewAttachmentDO::getInterviewId,null));
  for(var file:files){file.setInterviewId(rowId);attachmentMapper.updateById(file);}
  if(complete){
   relation.setDirectorStage("positioning_interview_completed");
   relation.setVersion((relation.getVersion()==null?0:relation.getVersion())+1);
   relationMapper.updateById(relation);
   collaborationNotify.student(MEDIA_STUDENT_INTERVIEW_COMPLETED, relation, userId, "interview-completed:"+row.getId());
  }
  return project(relation,row,userId);
 }
 static Map<String,Item> validateItems(List<DirectorFormTemplateVO.Field> fields,List<Item> items,boolean complete){
  if(items==null)throw exception(POSITIONING_INTERVIEW_INVALID);
  Map<String,DirectorFormTemplateVO.Field> definitions=new HashMap<>();fields.forEach(f->definitions.put(f.getKey(),f));
  Map<String,Item> values=new HashMap<>();
  for(Item item:items){
   if(item==null||!definitions.containsKey(item.getFieldKey())||values.put(item.getFieldKey(),item)!=null
    ||(item.getStatus()!=null&&!STATUSES.containsKey(item.getStatus())))throw exception(POSITIONING_INTERVIEW_INVALID);
   var definition=definitions.get(item.getFieldKey());
   if(!Boolean.TRUE.equals(definition.getAllowRemark())&&item.getRemark()!=null&&!item.getRemark().isBlank())throw exception(POSITIONING_INTERVIEW_INVALID);
  }
  if(complete)for(var field:fields){
   if(!Boolean.TRUE.equals(field.getEnabled())||!Boolean.TRUE.equals(field.getRequired())||Boolean.TRUE.equals(field.getSystemField()))continue;
   Item item=values.get(field.getKey());if(item==null||item.getStatus()==null)throw exception(POSITIONING_INTERVIEW_INVALID);
  }
  return values;
 }
 private String directory(Long relation,Long student,Long user){return "zsjos/positioning-interview/"+TenantContextHolder.getRequiredTenantId()+"/relations/"+relation+"/"+student+"/"+user;}
 private boolean belongsToRelation(PositioningInterviewAttachmentDO attachment,Long relation,Long student,Long user){
  if(!Objects.equals(attachment.getStudentPersonId(),student))return false;
  if(attachment.getInterviewId()==null){
   // Old unbound uploads cannot prove which relation owns them; require a new relation-scoped upload.
   return Objects.equals(attachment.getUploadedBy(),user)&&Objects.equals(attachment.getDirectory(),directory(relation,student,user));
  }
  var interview=mapper.selectById(attachment.getInterviewId());
  return interview!=null&&Objects.equals(interview.getServiceRelationId(),relation)
   &&Objects.equals(interview.getStudentPersonId(),student)
   &&Objects.equals(interview.getTenantId(),TenantContextHolder.getRequiredTenantId());
 }
 private List<PositioningInterviewAttachmentDO> validateFiles(List<Long> ids,Long relation,Long student,Long user,Long interview){
  if(ids==null||ids.size()>20||new HashSet<>(ids).size()!=ids.size())throw exception(POSITIONING_INTERVIEW_ATTACHMENT);
  List<PositioningInterviewAttachmentDO> result=new ArrayList<>();
  for(Long id:ids){
   var row=attachmentMapper.selectOne(new LambdaQueryWrapper<PositioningInterviewAttachmentDO>().eq(PositioningInterviewAttachmentDO::getFileId,id).eq(PositioningInterviewAttachmentDO::getStudentPersonId,student));
   if(row==null || !Objects.equals(row.getUploadedBy(),user) || row.getInterviewId()!=null&&!Objects.equals(row.getInterviewId(),interview)
    || !belongsToRelation(row,relation,student,user))throw exception(POSITIONING_INTERVIEW_ATTACHMENT);
   FileInfoRespDTO info=fileApi.getFileInfo(id);
   if(info==null||!Objects.equals(info.getCreator(),String.valueOf(user))||info.getPath()==null||!info.getPath().startsWith(row.getDirectory()+"/"))throw exception(POSITIONING_INTERVIEW_ATTACHMENT);
   result.add(row);
  }return result;
 }
 @Transactional(rollbackFor=Exception.class)
 @ZsjosPermission(bizType="student-service",bizId="#relationId",action="director-interview")
 public Attachment upload(Long relationId,Long userId,MultipartFile file)throws IOException {
  requireFeature(userId,EDIT);var relation=authorized(relationId,userId);
  mapper.lockStudent(relation.getPersonId(),TenantContextHolder.getRequiredTenantId());
  relation=validateAuthorization(relationMapper.selectByIdForUpdate(relationId,TenantContextHolder.getRequiredTenantId()),userId);
  var existing=current(relation.getId(),relation.getPersonId());if(!ready(relation)||existing!=null&&"completed".equals(existing.getStatus()))throw exception(POSITIONING_INTERVIEW_STATE);
  String name=file==null?null:file.getOriginalFilename();
  byte[] content=file==null||file.isEmpty()?null:file.getBytes();
  // 允许文档、图片、音频、视频；以探测结果为准，避免客户端伪造 Content-Type。
  String detected=PositioningAttachmentTypes.detectAllowed(name,content);
  if(file==null||file.isEmpty()||file.getSize()>20L*1024*1024||detected==null)throw exception(POSITIONING_INTERVIEW_ATTACHMENT);
  String directory=directory(relationId,relation.getPersonId(),userId);
  var info=fileApi.createFileInfo(content,name,directory,detected);
  var row=new PositioningInterviewAttachmentDO();row.setStudentPersonId(relation.getPersonId());row.setFileId(info.getId());row.setFileName(name);row.setFileSize(file.getSize());
  row.setMimeType(detected);row.setUploadedBy(userId);row.setDirectory(directory);attachmentMapper.insert(row);return attachment(row);
 }
 private Attachment attachment(PositioningInterviewAttachmentDO row){Attachment a=new Attachment();a.setFileId(row.getFileId());a.setFileName(row.getFileName());a.setMimeType(row.getMimeType());a.setFileSize(row.getFileSize());return a;}
 @ZsjosPermission(bizType="student-service",bizId="#relationId",action="positioning-interview-read")
 public Attachment download(Long relationId,Long userId,Long fileId){
  requireFeature(userId,QUERY);var relation=authorizedRead(relationId,userId);
  var row=attachmentMapper.selectOne(new LambdaQueryWrapper<PositioningInterviewAttachmentDO>().eq(PositioningInterviewAttachmentDO::getStudentPersonId,relation.getPersonId()).eq(PositioningInterviewAttachmentDO::getFileId,fileId));
  if(row==null||!belongsToRelation(row,relationId,relation.getPersonId(),userId))throw exception(POSITIONING_INTERVIEW_ATTACHMENT);
  var result=attachment(row);result.setUrl(fileApi.presignGetUrl(fileId,300));return result;
 }
 @Transactional(rollbackFor=Exception.class)
 @ZsjosPermission(bizType="student-service",bizId="#relationId",action="director-interview")
 public void remove(Long relationId,Long userId,Long fileId,Integer version,String idempotencyKey){
  requireFeature(userId,EDIT);var relation=authorized(relationId,userId);mapper.lockStudent(relation.getPersonId(),TenantContextHolder.getRequiredTenantId());
  relation=validateAuthorization(relationMapper.selectByIdForUpdate(relationId,TenantContextHolder.getRequiredTenantId()),userId);
  var current=current(relation.getId(),relation.getPersonId());
  String fingerprint=DigestUtil.sha256Hex("remove:"+fileId+":"+version);
  if(current!=null&&Objects.equals(current.getIdempotencyKey(),idempotencyKey)){
   if(!Objects.equals(current.getRequestFingerprint(),fingerprint))throw exception(POSITIONING_INTERVIEW_REPLAY);
   return;
  }
  if(current!=null&&"completed".equals(current.getStatus()))throw exception(POSITIONING_INTERVIEW_STATE);
  if(!Objects.equals(current==null?0:current.getVersion(),version))throw exception(POSITIONING_INTERVIEW_VERSION);
  var files=validateFiles(List.of(fileId),relationId,relation.getPersonId(),userId,current==null?null:current.getId());
  attachmentMapper.deleteById(files.getFirst().getId());
  if(current!=null){current.setVersion(current.getVersion()+1);current.setIdempotencyKey(idempotencyKey);current.setRequestFingerprint(fingerprint);mapper.updateById(current);}
 }
}
