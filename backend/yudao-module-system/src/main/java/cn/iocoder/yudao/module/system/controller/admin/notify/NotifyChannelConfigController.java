package cn.iocoder.yudao.module.system.controller.admin.notify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.channel.*;
import cn.iocoder.yudao.module.system.service.notify.NotifyChannelConfigService;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyChannelConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 通知渠道")
@RestController
@RequestMapping("/system/notify-channel")
public class NotifyChannelConfigController {
    @Resource private NotifyChannelConfigService service;

    @GetMapping("/get")
    @Operation(summary = "获得通知渠道配置")
    @PreAuthorize("@ss.hasPermission('system:notify-channel:query')")
    public CommonResult<NotifyChannelConfigRespVO> get(@RequestParam String channelCode) {
        NotifyChannelConfig config = service.get(channelCode);
        if (config == null) return success(null);
        NotifyChannelConfigRespVO vo = new NotifyChannelConfigRespVO();
        vo.setChannelCode(config.getChannelCode()); vo.setEnabled(config.getEnabled());
        vo.setConfigRef(config.getProvider()); vo.setMaskedConfig(config.getConfigJson());
        vo.setSocialClientConfigured(Boolean.TRUE.equals(config.getEnabled())
                && service.getEnabled(tenantId(), channelCode) != null);
        return success(vo);
    }

    @PutMapping("/update")
    @Operation(summary = "启用或停用通知渠道")
    @PreAuthorize("@ss.hasPermission('system:notify-channel:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody NotifyChannelConfigUpdateReqVO reqVO) {
        service.updateEnabled(reqVO.getChannelCode(), reqVO.getEnabled());
        return success(true);
    }

    private Long tenantId() { return cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId(); }
}
