package cn.iocoder.yudao.module.zsjos.controller.admin.workplan;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;
import cn.iocoder.yudao.module.zsjos.service.workplan.WorkPlanService;
import cn.iocoder.yudao.module.zsjos.service.workplan.WorkPlanTemplateService;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 通用工作计划")
@RestController
@RequestMapping("/zsjos/work-plan")
public class WorkPlanController {
    @Resource private WorkPlanService workPlanService;
    @Resource private WorkPlanTemplateService workPlanTemplateService;

    @GetMapping("/templates/available") @Operation(summary = "获得当前用户可用的已发布计划模板")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:create')")
    public CommonResult<List<WorkPlanTemplateRespVO>> availableTemplates() {
        return success(workPlanTemplateService.getAvailableTemplates(getLoginUserId()));
    }

    @GetMapping("/page") @Operation(summary = "获得可见工作计划分页")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:query')")
    public CommonResult<PageResult<WorkPlanRespVO>> page(@Valid WorkPlanPageReqVO reqVO) {
        return success(workPlanService.getPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/search-page") @Operation(summary = "按固定条件和计划目标字段筛选工作计划")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:query')")
    public CommonResult<PageResult<WorkPlanRespVO>> searchPage(@Valid @RequestBody WorkPlanSearchReqVO reqVO) {
        return success(workPlanService.searchPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/export-excel") @Operation(summary = "导出可见计划任务明细")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:export')")
    public void export(@Valid @RequestBody WorkPlanSearchReqVO reqVO, HttpServletResponse response) throws IOException {
        WorkPlanExportData data = workPlanService.export(reqVO, getLoginUserId());
        FastExcelFactory.write(response.getOutputStream()).head(data.getHeaders()).autoCloseStream(false)
                .sheet("任务明细").doWrite(data.getRows());
        response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8("计划任务明细.xlsx"));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
    }

    @GetMapping("/get") @Operation(summary = "获得工作计划详情")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:query')")
    public CommonResult<WorkPlanRespVO> get(@RequestParam("id") Long id) {
        return success(workPlanService.get(id, getLoginUserId()));
    }

    @PostMapping("/create") @Operation(summary = "创建工作计划草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:create')")
    public CommonResult<Long> create(@Valid @RequestBody WorkPlanSaveReqVO reqVO) {
        return success(workPlanService.create(reqVO, getLoginUserId()));
    }

    @PutMapping("/{id}") @Operation(summary = "修改草稿或受控调整进行中计划")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:update')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody WorkPlanSaveReqVO reqVO) {
        workPlanService.update(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/{id}/publish") @Operation(summary = "直接发布工作计划")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:publish')")
    public CommonResult<Boolean> publish(@PathVariable Long id, @Valid @RequestBody WorkPlanVersionReqVO reqVO) {
        workPlanService.publish(id, reqVO.getVersion(), getLoginUserId()); return success(true);
    }

    @PostMapping("/{id}/cancel") @Operation(summary = "取消工作计划")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:cancel')")
    public CommonResult<Boolean> cancel(@PathVariable Long id, @Valid @RequestBody WorkPlanCancelReqVO reqVO) {
        workPlanService.cancel(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/{planId}/task") @Operation(summary = "分派顶层或下级工作任务")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:work-plan:assign', 'zsjos:work-plan:decompose')")
    public CommonResult<Long> addTask(@PathVariable Long planId, @Valid @RequestBody WorkTaskSaveReqVO reqVO) {
        return success(workPlanService.addTask(planId, reqVO, getLoginUserId()));
    }

    @PostMapping("/task/temporary") @Operation(summary = "创建临时工作任务")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:assign')")
    public CommonResult<Long> createTemporaryTask(@Valid @RequestBody WorkTaskSaveReqVO reqVO) {
        return success(workPlanService.createTemporaryTask(reqVO, getLoginUserId()));
    }

    @GetMapping("/task/get") @Operation(summary = "获得工作任务详情")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:query')")
    public CommonResult<WorkTaskRespVO> getTask(@RequestParam("id") Long id) {
        return success(workPlanService.getTask(id, getLoginUserId()));
    }

    @GetMapping("/task/my-page") @Operation(summary = "获得与我相关的工作任务分页")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:query')")
    public CommonResult<PageResult<WorkTaskRespVO>> myTaskPage(@Valid PageParam pageParam,
                                                               @RequestParam(value = "status", required = false) String status) {
        return success(workPlanService.getMyTaskPage(pageParam, status, getLoginUserId()));
    }

    @PutMapping("/task/{id}") @Operation(summary = "受控调整工作任务")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:assign')")
    public CommonResult<Boolean> adjustTask(@PathVariable Long id, @Valid @RequestBody WorkTaskSaveReqVO reqVO) {
        workPlanService.adjustTask(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/task/{id}/cancel") @Operation(summary = "取消工作任务及未完成下级任务")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:cancel')")
    public CommonResult<Boolean> cancelTask(@PathVariable Long id, @Valid @RequestBody WorkPlanCancelReqVO reqVO) {
        workPlanService.cancelTask(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/task/{id}/report") @Operation(summary = "提交完成汇报")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:complete')")
    public CommonResult<Boolean> submitReport(@PathVariable Long id, @Valid @RequestBody WorkReportSubmitReqVO reqVO) {
        workPlanService.submitReport(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/task/{id}/confirm") @Operation(summary = "确认任务完成或退回修改")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:review')")
    public CommonResult<Boolean> confirmReport(@PathVariable Long id, @Valid @RequestBody WorkReportConfirmReqVO reqVO) {
        workPlanService.confirmReport(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/{id}/summary") @Operation(summary = "提交计划总结并完成计划")
    @PreAuthorize("@ss.hasPermission('zsjos:work-plan:close')")
    public CommonResult<Boolean> submitSummary(@PathVariable Long id, @Valid @RequestBody WorkPlanSummaryReqVO reqVO) {
        workPlanService.submitSummary(id, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/attachment/upload") @Operation(summary = "上传工作计划附件")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:work-plan:create', 'zsjos:work-plan:update', " +
            "'zsjos:work-plan:assign', 'zsjos:work-plan:complete', 'zsjos:work-plan:close')")
    public CommonResult<WorkPlanAttachmentUploadRespVO> uploadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(workPlanService.uploadAttachment(file, getLoginUserId()));
    }
}
