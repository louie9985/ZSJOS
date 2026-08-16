package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import jakarta.validation.Valid;

/**
 * EAM 资产流转 Service 接口
 */
public interface EamTransferService {

    /**
     * 创建流转单
     *
     * 领用 / 借用 / 调拨 需审批，创建后进入审批中；退还 / 归还 直接生效。
     *
     * @return 单据编号
     */
    Long createTransfer(@Valid EamTransferCreateReqVO reqVO);

    /**
     * 审批通过：应用资产状态与归属变更
     */
    void approveTransfer(Long id);

    /**
     * 审批驳回
     */
    void rejectTransfer(Long id, String reason);

    /**
     * 取消流转单（仅审批中可取消）
     */
    void cancelTransfer(Long id);

    EamTransferDO getTransfer(Long id);

    PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO);

}
