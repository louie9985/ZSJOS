package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;

import java.util.List;

public interface EamPurchaseService {
    Long createPurchase(EamPurchaseCreateReqVO reqVO, Long applicantUserId);
    EamPurchaseRespVO getPurchase(Long id);
    List<EamPurchaseRespVO> getPurchaseList();
    Long receive(Long purchaseId, EamReceiptCreateReqVO reqVO);
    Long returnToSupplier(Long purchaseId, EamReceiptCreateReqVO reqVO);
    void shortClose(Long purchaseId, EamShortCloseReqVO reqVO);
    void submitExpense(Long purchaseId, EamExpenseSubmitReqVO reqVO);
    void handlePurchaseProcessResult(Long purchaseId, Integer bpmStatus, String reason);
    void handleExpenseProcessResult(Long purchaseId, Integer bpmStatus, String reason);
}
