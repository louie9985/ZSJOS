package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import cn.iocoder.yudao.module.zsjos.service.personnel.PersonnelStateService;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_SUBMITTER_IDENTITY_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.*;

@Service
public class LeadSubmissionIdentityService {
    public enum Identity { NEW_MEDIA, NEW_MEDIA_MANAGER, PARTNER, SALES }
    public record Resolution(Identity identity, Long partnerId) {}

    @Resource private AdminUserApi adminUserApi;
    @Resource private PostApi postApi;
    @Resource private DeptApi deptApi;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PersonnelStateService personnelStateService;

    public Resolution requireOrdinarySubmitter(Long userId) {
        AdminUserRespDTO user = requireEnabledUser(userId);
        if (hasPost(user, NEW_MEDIA_OPERATOR)) return new Resolution(Identity.NEW_MEDIA, null);
        if (hasPost(user, DEPT_MANAGER) && deptApi.getDeptListByLeaderUserId(userId).stream()
                .anyMatch(dept -> adminUserApi.getUserListByDeptIds(java.util.List.of(dept.getId())).stream()
                        .anyMatch(candidate -> hasPost(candidate, NEW_MEDIA_OPERATOR)))) {
            return new Resolution(Identity.NEW_MEDIA_MANAGER, null);
        }
        PartnerDO partner = partnerMapper.selectEnabledByUserId(userId);
        if (partner != null) return new Resolution(Identity.PARTNER, partner.getId());
        throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
    }

    public void requireSales(Long userId) {
        if (!hasPost(requireEnabledUser(userId), SALES_SPECIALIST)) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
    }

    public Resolution resolveHistoricalSubmission(Long userId, String sourceType, Long partnerId) {
        requireEnabledUser(userId);
        if (SOURCE_SALES_SELF.equals(sourceType)) {
            return new Resolution(Identity.SALES, null);
        }
        if (SOURCE_PARTNER.equals(sourceType)) {
            PartnerDO partner = partnerId == null ? partnerMapper.selectEnabledByUserId(userId)
                    : partnerMapper.selectById(partnerId);
            if (partner == null || !Objects.equals(partner.getBoundSystemUserId(), userId)) {
                throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
            }
            return new Resolution(Identity.PARTNER, partner.getId());
        }
        return new Resolution(Identity.NEW_MEDIA, null);
    }

    public void requireHistoricalSubmitter(LeadDO lead, Long userId) {
        if (!Objects.equals(lead.getSourceUserId(), userId)) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
        resolveHistoricalSubmission(userId, lead.getSourceType(), lead.getPartnerId());
    }

    private AdminUserRespDTO requireEnabledUser(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
        if (!personnelStateService.isEnabled(userId)) throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        return user;
    }

    private boolean hasPost(AdminUserRespDTO user, String code) {
        PostRespDTO post = postApi.getPostByCode(code);
        return post != null && CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())
                && user.getPostIds() != null && user.getPostIds().contains(post.getId());
    }
}
