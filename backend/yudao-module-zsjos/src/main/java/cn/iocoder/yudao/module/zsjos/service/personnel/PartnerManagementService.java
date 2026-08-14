package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.*;
import java.util.List;

public interface PartnerManagementService {
    Long create(PartnerCreateReqVO reqVO);
    List<PartnerRespVO> list();
    void disable(Long id, PartnerStateReqVO reqVO);
    void enable(Long id, PartnerStateReqVO reqVO);
    void convert(Long id, PartnerConvertReqVO reqVO);
}
