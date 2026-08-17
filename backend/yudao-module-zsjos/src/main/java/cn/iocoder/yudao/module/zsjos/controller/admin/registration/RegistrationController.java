package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "员工工作台 - 报名履约公共池")
@RestController
@RequestMapping("/zsjos/registration")
public class RegistrationController {
    @Resource private RegistrationService registrationService;

    @GetMapping("/pool-page")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:query-pool')")
    public CommonResult<PageResult<RegistrationCaseRespVO>> getPoolPage(@Valid PageParam pageParam,
                                                                         @RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) String keyword) {
        return success(registrationService.getPoolPage(pageParam, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:query-pool')")
    public CommonResult<RegistrationCaseRespVO> get(@PathVariable Long id) { return success(registrationService.getCase(id)); }

    @GetMapping("/study-planner-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<List<StudyPlannerSimpleRespVO>> candidates() { return success(registrationService.getStudyPlannerCandidates()); }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                             @Valid @RequestBody RegistrationChecklistItemUpdateReqVO reqVO) {
        return success(registrationService.updateChecklistItem(id, itemId, SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/{id}/study-planner")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> updatePlanner(@PathVariable Long id,
                                                @Valid @RequestBody RegistrationPlannerUpdateReqVO reqVO) {
        return success(registrationService.updateStudyPlanner(id, SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long id, @Valid @RequestBody RegistrationVersionReqVO reqVO) {
        registrationService.complete(id, SecurityFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }
}
