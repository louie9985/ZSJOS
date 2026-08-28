package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import lombok.Data;

@Data
public class BpmProcessInstanceRelationDetailRespVO {
    private BpmProcessInstanceRelationRespVO relation;
    private BpmApprovalDetailRespVO approvalDetail;
    private BpmProcessInstanceBpmnModelViewRespVO modelView;
}
