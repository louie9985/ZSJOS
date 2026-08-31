package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferInspectReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import jakarta.validation.Valid;
import java.util.List;

/**
 * EAM 资产流转 Service 接口
 */
public interface EamTransferService {

    /**
     * 创建流转单
     *
     * 领用 / 借用 / 调拨进入 BPM；退还 / 归还进入待验收。
     *
     * @return 单据编号
     */
    Long createTransfer(@Valid EamTransferCreateReqVO reqVO);

    /**
     * 审批通过：应用资产状态与归属变更
     */
    void handleProcessResult(Long id, Integer bpmStatus, String reason);

    /**
     * 取消流转单（仅审批中可取消）
     */
    void cancelTransfer(Long id, Long userId);

    void inspectTransfer(Long id, EamTransferInspectReqVO reqVO, Long inspectorUserId);

    EamTransferDO getTransfer(Long id);
    EamTransferDO getTransfer(Long id, Long userId);

    PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO);
    PageResult<EamTransferDO> getTransferPage(EamTransferPageReqVO reqVO, Long userId);

    List<EamTransferDO> getMyTransfers(Long userId);

}
