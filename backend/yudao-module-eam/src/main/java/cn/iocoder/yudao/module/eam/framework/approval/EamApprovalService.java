package cn.iocoder.yudao.module.eam.framework.approval;

/**
 * EAM 审批适配接口
 *
 * EAM 只持有业务单据、业务状态和流程实例引用；流程定义、任务、审批人、审批历史归 BPM 所有。
 * 本接口是两者之间唯一的耦合点：替换实现即可在「模块内直通」与「BPM 工作流」之间切换，
 * 业务代码无需改动。
 */
public interface EamApprovalService {

    /**
     * 发起一次审批
     *
     * @param definitionKey 流程定义 Key，如 eam-transfer / eam-scrap
     * @param businessKey   业务主键，通常是单据 ID 的字符串形式
     * @param summary       审批摘要，用于待办展示
     * @return 流程实例 ID；返回 null 表示未走流程（直通生效）
     */
    String start(String definitionKey, String businessKey, String summary);

    /**
     * 当前实现是否会真正产生审批环节
     *
     * 直通实现返回 false，调用方据此在创建单据时直接置为生效状态。
     */
    boolean approvalRequired();

}
