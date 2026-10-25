package cn.iocoder.yudao.module.zsjos.controller.pub.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Runtime;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Submit;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFormService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student-info-form")
@PermitAll
@TenantIgnore
public class PublicStudentInfoFormController {
    @Resource private StudentInfoFormService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService images;
    @Resource private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoUploadRateLimit uploadLimit;
    @PostMapping("/images")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Image> upload(
            @RequestHeader("X-Student-Info-Token") String token,@RequestParam String fieldKey,
            @RequestParam(defaultValue="") String side,@RequestParam org.springframework.web.multipart.MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); uploadLimit.check(token,request.getRemoteAddr());
        return success(images.upload(token,fieldKey,side,file));
    }
    @DeleteMapping("/images/{id}")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Boolean> delete(@RequestHeader("X-Student-Info-Token") String token,@PathVariable Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); images.delete(token,id); return success(true);
    }
    @GetMapping("/images/{id}")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public byte[] image(@RequestHeader("X-Student-Info-Token") String token,@PathVariable Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Content-Type-Options","nosniff");
        response.setContentType("application/octet-stream"); return images.publicRead(token,id);
    }
    @GetMapping("/detail")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Runtime> detail(@RequestHeader("X-Student-Info-Token") String token,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return success(service.publicDetail(token));
    }
    @PostMapping("/submit")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Boolean> submit(@RequestHeader("X-Student-Info-Token") String token,
                                        @Valid @RequestBody Submit request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        service.submit(token,request); return success(true);
    }
    @PutMapping("/draft")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Draft> draft(
            @RequestHeader("X-Student-Info-Token") String token,
            @Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.DraftWrite request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return success(service.saveDraft(token,request));
    }
}
