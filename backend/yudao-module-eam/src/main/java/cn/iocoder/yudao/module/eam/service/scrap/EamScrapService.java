package cn.iocoder.yudao.module.eam.service.scrap;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.scrap.vo.EamScrapPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.scrap.EamScrapDO;
import jakarta.validation.Valid;

/**
 * EAM 报废 Service 接口
 */
public interface EamScrapService {

    /**
     * 申请报废：创建报废单并把资产置为「待报废」
     */
    Long createScrap(@Valid EamScrapCreateReqVO reqVO);

    /**
     * 审批通过：资产进入终态「已报废」
     */
    void approveScrap(Long id);

    /**
     * 审批驳回：资产恢复到申请前状态
     */
    void rejectScrap(Long id, String reason);

    EamScrapDO getScrap(Long id);

    PageResult<EamScrapDO> getScrapPage(EamScrapPageReqVO reqVO);

}
