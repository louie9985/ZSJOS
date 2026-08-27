package cn.iocoder.yudao.module.eam.service.stock;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockCandidateRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockReserveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReservationDO;

import java.util.List;
import java.util.Map;

public interface EamStockService {
    List<EamStockCandidateRespVO> getCandidates(EamDemandItemDO demandItem);
    Long reserve(EamDemandItemDO demandItem, Long employeeId, EamStockReserveReqVO reqVO);
    void allocateReservation(Long reservationId, EamDemandItemDO demandItem, Long targetDeptId);
    EamStockReservationDO getReservation(Long reservationId);
    EamStockBalanceDO getOrCreateBalance(EamDemandItemDO item);
    EamStockBalanceDO getOrCreateBalance(Long categoryId, String name, String unit,
                                         Integer managementMode, Integer deliveryMode,
                                         Integer custodyMode, Map<String, Object> extFields,
                                         Map<String, String> extFieldLabels,
                                         Map<String, String> extFieldDictTypes);
    void inbound(Long balanceId, Integer quantity, String businessType, Long businessId, String remark);
    void inboundFrozen(Long balanceId, Integer quantity, String businessType, Long businessId, String remark);
    void outbound(Long balanceId, Integer quantity, String businessType, Long businessId, String remark);
    EamStockBalanceDO getBalance(Long balanceId);
    List<EamStockBalanceDO> getBalanceList();
    void updateMinimum(Long balanceId, Integer minimumQuantity);
    int scanLowStock();
    int scanExpiring(int days);
    int createReminderProjections(int days);
}
