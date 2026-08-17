package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistDraftSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationVersionReqVO;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationChecklistConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 报名履约清单配置")
@RestController
@RequestMapping("/zsjos/registration-checklist-config")
public class RegistrationChecklistConfigController {
    @Resource private RegistrationChecklistConfigService configService;

    @GetMapping
    @Operation(summary = "获得报名履约清单草稿和已发布版本")
    @PreAuthorize("@ss.hasPermission('zsjos:registration-checklist-config:query')")
    public CommonResult<RegistrationChecklistConfigRespVO> getConfig() { return success(configService.getConfig()); }

    @PostMapping("/draft/copy")
    @PreAuthorize("@ss.hasPermission('zsjos:registration-checklist-config:update')")
    public CommonResult<Long> copyDraft(@Valid @RequestBody RegistrationVersionReqVO reqVO) {
        return success(configService.copyPublishedToDraft(reqVO.getVersion()));
    }

    @PutMapping("/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:registration-checklist-config:update')")
    public CommonResult<Boolean> saveDraft(@Valid @RequestBody RegistrationChecklistDraftSaveReqVO reqVO) {
        configService.saveDraft(reqVO); return success(true);
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:registration-checklist-config:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody RegistrationVersionReqVO reqVO) {
        configService.publish(reqVO.getVersion()); return success(true);
    }
}
