package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentArchiveExtraService;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student/service/{relationId}/archive/extra")
public class StudentArchiveExtraController {
    @Resource private StudentArchiveExtraService service;
    @GetMapping
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read')")
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Detail> detail(@PathVariable Long relationId) { return success(service.detail(relationId)); }
    @PostMapping
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:update')")
    public CommonResult<Integer> save(@PathVariable Long relationId,@Valid @RequestBody Save request) { return success(service.save(relationId,request)); }
    @PostMapping("/visibility")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<Integer> saveVisibility(@Valid @RequestBody VisibilitySave request) {
        return success(service.saveVisibility(request));
    }
    @PostMapping("/attachments")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:update') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public CommonResult<StudentInfoVO.Image> upload(@PathVariable Long relationId,@RequestParam String fieldKey,@RequestParam MultipartFile file) {
        return success(service.upload(relationId,fieldKey,file));
    }
    @GetMapping("/attachments/{id}")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public byte[] download(@PathVariable Long relationId,@PathVariable Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Content-Disposition","attachment; filename=material");
        response.setContentType("application/octet-stream"); return service.download(relationId,id);
    }
}
