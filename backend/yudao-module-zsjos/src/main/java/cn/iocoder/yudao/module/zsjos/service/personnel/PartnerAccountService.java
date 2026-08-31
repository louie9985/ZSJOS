package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;

public interface PartnerAccountService {

    PartnerAccountDO create(Long partnerId, String mobile, String rawPassword);

    PartnerAccountDO authenticate(String mobile, String rawPassword, String loginIp);

    void recordLogin(Long accountId, String loginIp);

    PartnerContext requireContext(Long accountId);

    PartnerAccountDO getByPartnerId(Long partnerId);

    PartnerAccountDO getById(Long accountId);

    PartnerAccountDO getByMobile(String mobile);

    String getEnabledMobile(Long accountId);

    void setEnabled(Long partnerId, boolean enabled);

    void updateMobile(Long partnerId, String mobile);

    void resetPassword(Long partnerId, String newPassword);

    void updatePassword(Long accountId, String oldPassword, String newPassword);

    void updateWecomEnabled(Long accountId, boolean enabled);
}
