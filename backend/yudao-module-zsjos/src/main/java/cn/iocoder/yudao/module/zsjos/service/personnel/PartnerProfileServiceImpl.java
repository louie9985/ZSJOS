package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerProfileUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWecomBindReqVO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_WECOM_NOT_BOUND;

@Service
public class PartnerProfileServiceImpl implements PartnerProfileService {
    @Resource private PartnerAccountService accountService;
    @Resource private PartnerMapper partnerMapper;
    @Resource private SocialUserApi socialUserApi;

    @Override
    public PartnerProfileRespVO get(Long accountId) {
        PartnerContext context = accountService.requireContext(accountId);
        PartnerDO partner = partnerMapper.selectById(context.partnerId());
        PartnerAccountDO account = accountService.getByPartnerId(context.partnerId());
        SocialUserRespDTO wecom = socialUserApi.getSocialUserByUserId(UserTypeEnum.PARTNER.getValue(),
                context.accountId(), SocialTypeEnum.WECHAT_ENTERPRISE.getType());
        return new PartnerProfileRespVO().setNickname(partner.getName()).setMobile(account.getMobile())
                .setEmail(partner.getEmail()).setAvatar(partner.getAvatar()).setSex(partner.getSex())
                .setWecomBound(wecom != null).setWecomEnabled(Boolean.TRUE.equals(account.getWecomEnabled()));
    }

    @Override
    public void update(Long accountId, PartnerProfileUpdateReqVO reqVO) {
        PartnerContext context = accountService.requireContext(accountId);
        partnerMapper.updateById(new PartnerDO().setId(context.partnerId()).setName(reqVO.getNickname())
                .setEmail(reqVO.getEmail()).setAvatar(reqVO.getAvatar()).setSex(reqVO.getSex()));
    }

    @Override
    public void bindWecom(Long accountId, PartnerWecomBindReqVO reqVO) {
        accountService.requireContext(accountId);
        socialUserApi.bindSocialUser(new SocialUserBindReqDTO(accountId, UserTypeEnum.PARTNER.getValue(),
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), reqVO.getCode(), reqVO.getState()));
    }

    @Override
    public void updateNotifyChannel(Long accountId, boolean wecomEnabled) {
        accountService.requireContext(accountId);
        if (wecomEnabled) {
            SocialUserRespDTO wecom = socialUserApi.getSocialUserByUserId(UserTypeEnum.PARTNER.getValue(),
                    accountId, SocialTypeEnum.WECHAT_ENTERPRISE.getType());
            if (wecom == null) {
                throw exception(PARTNER_WECOM_NOT_BOUND);
            }
        }
        accountService.updateWecomEnabled(accountId, wecomEnabled);
    }
}
