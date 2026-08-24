package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountFieldConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/media-account-field-config")
@Validated
public class MediaAccountFieldConfigController {
    @Resource private MediaAccountFieldConfigService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:media-account-field-config:query')")
    public CommonResult<MediaAccountFieldConfigRespVO> getConfig() {
        return success(service.getConfig());
    }

    @GetMapping("/published")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-student:query-my')")
    public CommonResult<MediaAccountFieldConfigRespVO.VersionVO> getPublished() {
        return success(service.getPublished());
    }

    @PostMapping("/draft/copy")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account-field-config:update')")
    public CommonResult<Long> copyDraft(@Valid @RequestBody VersionReq request) {
        return success(service.copyDraft(request.getId(), request.getVersion()));
    }

    @PutMapping("/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account-field-config:update')")
    public CommonResult<Boolean> updateDraft(@Valid @RequestBody MediaAccountFieldConfigSaveReqVO request) {
        service.updateDraft(request); return success(true);
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account-field-config:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody VersionReq request) {
        service.publish(request.getId(), request.getVersion()); return success(true);
    }

    @Data
    public static class VersionReq {
        @NotNull private Long id;
        @NotNull private Integer version;
    }
}
