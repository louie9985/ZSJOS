package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import lombok.Data;
import java.util.List;

@Data
public class EamEmployeeAssetSummaryRespVO {
    private Long employeeId;
    private List<EamEmployeeAssetItemRespVO> items;
    private List<EamEmployeeAssetTaskRespVO> tasks;
    private int pendingSignCount;
    private int pendingReturnCount;
    private boolean offboardingUncleared;
}
