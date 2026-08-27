package cn.iocoder.yudao.module.eam.service.employeeasset;

import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.EamEmployeeAssetSummaryRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.EamEmployeeAssetTaskActionReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.EamEmployeeAssetTaskRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.EamEmployeeAssetTaskSubmitReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReturnInspectReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEvent;

public interface EamEmployeeAssetService {
    EamEmployeeAssetSummaryRespVO getByEmployeeId(Long employeeId);
    EamEmployeeAssetSummaryRespVO getByUserId(Long userId);
    Long createHolding(Long employeeId, Long assetId, Long stockBalanceId,
                       String name, String unit, Integer quantity, Integer custodyMode);
    void sign(Long holdingId, Long userId);
    void applyReturn(Long holdingId, Long userId, String remark);
    void inspectReturn(Long holdingId, EamReturnInspectReqVO reqVO);
    void handleLifecycleEvent(HrmEmployeeLifecycleEvent event);
    EamEmployeeAssetTaskRespVO getTask(Long taskId);
    void submitProvisioning(Long taskId, EamEmployeeAssetTaskSubmitReqVO reqVO, Long applicantUserId);
    void submitReview(Long taskId, EamEmployeeAssetTaskActionReqVO reqVO);
    void handleReviewProcessResult(Long taskId, Integer bpmStatus, String reason);
}
