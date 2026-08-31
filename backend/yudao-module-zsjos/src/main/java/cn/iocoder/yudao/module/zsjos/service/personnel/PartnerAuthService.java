package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLoginRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerPermissionInfoRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomLoginReqVO;

public interface PartnerAuthService {
    PartnerLoginRespVO login(PartnerLoginReqVO reqVO, String loginIp);
    PartnerLoginRespVO activate(PartnerActivateReqVO reqVO, String loginIp);
    String getWecomAuthorizeUrl(String redirectUri);
    PartnerLoginRespVO wecomLogin(PartnerWecomLoginReqVO reqVO, String loginIp);
    PartnerLoginRespVO refresh(String refreshToken, String clientId);
    void logout(String accessToken);
    PartnerPermissionInfoRespVO getPermissionInfo(Long accountId);
}
