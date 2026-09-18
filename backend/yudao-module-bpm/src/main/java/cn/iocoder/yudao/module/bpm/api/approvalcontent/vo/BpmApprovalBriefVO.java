package cn.iocoder.yudao.module.bpm.api.approvalcontent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批中心列表用的业务摘要（一个任务对应一条）。
 *
 * <p>审批中心把 {@code title} 当作"这条单子是什么"的主文案，
 * {@code fields} 作为摘要列的若干"标签：值"。
 */
@Schema(description = "管理后台 - 审批业务摘要")
@Data
public class BpmApprovalBriefVO {

    @Schema(description = "业务类型", example = "withdrawal")
    private String bizType;

    @Schema(description = "业务标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "WD20260101001")
    private String title;

    @Schema(description = "业务副标题", example = "申请金额 1280.00")
    private String subtitle;

    /** 摘要字段。控制在 3~5 个以内，审批中心只取前几个渲染。 */
    @Schema(description = "摘要字段")
    private List<BpmApprovalFieldVO> fields = new ArrayList<>();

    @Schema(description = "打开业务详情的路由；为空表示该业务域未接入跳转")
    private String route;

    @Schema(description = "路由查询参数")
    private Map<String, Object> query;

    @Schema(description = "跳转不可用时的用户提示")
    private String message;
}
