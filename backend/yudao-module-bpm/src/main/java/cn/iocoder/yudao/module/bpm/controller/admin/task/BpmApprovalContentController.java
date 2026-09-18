package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentService;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmApprovalContentBatchReqVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 审批中心的业务内容查询。
 *
 * <p>审批中心是通用列表，只认识流程；业务内容由各业务域的 Provider 提供，
 * 见 {@link BpmApprovalContentService}。新增业务域只需在自己的模块里实现
 * {@code BpmApprovalContentProvider}，本接口无需改动。
 */
@Tag(name = "管理后台 - 审批业务内容")
@RestController
@RequestMapping("/bpm/approval-content")
public class BpmApprovalContentController {

    /**
     * 字段名不能叫 contentService：{@code @Resource} 按名字注入，而 zsjos 模块的
     * {@code ContentService} 生成的 bean 正好也叫这个名，启动时会注入成那个类而失败。
     */
    @Resource
    private BpmApprovalContentService approvalContentService;

    @PostMapping("/business-summary-batch")
    @Operation(summary = "批量获得审批任务的业务摘要")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<Map<String, BpmApprovalBriefVO>> getBusinessSummaryBatch(
            @Valid @RequestBody BpmApprovalContentBatchReqVO reqVO) {
        return success(approvalContentService.getBriefMap(reqVO.getTaskIds(), reqVO.getView(),
                WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/business-detail")
    @Operation(summary = "获得审批任务的业务详情卡")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<BpmApprovalDetailVO> getBusinessDetail(
            @RequestParam @NotBlank String taskId,
            @RequestParam(defaultValue = "todo") String view) {
        return success(approvalContentService.getDetail(taskId, view, WebFrameworkUtils.getLoginUserId()));
    }
}
