package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerMeRespVO;
import java.util.List;

public interface PartnerManagementService {
    Long create(PartnerCreateReqVO reqVO);
    List<PartnerRespVO> list();
    void disable(Long id, PartnerStateReqVO reqVO);
    void enable(Long id, PartnerStateReqVO reqVO);
    void convert(Long id, PartnerConvertReqVO reqVO);
    void updateMobile(Long id, PartnerMobileUpdateReqVO reqVO);
    void resetPassword(Long id, PartnerPasswordResetReqVO reqVO);
    PartnerMeRespVO getMe(Long userId);
}
