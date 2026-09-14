package cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview.vo.PositioningInterviewVO.*;
import cn.iocoder.yudao.module.zsjos.service.positioninginterview.PositioningInterviewService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @Validated
@RequestMapping("/zsjos/student/service/{relationId}/positioning-interview")
public class PositioningInterviewController {
 @Resource private PositioningInterviewService service;
 @GetMapping({"","/context"}) @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview-query')")
 public CommonResult<Context> context(@PathVariable Long relationId){return success(service.context(relationId,getLoginUserId()));}
 @PostMapping("/draft") @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview')")
 public CommonResult<Context> draft(@PathVariable Long relationId,@Valid @RequestBody SaveReq request){return success(service.save(relationId,request,getLoginUserId(),false));}
 @PostMapping("/complete") @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview-complete')")
 public CommonResult<Context> complete(@PathVariable Long relationId,@Valid @RequestBody SaveReq request){return success(service.save(relationId,request,getLoginUserId(),true));}
 @PostMapping("/attachments") @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview')")
 public CommonResult<Attachment> upload(@PathVariable Long relationId,@RequestParam("file") MultipartFile file)throws IOException{return success(service.upload(relationId,getLoginUserId(),file));}
 @GetMapping("/attachments/{fileId}") @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview-query')")
 public CommonResult<Attachment> download(@PathVariable Long relationId,@PathVariable Long fileId){return success(service.download(relationId,getLoginUserId(),fileId));}
 @DeleteMapping("/attachments/{fileId}") @PreAuthorize("@ss.hasPermission('zsjos:student:positioning-interview')")
 public CommonResult<Boolean> remove(@PathVariable Long relationId,@PathVariable Long fileId,@RequestParam Integer version,@RequestParam @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String idempotencyKey){service.remove(relationId,getLoginUserId(),fileId,version,idempotencyKey);return success(true);}
}
