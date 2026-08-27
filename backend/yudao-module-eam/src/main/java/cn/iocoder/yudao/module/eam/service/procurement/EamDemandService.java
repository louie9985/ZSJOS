package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;

import java.util.List;

public interface EamDemandService {
    Long createDemand(EamDemandCreateReqVO reqVO, Long applicantUserId);
    EamDemandRespVO getDemand(Long id);
    List<EamDemandRespVO> getMyDemands(Long userId);
    List<EamDemandRespVO> getDemandList();
    List<EamStockCandidateRespVO> getCandidates(Long demandItemId);
    List<EamStockCandidateRespVO> previewCandidates(EamDemandItemReqVO reqVO);
    Long reserve(EamStockReserveReqVO reqVO);
    Long reserveAndAllocate(EamStockReserveReqVO reqVO);
    void allocate(EamStockAllocateReqVO reqVO);
    Long createTaskDemand(EamDemandCreateReqVO reqVO, Long employeeId, Long applicantUserId, String processKey,
                          Long businessId);
    void addFulfilledQuantity(Long demandItemId, int quantity);
    void addClosedQuantity(Long demandItemId, int quantity);
    void handleProcessResult(Long demandId, Integer bpmStatus, String reason);
}
