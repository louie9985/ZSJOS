package cn.iocoder.yudao.module.zsjos.controller.admin.positioning;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 新媒体定位卡")
@RestController
@RequestMapping("/zsjos/positioning-card")
public class PositioningCardController {
    @Resource private PositioningCardService service;
    @PostMapping("/create") @Operation(summary = "创建定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<Long> create(@Valid @RequestBody PositioningCardSaveReqVO req) { return success(service.create(req, getLoginUserId())); }
    @GetMapping("/get") @Operation(summary = "获得定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }
    @GetMapping("/page") @Operation(summary="分页查询定位卡") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')") public CommonResult<PageResult<PositioningCardRespVO>> page(@Valid PositioningCardPageReqVO req){ return success(service.page(req, getLoginUserId())); }
    @PostMapping("/{id}/submit-review") @Operation(summary = "提交定位审核")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:submit-review')")
    public CommonResult<Boolean> submitReview(@PathVariable Long id, @RequestParam Integer version) {
        service.submitReview(id, version, getLoginUserId()); return success(true);
    }
    @PostMapping("/{id}/operator-approve") @Operation(summary = "运营可行性复核通过")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:feasibility-review')")
    public CommonResult<Boolean> operatorApprove(@PathVariable Long id, @RequestParam Integer version) {
        service.operatorApprove(id, version); return success(true);
    }
    @PostMapping("/{id}/operator-reject") @Operation(summary = "运营可行性复核退回")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:feasibility-review')")
    public CommonResult<Boolean> operatorReject(@PathVariable Long id, @RequestParam Integer version) {
        service.operatorReject(id, version); return success(true);
    }
    @PostMapping("/{id}/confirm-trial") @Operation(summary = "确认十四天试跑结果")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:confirm-trial')")
    public CommonResult<Boolean> confirmTrial(@PathVariable Long id, @RequestParam Integer version) {
        service.confirmTrial(id, version); return success(true);
    }
    @PostMapping("/{id}/archive") @Operation(summary = "归档定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:archive')")
    public CommonResult<Boolean> archive(@PathVariable Long id, @RequestParam Integer version) {
        service.archive(id, version); return success(true);
    }
}
