package cn.iocoder.yudao.module.zsjos.controller.admin.workorder;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 通用工单")
@RestController
@RequestMapping("/zsjos/work-order")
public class WorkOrderController {
    @Resource private WorkOrderService service;
    @PostMapping("/scene/create") @Operation(summary = "创建工单场景") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:create')") public CommonResult<Long> createScene(@Valid @RequestBody WorkOrderSceneCreateReqVO req) { return success(service.createScene(req, getLoginUserId())); }
    @PutMapping("/scene/update") @Operation(summary = "更新工单场景") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:update')") public CommonResult<Boolean> updateScene(@Valid @RequestBody WorkOrderSceneUpdateReqVO req) { service.updateScene(req, getLoginUserId()); return success(true); }
    @GetMapping("/scene/page") @Operation(summary = "工单场景分页") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:query')") public CommonResult<PageResult<WorkOrderSceneRespVO>> scenePage(@Valid WorkOrderScenePageReqVO req) { return success(service.scenePage(req.getPageNo(), req.getPageSize())); }
    @GetMapping("/scene/get") @Operation(summary = "工单场景详情") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:query')") public CommonResult<WorkOrderSceneRespVO> scene(@RequestParam String code) { return success(service.getScene(code)); }
    @PostMapping("/create") @Operation(summary = "发起工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:create')") public CommonResult<Long> create(@Valid @RequestBody WorkOrderCreateReqVO req) { return success(service.create(req, getLoginUserId())); }
    @PostMapping("/{id}/claim") @Operation(summary = "抢单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:claim')") public CommonResult<Boolean> claim(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.claim(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/complete") @Operation(summary = "完成工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:complete')") public CommonResult<Boolean> complete(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.complete(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/accept") @Operation(summary = "验收工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:accept')") public CommonResult<Boolean> accept(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.accept(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/return") @Operation(summary = "退回重做") @PreAuthorize("@ss.hasPermission('zsjos:work-order:return')") public CommonResult<Boolean> returnForRework(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.returnForRework(id, req, getLoginUserId()); return success(true); }
    @GetMapping("/my-page") @Operation(summary = "我的工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<PageResult<WorkOrderRespVO>> myPage(@Valid WorkOrderMyPageReqVO req) { return success(service.myPage(req.getStatus(), req.getPageNo(), req.getPageSize(), getLoginUserId())); }
    @GetMapping("/pool") @Operation(summary = "工单抢单池") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<PageResult<WorkOrderRespVO>> pool(@Valid WorkOrderPoolPageReqVO req) { return success(service.pool(req.getSceneCode(), req.getPageNo(), req.getPageSize(), getLoginUserId())); }
    @GetMapping("/{id}") @Operation(summary = "工单详情") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<WorkOrderRespVO> get(@PathVariable Long id) { return success(service.get(id, getLoginUserId())); }
}
