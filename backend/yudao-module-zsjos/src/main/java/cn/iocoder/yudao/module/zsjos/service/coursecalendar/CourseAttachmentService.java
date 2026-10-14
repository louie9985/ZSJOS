package cn.iocoder.yudao.module.zsjos.service.coursecalendar;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CourseAttachmentErrorCodes.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CALENDAR_NOTIFY_MANAGE_PERMISSION_DENIED;

@Service
public class CourseAttachmentService {
 public static final long MAX_SIZE=100L*1024*1024;
 private static final Set<String> TYPES=Set.of("image/png","image/jpeg","image/gif","image/webp","application/pdf",
  "application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation");
 @Resource private FileApi fileApi;
 @Resource private PermissionApi permissionApi;
 @Resource private CourseCalendarEventMapper mapper;
 @Resource private CourseCalendarObjectPermissionProvider access;

 public CourseAttachmentRespVO upload(MultipartFile file,Long userId) throws IOException {
  requireManage(userId);
  if(file.isEmpty() || file.getSize()>MAX_SIZE) throw exception(INVALID_FILE);
  String name=file.getOriginalFilename();
  if(name==null || name.isBlank() || name.length()>255 || name.matches(".*[\\\\/\\p{Cntrl}].*")
    || !name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp|pdf|docx?|xlsx?|pptx?)")) throw exception(INVALID_FILE);
  byte[] bytes=file.getBytes();
  String type=FileTypeUtils.getMineType(bytes,name);
  if(!TYPES.contains(type)) throw exception(INVALID_FILE);
  return response(fileApi.createFileInfo(bytes,name,directory()+userId,type),true);
 }

 @ZsjosPermission(bizType="course-calendar",bizId="#courseId",action="read-attachment")
 public CourseAttachmentRespVO read(Long courseId,Long fileId,Long userId) {
  access.check(courseId,"read-attachment",userId);
  var row=mapper.selectById(courseId);
  if(row==null || !ids(row.getAttachmentIdsJson()).contains(fileId)) throw exception(INVALID_REFERENCE);
  var file=fileApi.getFileInfo(fileId);
  if(file==null || file.getPath()==null || !file.getPath().startsWith(directory())) throw exception(INVALID_REFERENCE);
  return response(file,true);
 }

 public String validate(List<Long> requested,String existingJson,Long userId) {
  // Omission by clients without an attachment editor preserves the stored references.
  if(requested==null) return existingJson;
  requireManage(userId);
  if(requested.size()>20 || new HashSet<>(requested).size()!=requested.size()) throw exception(TOO_MANY_FILES);
  var existing=new HashSet<>(ids(existingJson));
  for(Long id:requested) {
   if(id==null || id<=0) throw exception(INVALID_REFERENCE);
   // Existing links are historical facts. Retain even unavailable files until explicitly removed.
   if(existing.contains(id)) continue;
   var file=fileApi.getFileInfo(id);
   if(file==null || file.getPath()==null || !file.getPath().startsWith(directory()+userId+"/")
     || !Objects.equals(file.getCreator(),String.valueOf(userId))
     || !TYPES.contains(file.getType()) || file.getSize()==null || file.getSize()<=0 || file.getSize()>MAX_SIZE)
    throw exception(INVALID_REFERENCE);
  }
  return JsonUtils.toJsonString(requested);
 }
 public Map<Long, FileInfoRespDTO> metadata(List<String> references) {
  var distinct = references.stream().flatMap(json -> ids(json).stream()).distinct().toList();
  return distinct.isEmpty() ? Map.of() : fileApi.getFileInfoMap(distinct);
 }
 public List<CourseAttachmentRespVO> describe(String json, Map<Long, FileInfoRespDTO> metadata) {
  return ids(json).stream().map(id -> {
   var file=metadata.get(id);
   return file==null || file.getPath()==null || !file.getPath().startsWith(directory())
    ? new CourseAttachmentRespVO().setFileId(id).setName("附件已不可用").setType("") : response(file,false);
  }).toList();
 }
 private CourseAttachmentRespVO response(FileInfoRespDTO file,boolean signed) {
  var result=new CourseAttachmentRespVO().setFileId(file.getId()).setName(file.getName()).setType(file.getType()).setSize(file.getSize());
  return signed ? result.setUrl(fileApi.presignGetUrl(file.getId(),3600)) : result;
 }
 private static List<Long> ids(String json) { return json==null || json.isBlank()?List.of():JsonUtils.parseArray(json,Long.class); }
 private String directory(){return "zsjos/course-attachment/"+TenantContextHolder.getRequiredTenantId()+"/";}
 private void requireManage(Long userId){
  if(userId==null || !permissionApi.hasAnyPermissions(userId,"zsjos:course-calendar:manage")) throw exception(CALENDAR_NOTIFY_MANAGE_PERMISSION_DENIED);
 }
}
