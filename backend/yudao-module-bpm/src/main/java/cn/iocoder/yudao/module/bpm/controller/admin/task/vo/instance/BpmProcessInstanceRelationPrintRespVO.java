package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import lombok.Data;

@Data
public class BpmProcessInstanceRelationPrintRespVO {
    private BpmProcessInstanceRelationRespVO relation;
    private BpmProcessPrintDataRespVO printData;
}
