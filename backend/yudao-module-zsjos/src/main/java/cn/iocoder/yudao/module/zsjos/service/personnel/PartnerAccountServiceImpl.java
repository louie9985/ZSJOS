package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PartnerAccountServiceImpl implements PartnerAccountService {

    @Resource private PartnerAccountMapper accountMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public PartnerAccountDO create(Long partnerId, String mobile, String rawPassword) {
        requireMobileAvailable(mobile, null);
        PartnerAccountDO account = new PartnerAccountDO().setPartnerId(partnerId).setMobile(StrUtil.trim(mobile))
                .setPassword(passwordEncoder.encode(rawPassword)).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setVersion(0);
        accountMapper.insert(account);
        return account;
    }

    @Override
    public PartnerAccountDO authenticate(String mobile, String rawPassword, String loginIp) {
        PartnerAccountDO account = accountMapper.selectByMobile(StrUtil.trim(mobile));
        if (account == null || !passwordEncoder.matches(rawPassword, account.getPassword())) {
            throw exception(PARTNER_LOGIN_BAD_CREDENTIALS);
        }
        requireContext(account.getId());
        updateVersioned(new PartnerAccountDO().setId(account.getId())
                .setLastLoginIp(loginIp).setLastLoginTime(LocalDateTime.now()).setVersion(account.getVersion()));
        return account;
    }

    @Override
    public PartnerContext requireContext(Long accountId) {
        PartnerAccountDO account = accountMapper.selectById(accountId);
        if (account == null) throw exception(PARTNER_ACCOUNT_NOT_EXISTS);
        PartnerDO partner = partnerMapper.selectById(account.getPartnerId());
        if (!CommonStatusEnum.ENABLE.getStatus().equals(account.getStatus()) || partner == null
                || !PARTNER_STATUS_ENABLED.equals(partner.getStatus())) {
            throw exception(PARTNER_ACCOUNT_DISABLED);
        }
        return new PartnerContext(account.getId(), partner.getId());
    }

    @Override
    public PartnerAccountDO getByPartnerId(Long partnerId) {
        return accountMapper.selectByPartnerId(partnerId);
    }

    @Override
    public PartnerAccountDO getById(Long accountId) {
        return accountMapper.selectById(accountId);
    }

    @Override
    public String getEnabledMobile(Long accountId) {
        requireContext(accountId);
        PartnerAccountDO account = accountMapper.selectById(accountId);
        return account == null ? null : account.getMobile();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long partnerId, boolean enabled) {
        PartnerAccountDO account = requireByPartnerId(partnerId);
        updateVersioned(new PartnerAccountDO().setId(account.getId())
                .setStatus(enabled ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus())
                .setVersion(account.getVersion()));
        if (!enabled) revoke(account.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMobile(Long partnerId, String mobile) {
        PartnerAccountDO account = requireByPartnerId(partnerId);
        requireMobileAvailable(mobile, account.getId());
        updateVersioned(new PartnerAccountDO().setId(account.getId()).setMobile(StrUtil.trim(mobile))
                .setVersion(account.getVersion()));
        revoke(account.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long partnerId, String newPassword) {
        PartnerAccountDO account = requireByPartnerId(partnerId);
        updateEncodedPassword(account, newPassword);
        revoke(account.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long accountId, String oldPassword, String newPassword) {
        PartnerAccountDO account = accountMapper.selectById(accountId);
        if (account == null) throw exception(PARTNER_ACCOUNT_NOT_EXISTS);
        if (!passwordEncoder.matches(oldPassword, account.getPassword())) throw exception(PARTNER_PASSWORD_MISMATCH);
        updateEncodedPassword(account, newPassword);
        revoke(account.getId());
    }

    private void updateEncodedPassword(PartnerAccountDO account, String rawPassword) {
        updateVersioned(new PartnerAccountDO().setId(account.getId())
                .setPassword(passwordEncoder.encode(rawPassword)).setVersion(account.getVersion()));
    }

    private void updateVersioned(PartnerAccountDO update) {
        if (accountMapper.updateById(update) != 1) {
            throw exception(PARTNER_ACCOUNT_CONCURRENT_MODIFICATION);
        }
    }

    private PartnerAccountDO requireByPartnerId(Long partnerId) {
        PartnerAccountDO account = accountMapper.selectByPartnerId(partnerId);
        if (account == null) throw exception(PARTNER_ACCOUNT_NOT_EXISTS);
        return account;
    }

    private void requireMobileAvailable(String mobile, Long currentId) {
        PartnerAccountDO existing = accountMapper.selectByMobile(StrUtil.trim(mobile));
        if (existing != null && !Objects.equals(existing.getId(), currentId)) throw exception(PARTNER_MOBILE_DUPLICATE);
    }

    private void revoke(Long accountId) {
        oauth2TokenApi.removeAccessToken(accountId, UserTypeEnum.PARTNER.getValue());
    }
}
