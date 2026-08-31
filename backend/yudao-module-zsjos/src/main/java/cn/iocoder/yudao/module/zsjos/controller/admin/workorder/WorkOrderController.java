package cn.iocoder.yudao.module.zsjos.controller.admin.workorder;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import cn.iocoder.yudao.module.zsjos.service.production.ProductionTicketService;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.io.IoUtil;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_ORDER_RELATED_ACCOUNT_REQUIRED;

@Tag(name = "管理后台 - 通用工单")
@RestController
@RequestMapping("/zsjos/work-order")
public class WorkOrderController {
    @Resource private WorkOrderService service;
    @Resource private ProductionTicketService productionTicketService;
    @PostMapping("/file/upload") @Operation(summary = "上传工单附件") @PreAuthorize("@ss.hasAnyPermissions('zsjos:work-order:create','zsjos:work-order:complete')") public CommonResult<WorkOrderFileRespVO> upload(@RequestParam("file") MultipartFile file) throws Exception { return success(service.upload(IoUtil.readBytes(file.getInputStream()), file.getOriginalFilename(), file.getContentType(), getLoginUserId())); }
    @PostMapping("/scene/create") @Operation(summary = "创建工单场景") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:create')") public CommonResult<Long> createScene(@Valid @RequestBody WorkOrderSceneCreateReqVO req) { return success(service.createScene(req, getLoginUserId())); }
    @PutMapping("/scene/update") @Operation(summary = "更新工单场景") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:update')") public CommonResult<Boolean> updateScene(@Valid @RequestBody WorkOrderSceneUpdateReqVO req) { service.updateScene(req, getLoginUserId()); return success(true); }
    @GetMapping("/scene/page") @Operation(summary = "工单场景分页") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:query')") public CommonResult<PageResult<WorkOrderSceneRespVO>> scenePage(@Valid WorkOrderScenePageReqVO req) { return success(service.scenePage(req.getPageNo(), req.getPageSize())); }
    @GetMapping("/scene/get") @Operation(summary = "工单场景详情") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:query')") public CommonResult<WorkOrderSceneRespVO> scene(@RequestParam String code) { return success(service.getScene(code)); }
    @GetMapping("/scene/publish-validation") @Operation(summary = "校验模板发布条件") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:publish')") public CommonResult<WorkOrderScenePublishValidationRespVO> validateScenePublish(@RequestParam Long id) { return success(service.validateScenePublish(id)); }
    @PostMapping("/scene/publish") @Operation(summary = "发布工单模板") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:publish')") public CommonResult<Boolean> publishScene(@Valid @RequestBody WorkOrderScenePublishReqVO req) { service.publishScene(req, getLoginUserId()); return success(true); }
    @PutMapping("/scene/disable") @Operation(summary = "停用工单模板") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:disable')") public CommonResult<Boolean> disableScene(@RequestParam Long id, @RequestParam Integer version) { service.disableScene(id, version, getLoginUserId()); return success(true); }
    @GetMapping("/scene/versions") @Operation(summary = "模板发布版本历史") @PreAuthorize("@ss.hasPermission('zsjos:work-order-scene:query')") public CommonResult<java.util.List<WorkOrderSceneRespVO>> sceneVersions(@RequestParam Long id) { return success(service.sceneVersions(id)); }
    @GetMapping("/catalog") @Operation(summary = "可发起工单模板目录") @PreAuthorize("@ss.hasPermission('zsjos:work-order:create')") public CommonResult<PageResult<WorkOrderSceneRespVO>> catalog(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "100") int pageSize) { return success(service.catalog(pageNo, pageSize, getLoginUserId())); }
    @GetMapping("/candidate-page") @Operation(summary = "工单接收人候选") @PreAuthorize("@ss.hasPermission('zsjos:work-order:create')") public CommonResult<PageResult<WorkOrderCandidateRespVO>> candidatePage(@Valid WorkOrderCandidatePageReqVO req) { return success(service.candidatePage(req, getLoginUserId())); }
    @GetMapping("/candidate-department-page") @Operation(summary = "工单接收部门候选") @PreAuthorize("@ss.hasPermission('zsjos:work-order:create')") public CommonResult<PageResult<WorkOrderCandidateRespVO>> candidateDepartmentPage(@Valid WorkOrderCandidatePageReqVO req) { return success(service.candidateDepartmentPage(req, getLoginUserId())); }
    @PostMapping("/create") @Operation(summary = "发起工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:create')") public CommonResult<Long> create(@Valid @RequestBody WorkOrderCreateReqVO req) {
        Long userId = getLoginUserId();
        if (!service.isProductionTemplate(req.getSceneCode(), userId)) return success(service.create(req, userId));
        if (req.getRelatedAccountId() == null) throw exception(WORK_ORDER_RELATED_ACCOUNT_REQUIRED);
        ProductionTicketSaveReqVO ticket = new ProductionTicketSaveReqVO();
        ticket.setSceneCode(req.getSceneCode()); ticket.setAccountId(req.getRelatedAccountId());
        ticket.setAssigneeUserId(req.getTargetUserId()); ticket.setTargetDeptId(req.getTargetDeptId());
        ticket.setOperatorRemark(req.getRemark()); ticket.setValues(req.getValues());
        ticket.setAttachmentIds(req.getAttachmentIds()); ticket.setIdempotencyKey(req.getIdempotencyKey());
        return success(productionTicketService.createFromWorkOrder(ticket, userId));
    }
    @PostMapping("/{id}/take") @Operation(summary = "接受指定工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:take')") public CommonResult<Boolean> take(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.take(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/claim") @Operation(summary = "抢单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:claim')") public CommonResult<Boolean> claim(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.claim(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/complete") @Operation(summary = "完成工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:complete')") public CommonResult<Boolean> complete(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.complete(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/accept") @Operation(summary = "验收工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:accept')") public CommonResult<Boolean> accept(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.accept(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/return") @Operation(summary = "退回重做") @PreAuthorize("@ss.hasPermission('zsjos:work-order:return')") public CommonResult<Boolean> returnForRework(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.returnForRework(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/reject") @Operation(summary = "拒绝接单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:reject')") public CommonResult<Boolean> reject(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.reject(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/withdraw") @Operation(summary = "撤回工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:withdraw')") public CommonResult<Boolean> withdraw(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.withdraw(id, req, getLoginUserId()); return success(true); }
    @PostMapping("/{id}/terminate") @Operation(summary = "验收终止") @PreAuthorize("@ss.hasPermission('zsjos:work-order:terminate')") public CommonResult<Boolean> terminate(@PathVariable Long id, @Valid @RequestBody WorkOrderActionReqVO req) { service.terminate(id, req, getLoginUserId()); return success(true); }
    @GetMapping("/my-page") @Operation(summary = "我的工单") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<PageResult<WorkOrderRespVO>> myPage(@Valid WorkOrderMyPageReqVO req) { return success(service.myPage(req.getStatus(), req.getView(), req.getPageNo(), req.getPageSize(), getLoginUserId())); }
    @GetMapping("/pool") @Operation(summary = "工单抢单池") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<PageResult<WorkOrderRespVO>> pool(@Valid WorkOrderPoolPageReqVO req) { return success(service.pool(req.getSceneCode(), req.getPageNo(), req.getPageSize(), getLoginUserId())); }
    @GetMapping("/audit/page") @Operation(summary = "管理员只读审计工单分页") @PreAuthorize("@ss.hasPermission('zsjos:work-order:audit')") public CommonResult<PageResult<WorkOrderRespVO>> auditPage(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) { return success(service.auditPage(status, pageNo, pageSize)); }
    @GetMapping("/audit/{id}") @Operation(summary = "管理员只读审计工单详情") @PreAuthorize("@ss.hasPermission('zsjos:work-order:audit')") public CommonResult<WorkOrderRespVO> auditGet(@PathVariable Long id) { return success(service.auditGet(id)); }
    @GetMapping("/{id}") @Operation(summary = "工单详情") @PreAuthorize("@ss.hasPermission('zsjos:work-order:query')") public CommonResult<WorkOrderRespVO> get(@PathVariable Long id) { return success(service.get(id, getLoginUserId())); }
}
