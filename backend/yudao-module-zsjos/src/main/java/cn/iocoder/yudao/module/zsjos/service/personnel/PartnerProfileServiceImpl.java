package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class PartnerProfileServiceImpl implements PartnerProfileService {
    @Resource private PartnerAccountService accountService;
    @Resource private PartnerMapper partnerMapper;

    @Override
    public PartnerProfileRespVO get(Long accountId) {
        PartnerContext context = accountService.requireContext(accountId);
        PartnerDO partner = partnerMapper.selectById(context.partnerId());
        PartnerAccountDO account = accountService.getByPartnerId(context.partnerId());
        return new PartnerProfileRespVO().setNickname(partner.getName()).setMobile(account.getMobile())
                .setEmail(partner.getEmail()).setAvatar(partner.getAvatar()).setSex(partner.getSex());
    }

    @Override
    public void update(Long accountId, PartnerProfileUpdateReqVO reqVO) {
        PartnerContext context = accountService.requireContext(accountId);
        partnerMapper.updateById(new PartnerDO().setId(context.partnerId()).setName(reqVO.getNickname())
                .setEmail(reqVO.getEmail()).setAvatar(reqVO.getAvatar()).setSex(reqVO.getSex()));
    }
}
