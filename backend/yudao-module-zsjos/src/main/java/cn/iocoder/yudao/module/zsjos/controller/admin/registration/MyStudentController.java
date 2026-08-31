package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.service.registration.MyStudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "员工工作台 - 我的学员")
@RestController
@RequestMapping("/zsjos/student")
public class MyStudentController {
    @Resource private MyStudentService studentService;

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<PageResult<MyStudentRespVO>> getMyPage(@Valid MyStudentPageReqVO reqVO) {
        return success(studentService.getMyPage(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/my/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<PageResult<MyStudentRespVO>> searchMyPage(@Valid @RequestBody MyStudentPageReqVO reqVO) {
        return success(studentService.getMyPage(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @GetMapping("/my/{personId}")
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<MyStudentRespVO> getMyStudent(@PathVariable Long personId) {
        return success(studentService.getMyStudent(SecurityFrameworkUtils.getLoginUserId(), personId));
    }

    @GetMapping("/my/by-service/{relationId}")
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<MyStudentRespVO> getMyStudentByService(@PathVariable Long relationId) {
        return success(studentService.getMyStudentByService(SecurityFrameworkUtils.getLoginUserId(), relationId));
    }
}
