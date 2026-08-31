package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchModeReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.SalesDispatchStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 销售派单状态")
@RestController
@RequestMapping("/zsjos/lead/dispatch-status")
@PreAuthorize("@ss.hasPermission('zsjos:lead:accept')")
public class SalesDispatchStatusController {

    @Resource private SalesDispatchStatusService statusService;

    @GetMapping("/my")
    @Operation(summary = "获得我的派单状态")
    public CommonResult<SalesDispatchStatusRespVO> getMyStatus() {
        return success(statusService.getMyStatus(getLoginUserId()));
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "刷新销售页面在线心跳")
    public CommonResult<SalesDispatchStatusRespVO> heartbeat() {
        return success(statusService.heartbeat(getLoginUserId()));
    }

    @PutMapping("/mode")
    @Operation(summary = "开启或暂停接单")
    public CommonResult<SalesDispatchStatusRespVO> updateMode(@Valid @RequestBody SalesDispatchModeReqVO reqVO) {
        return success(statusService.updateMode(getLoginUserId(), reqVO.getAccepting()));
    }

    @PostMapping("/offline")
    @Operation(summary = "销售页面主动离线")
    public CommonResult<SalesDispatchStatusRespVO> offline() {
        return success(statusService.offline(getLoginUserId()));
    }
}
