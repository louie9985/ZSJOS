package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;

import java.util.List;
import java.util.Map;

/**
 * 审批内容查询服务：审批中心通过它把"流程任务"翻译成"业务内容"。
 *
 * <p>鉴权是<b>逐任务</b>的，不做批量放宽：每个 taskId 都要能通过
 * {@code BpmProcessTaskApi} 的归属校验（非本人任务直接拿不到），才继续解析业务内容。
 * 批量接口只是省去 N 次 HTTP，不是放宽权限。
 */
public interface BpmApprovalContentService {

    /**
     * 单个任务的业务摘要。无法解析（未接入、无权、格式异常）时返回 null。
     *
     * @param taskId   流程任务编号
     * @param view     {@code todo} 或 {@code done}
     * @param viewerId 当前登录用户
     */
    BpmApprovalBriefVO getBrief(String taskId, String view, Long viewerId);

    /**
     * 批量业务摘要，key 为 taskId。
     *
     * <p>列表页一次几十条，逐条调用会产生 N 次往返，因此只暴露批量入口。
     * 返回的 Map 只包含<b>成功解析</b>的任务；调用方对缺失的 key 退回通用展示。
     */
    Map<String, BpmApprovalBriefVO> getBriefMap(List<String> taskIds, String view, Long viewerId);

    /**
     * 单个任务的业务详情卡。无权或未接入时返回 null。
     */
    BpmApprovalDetailVO getDetail(String taskId, String view, Long viewerId);
}
