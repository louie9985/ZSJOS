package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import cn.iocoder.yudao.module.zsjos.service.personnel.PersonnelStateService;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
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
        AdminUserRespDTO user = requireEnabledAccount(userId);
        if (isValidInternalUser(user) && hasPost(user, NEW_MEDIA_OPERATOR)) {
            return new Resolution(Identity.NEW_MEDIA, null);
        }
        if (isValidInternalUser(user) && hasPost(user, DEPT_MANAGER) && hasManagedNewMediaEmployee(userId)) {
            return new Resolution(Identity.NEW_MEDIA_MANAGER, null);
        }
        PartnerDO partner = partnerMapper.selectEnabledByUserId(userId);
        if (partner != null) return new Resolution(Identity.PARTNER, partner.getId());
        throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
    }

    public void requireSales(Long userId) {
        AdminUserRespDTO user = requireEnabledAccount(userId);
        if (!isValidInternalUser(user) || !hasPost(user, SALES_SPECIALIST)) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
    }

    public List<AdminUserRespDTO> getEnabledNewMediaProviders() {
        List<AdminUserRespDTO> users = adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> disabledPersonnel = personnelStateService.getDisabledUserIds(
                users.stream().map(AdminUserRespDTO::getId).toList());
        Set<Long> departmentIds = users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> departments = (departmentIds.isEmpty() ? List.<DeptRespDTO>of()
                : deptApi.getDeptList(departmentIds)).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, Function.identity()));
        PostRespDTO newMediaPost = postApi.getPostByCode(NEW_MEDIA_OPERATOR);
        PostRespDTO managerPost = postApi.getPostByCode(DEPT_MANAGER);
        List<AdminUserRespDTO> validNewMediaEmployees = users.stream()
                .filter(user -> isValidInternalUser(user, departments, disabledPersonnel))
                .filter(user -> hasPost(user, newMediaPost))
                .toList();
        Set<Long> managerIds = validNewMediaEmployees.stream()
                .map(AdminUserRespDTO::getDeptId).map(departments::get).filter(Objects::nonNull)
                .map(DeptRespDTO::getLeaderUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return users.stream()
                .filter(user -> isValidInternalUser(user, departments, disabledPersonnel))
                .filter(user -> hasPost(user, newMediaPost)
                        || managerIds.contains(user.getId()) && hasPost(user, managerPost))
                .sorted(java.util.Comparator.comparing(AdminUserRespDTO::getId))
                .toList();
    }

    public void requireNewMediaProvider(Long userId) {
        if (userId == null) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
        AdminUserRespDTO user = requireEnabledAccount(userId);
        if (!isValidInternalUser(user)
                || !hasPost(user, NEW_MEDIA_OPERATOR)
                && !(hasPost(user, DEPT_MANAGER) && hasManagedNewMediaEmployee(userId))) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
    }

    public Resolution resolveHistoricalSubmission(Long userId, String sourceType, Long partnerId) {
        requireEnabledAccount(userId);
        if (SOURCE_SALES_SELF.equals(sourceType)) {
            return new Resolution(Identity.SALES, null);
        }
        if (SOURCE_PARTNER.equals(sourceType)) {
            PartnerDO partner = partnerId == null ? partnerMapper.selectEnabledByUserId(userId)
                    : partnerMapper.selectById(partnerId);
            if (partner == null || !PARTNER_STATUS_ENABLED.equals(partner.getStatus())
                    || partner.getEnabledAt() == null || partner.getDisabledAt() != null
                    || !Objects.equals(partner.getBoundSystemUserId(), userId)) {
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

    private AdminUserRespDTO requireEnabledAccount(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(LEAD_SUBMITTER_IDENTITY_INVALID);
        }
        return user;
    }

    private boolean isValidInternalUser(AdminUserRespDTO user) {
        if (user.getDeptId() == null || deptApi.getDept(user.getDeptId()) == null
                || !CommonStatusEnum.ENABLE.getStatus().equals(deptApi.getDept(user.getDeptId()).getStatus())) {
            return false;
        }
        return personnelStateService.isEnabled(user.getId());
    }

    private boolean isValidInternalUser(AdminUserRespDTO user, Map<Long, DeptRespDTO> departments,
                                        Set<Long> disabledPersonnel) {
        DeptRespDTO department = user.getDeptId() == null ? null : departments.get(user.getDeptId());
        return CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                && department != null && CommonStatusEnum.ENABLE.getStatus().equals(department.getStatus())
                && !disabledPersonnel.contains(user.getId());
    }

    private boolean hasManagedNewMediaEmployee(Long managerUserId) {
        List<DeptRespDTO> managedDepartments = deptApi.getDeptListByLeaderUserId(managerUserId).stream()
                .filter(dept -> CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())).toList();
        if (managedDepartments.isEmpty()) {
            return false;
        }
        List<AdminUserRespDTO> users = adminUserApi.getUserListByDeptIds(
                managedDepartments.stream().map(DeptRespDTO::getId).toList());
        Set<Long> disabledPersonnel = personnelStateService.getDisabledUserIds(
                users.stream().map(AdminUserRespDTO::getId).toList());
        Set<Long> enabledDepartmentIds = managedDepartments.stream().map(DeptRespDTO::getId).collect(Collectors.toSet());
        PostRespDTO newMediaPost = postApi.getPostByCode(NEW_MEDIA_OPERATOR);
        return users.stream().anyMatch(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                && enabledDepartmentIds.contains(user.getDeptId()) && !disabledPersonnel.contains(user.getId())
                && hasPost(user, newMediaPost));
    }

    private boolean hasPost(AdminUserRespDTO user, String code) {
        return hasPost(user, postApi.getPostByCode(code));
    }

    private boolean hasPost(AdminUserRespDTO user, PostRespDTO post) {
        return post != null && CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())
                && user.getPostIds() != null && user.getPostIds().contains(post.getId());
    }
}
