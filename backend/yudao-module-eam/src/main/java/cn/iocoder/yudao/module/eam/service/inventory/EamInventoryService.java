package cn.iocoder.yudao.module.eam.service.inventory;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCheckReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDetailDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * EAM 盘点 Service 接口
 */
public interface EamInventoryService {

    /**
     * 创建盘点单，并按范围快照生成明细
     *
     * 明细在创建时固化账面归属，后续台账变动不影响本次盘点的比对基准。
     */
    Long createInventory(@Valid EamInventoryCreateReqVO reqVO);

    /**
     * 录入一条盘点结果
     */
    void checkDetail(@Valid EamInventoryCheckReqVO reqVO);

    /**
     * 完成盘点
     */
    void finishInventory(Long id);

    void deleteInventory(Long id);

    EamInventoryDO getInventory(Long id);

    PageResult<EamInventoryDO> getInventoryPage(EamInventoryPageReqVO reqVO);

    List<EamInventoryDetailDO> getDetailListByInventoryId(Long inventoryId);

    /**
     * 把「位置不符」的实盘归属同步回资产台账
     */
    void syncDetailToAsset(Long detailId);

    /**
     * 把「未找到」的资产标记为已丢失
     */
    void markDetailAssetLost(Long detailId);

}
