package cn.iocoder.yudao.module.system.controller.admin.notify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 业务通知规则")
@RestController
@RequestMapping("/system/notify-rule")
public class NotifyRuleController {

    @Resource private NotifyRuleService notifyRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建业务通知规则")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:create')")
    public CommonResult<Long> create(@Valid @RequestBody NotifyRuleSaveReqVO reqVO) {
        return success(notifyRuleService.createNotifyRule(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新业务通知规则")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody NotifyRuleSaveReqVO reqVO) {
        notifyRuleService.updateNotifyRule(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除业务通知规则")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        notifyRuleService.deleteNotifyRule(id);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用或停用业务通知规则")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody NotifyRuleStatusReqVO reqVO) {
        notifyRuleService.updateNotifyRuleStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得业务通知规则")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('system:notify-rule:query')")
    public CommonResult<NotifyRuleRespVO> get(@RequestParam Long id) {
        return success(BeanUtils.toBean(notifyRuleService.getNotifyRule(id), NotifyRuleRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得业务通知规则分页")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:query')")
    public CommonResult<PageResult<NotifyRuleRespVO>> page(@Valid NotifyRulePageReqVO reqVO) {
        PageResult<NotifyRuleDO> result = notifyRuleService.getNotifyRulePage(reqVO);
        return success(BeanUtils.toBean(result, NotifyRuleRespVO.class));
    }
}
