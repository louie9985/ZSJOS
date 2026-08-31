package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.bpm.api.task.BpmExternalStartUserProvider;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PartnerBpmStartUserProvider implements BpmExternalStartUserProvider {
    @Resource private PartnerAccountService accountService;
    @Resource private PartnerMapper partnerMapper;

    @Override public Integer getUserType() { return UserTypeEnum.PARTNER.getValue(); }

    @Override
    public String validateAndGetDisplayName(Long accountId) {
        PartnerContext context = accountService.requireContext(accountId);
        PartnerDO partner = partnerMapper.selectById(context.partnerId());
        return partner == null ? null : partner.getName();
    }
}
