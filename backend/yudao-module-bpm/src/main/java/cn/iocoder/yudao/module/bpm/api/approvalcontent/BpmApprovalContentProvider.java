package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;

/**
 * 审批内容提供方：把一条审批流程的 businessKey 翻译成审批中心能展示的业务内容。
 *
 * <p><b>为什么存在</b>：审批中心是通用的，它只认识流程（节点、状态、发起人），
 * 不认识任何业务域。而审批人要判断该不该通过，看的必须是业务内容——
 * 金额、客户、班级、资产这些。中间这层翻译就是 Provider。
 *
 * <p><b>不落摘要快照</b>：业务数据改了，审批中心看到的就是最新值。
 * 流程本身已经冻结了 businessKey，定位业务实体足够了。例外是那些业务侧
 * 已经存了快照的场景（如转班的班级名），此时应优先用快照——审批人要看的是
 * 申请当时的样子，不是现在的样子。
 *
 * <p>每个业务域实现一个，放在自己的模块里（{@code yudao-module-zsjos}、
 * {@code yudao-module-eam} 等），由 {@link BpmApprovalContentRegistry} 统一调度。
 */
public interface BpmApprovalContentProvider {

    /**
     * 业务类型标识，用于前端埋点与日志，例如 {@code withdrawal}。
     */
    String bizType();

    /**
     * 本 Provider 认领的 businessKey 前缀，例如 {@code "withdrawal:"}。
     *
     * <p>注册表按前缀长度倒序匹配，因此更长的前缀优先命中。
     * 这一点是必须的：{@code student-delivery-defer:} 与
     * {@code student-contact-extension:} 共用同一个流程定义 key，只能靠前缀区分。
     *
     * <p>前缀全局必须唯一，重复会导致应用启动失败（注册表构造期校验）。
     */
    String businessKeyPrefix();

    /**
     * 从 businessKey 中取出业务主键。
     *
     * <p>各域格式并不统一，且这个不一致是既成事实：
     * <ul>
     *   <li>两段式 {@code "withdrawal:12"}</li>
     *   <li>三段式 {@code "media-rebind:12:v3"}（第三段是版本号）</li>
     *   <li>四段式 {@code "feedback:9:round:2"}（第二段是 workOrderId）</li>
     * </ul>
     * 因此解析逻辑交由各 Provider 自己实现，<b>不要</b>假设能统一按
     * {@code split(":")[1]} 取值——上述两个例子那样写都会取错。
     *
     * @return 业务主键；格式不符时返回 null（调用方据此退回通用展示）
     */
    String parseBusinessId(String businessKey);

    /**
     * 列表摘要。返回 null 表示该任务不展示业务内容，审批中心退回通用展示。
     *
     * @param businessId 已解析的业务主键
     * @param viewerId   当前登录用户；Provider 必须据此校验是否有权查看
     */
    BpmApprovalBriefVO brief(String businessId, Long viewerId);

    /**
     * 详情卡。
     *
     * <p>权限不足时返回带 message 的空卡片，**不要抛异常**——审批中心仍然要能
     * 展示流程本身（节点、审批记录），只是不展示业务内容。
     */
    BpmApprovalDetailVO detail(String businessId, Long viewerId);
}
