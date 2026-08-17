package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileUpdateReqVO;

public interface PartnerProfileService {
    PartnerProfileRespVO get(Long accountId);
    void update(Long accountId, PartnerProfileUpdateReqVO reqVO);
}
