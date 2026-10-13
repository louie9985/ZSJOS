package cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseAttachmentService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/zsjos/course-calendar/attachment")
public class CourseAttachmentController {
 @Resource private CourseAttachmentService service;
 @PostMapping("/upload")
 @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:manage')")
 @ApiAccessLog(requestEnable=false,responseEnable=false)
 public CommonResult<CourseAttachmentRespVO> upload(@RequestParam("file") MultipartFile file) throws IOException {return success(service.upload(file,getLoginUserId()));}
 @GetMapping("/{courseId}/{fileId}")
 @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:query')")
 @ApiAccessLog(responseEnable=false)
 public CommonResult<CourseAttachmentRespVO> read(@PathVariable Long courseId,@PathVariable Long fileId){return success(service.read(courseId,fileId,getLoginUserId()));}
}
