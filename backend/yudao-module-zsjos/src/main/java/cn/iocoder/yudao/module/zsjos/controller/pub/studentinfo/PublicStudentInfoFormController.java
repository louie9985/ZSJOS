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
}
