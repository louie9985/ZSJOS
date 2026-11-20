package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoTaskService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student-info-task")
@Validated
public class StudentInfoTaskController {
    @Resource private StudentInfoTaskService service;
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/by-registration/{caseId}") @PreAuthorize("@ss.hasPermission('zsjos:student-info-task:query')")
    public CommonResult<Row> byRegistration(@PathVariable Long caseId) { return success(service.byRegistration(caseId)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/archive") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read')")
    public CommonResult<StudentInfoVO.Detail> archive(@PathVariable Long id,@RequestParam Long formId,@RequestParam Integer revision,@RequestParam(defaultValue="false") boolean sensitive) { return success(service.archive(id,formId,revision,sensitive)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/archive/images/{imageId}") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public byte[] archiveImage(@PathVariable Long id,@PathVariable Long imageId,@RequestParam Long formId,@RequestParam Integer revision,HttpServletResponse response) {
        response.setContentType("application/octet-stream");response.setHeader("X-Content-Type-Options","nosniff");return service.archiveImage(id,formId,revision,imageId);
    }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/{id}/revoke") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:revoke')")
    public CommonResult<Row> revoke(@PathVariable Long id,@Valid @RequestBody Command command) { return success(service.revoke(id,command)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/information") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read')")
    public CommonResult<StudentInfoVO.Detail> information(@PathVariable Long id,@RequestParam(defaultValue="false") boolean sensitive) { return success(service.information(id,sensitive)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/images/{imageId}") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public byte[] image(@PathVariable Long id,@PathVariable Long imageId,HttpServletResponse response) {
        response.setContentType("application/octet-stream"); response.setHeader("X-Content-Type-Options","nosniff"); return service.image(id,imageId);
    }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/{id}/regenerate") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:regenerate')")
    public CommonResult<Row> regenerate(@PathVariable Long id,@Valid @RequestBody Command command) { return success(service.regenerate(id,command)); }
    @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:student-info-task:query')")
    public CommonResult<PageResult<Row>> page(@Valid Query query) { return success(service.page(query)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:student-info-task:query')")
    public CommonResult<Row> detail(@PathVariable Long id) { return success(service.detail(id)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/generation-config") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:create') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public CommonResult<GenerationConfig> generationConfig(@PathVariable Long id) { return success(service.generationConfig(id)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/{id}/generate") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:create') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public CommonResult<Row> generate(@PathVariable Long id,@Valid @RequestBody Command command) { return success(service.generate(id,command)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/{id}/reuse") @PreAuthorize("@ss.hasPermission('zsjos:student-info-task:reuse') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    public CommonResult<Row> reuse(@PathVariable Long id,@Valid @RequestBody Command command) { return success(service.reuse(id,command)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/{id}/sent") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:sent')")
    public CommonResult<Row> sent(@PathVariable Long id,@Valid @RequestBody Command command) { return success(service.sent(id,command)); }
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/{id}/link") @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:link-read')")
    public CommonResult<StudentInfoVO.Link> link(@PathVariable Long id) { return success(service.link(id)); }
}
