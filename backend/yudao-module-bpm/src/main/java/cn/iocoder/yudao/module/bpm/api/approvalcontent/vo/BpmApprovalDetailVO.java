package cn.iocoder.yudao.module.bpm.api.approvalcontent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批中心详情页用的业务详情卡。
 *
 * <p>与 {@link BpmApprovalBriefVO} 的区别：详情卡按分组组织字段，可以承载较长文本、
 * 行项目、附件；分组通过 {@link Group#getSpan()} 控制整行还是半行。
 */
@Schema(description = "管理后台 - 审批业务详情卡")
@Data
public class BpmApprovalDetailVO {

    @Schema(description = "业务类型", example = "withdrawal")
    private String bizType;

    @Schema(description = "业务标题", example = "WD20260101001")
    private String title;

    @Schema(description = "业务状态文案", example = "待审核")
    private String statusText;

    @Schema(description = "详情分组")
    private List<Group> groups = new ArrayList<>();

    @Schema(description = "打开业务详情的路由；为空表示该业务域未接入跳转")
    private String route;

    @Schema(description = "路由查询参数")
    private Map<String, Object> query;

    @Schema(description = "无法展开时的提示，例如权限不足")
    private String message;

    @Schema(description = "审批内容分组")
    @Data
    public static class Group {

        @Schema(description = "分组标题", example = "提现信息")
        private String title;

        @Schema(description = "分组内字段")
        private List<BpmApprovalFieldVO> fields = new ArrayList<>();

        @Schema(description = "整行占满（用于长文本、行项目），默认半行", example = "false")
        private Boolean span;
    }

    /**
     * 降级卡片：**有权看流程，但看不到业务内容**。
     *
     * <p>刻意不抛异常、也不返回裸 null——审批中心遇到 null 会退回通用展示，
     * 界面上"无权查看"和"本来就没有内容"长得一模一样，审批人无从判断是哪种情况。
     * 给出一句明确的提示，歧义就消失了。
     *
     * @param bizType  业务类型标识
     * @param subject  业务域的人话称呼，例如"提现申请"
     */
    public static BpmApprovalDetailVO noAccess(String bizType, String subject) {
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType);
        card.setMessage("当前账号无权查看该" + subject + "的详细信息，如需处理请联系管理员开通相应权限。");
        return card;
    }

    /**
     * 降级卡片：业务数据已不存在（被删除，或 businessKey 指向了错误的记录）。
     *
     * <p>与 {@link #noAccess} 分开，是因为两者的处理方式不同——前者要申请权限，
     * 后者要排查数据。
     */
    public static BpmApprovalDetailVO notFound(String bizType, String subject) {
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType);
        card.setMessage("未找到该" + subject + "对应的业务数据，可能已被删除。");
        return card;
    }
}
