package cn.iocoder.yudao.module.eam.service.repair;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairFinishReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.repair.EamRepairDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * EAM 维修记录 Service 接口
 */
public interface EamRepairService {

    /**
     * 送修：创建维修记录并把资产置为「维修中」
     */
    Long createRepair(@Valid EamRepairCreateReqVO reqVO);

    /**
     * 维修完成：填写完成时间并把资产恢复到送修前状态
     */
    void finishRepair(@Valid EamRepairFinishReqVO reqVO);

    void deleteRepair(Long id);

    EamRepairDO getRepair(Long id);

    List<EamRepairDO> getRepairListByAssetId(Long assetId);

    PageResult<EamRepairDO> getRepairPage(EamRepairPageReqVO reqVO);

}
