package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerInvitationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerInvitationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.NEW_MEDIA_OPERATOR;

@Service
public class PartnerInvitationServiceImpl implements PartnerInvitationService {

    private static final int EXPIRE_DAYS = 7;
    private static final int CODE_RETRY_LIMIT = 20;
    private static final DateTimeFormatter PARTNER_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource private PartnerInvitationMapper invitationMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PartnerOwnershipMapper ownershipMapper;
    @Resource private PartnerOwnershipLogMapper ownershipLogMapper;
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartnerInvitationRespVO create(PartnerInvitationCreateReqVO reqVO, Long operatorUserId) {
        String mobile = StrUtil.trim(reqVO.getMobile());
        if (partnerAccountService.getByMobile(mobile) != null) {
            throw exception(PARTNER_MOBILE_DUPLICATE);
        }
        AdminUserRespDTO operator = requireNewMediaOperator(reqVO.getAssignedOperatorUserId());
        LocalDateTime now = LocalDateTime.now();
        invitationMapper.voidActiveByMobile(mobile, now);
        PartnerInvitationDO invitation = new PartnerInvitationDO()
                .setInviteCode(generateInviteCode())
                .setName(StrUtil.trim(reqVO.getName()))
                .setMobile(mobile)
                .setAssignedOperatorUserId(operator.getId())
                .setAssignedOperatorNameSnapshot(operator.getNickname())
                .setStatus(PARTNER_INVITATION_STATUS_ACTIVE)
                .setExpiresAt(now.plusDays(EXPIRE_DAYS))
                .setCreatedByUserId(operatorUserId)
                .setVersion(0);
        try {
            invitationMapper.insert(invitation);
        } catch (DuplicateKeyException duplicate) {
            throw exception(PARTNER_INVITATION_CODE_CONFLICT);
        }
        return toResp(invitation, Map.of());
    }

    @Override
    public PageResult<PartnerInvitationRespVO> getPage(PartnerInvitationPageReqVO reqVO) {
        reqVO.setKeyword(StrUtil.trimToNull(reqVO.getKeyword()));
        PageResult<PartnerInvitationDO> page = invitationMapper.selectPage(reqVO);
        Map<Long, AdminUserRespDTO> creators = getUserMap(page.getList().stream()
                .map(PartnerInvitationDO::getCreatedByUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return new PageResult<>(page.getList().stream().map(invitation -> toResp(invitation, creators)).toList(),
                page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInvitation(Long id, Long operatorUserId) {
        PartnerInvitationDO invitation = invitationMapper.selectById(id);
        if (invitation == null) {
            throw exception(PARTNER_INVITATION_NOT_EXISTS);
        }
        if (!PARTNER_INVITATION_STATUS_ACTIVE.equals(invitation.getStatus())) {
            return;
        }
        invitation.setStatus(PARTNER_INVITATION_STATUS_VOIDED);
        invitation.setVoidedAt(LocalDateTime.now());
        if (invitationMapper.updateById(invitation) != 1) {
            throw exception(PARTNER_INVITATION_VERSION_CONFLICT);
        }
    }

    @Override
    public PageResult<LeadAssignmentUserRespVO> getOperatorCandidatePage(String keyword, Integer pageNo,
                                                                         Integer pageSize) {
        RoleRespDTO role = requireNewMediaOperatorRole();
        String normalizedKeyword = StrUtil.trimToNull(keyword);
        java.util.List<AdminUserRespDTO> users = adminUserApi.getUserList(
                permissionApi.getUserRoleIdListByRoleIds(Set.of(role.getId()))).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .filter(user -> normalizedKeyword == null
                        || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(user.getNickname()), normalizedKeyword)
                        || StrUtil.contains(StrUtil.nullToEmpty(user.getMobile()), normalizedKeyword))
                .sorted(java.util.Comparator.comparing(AdminUserRespDTO::getId))
                .toList();
        int currentPageNo = Math.max(1, pageNo == null ? 1 : pageNo);
        int currentPageSize = Math.min(100, Math.max(1, pageSize == null ? 20 : pageSize));
        int from = Math.min((currentPageNo - 1) * currentPageSize, users.size());
        int to = Math.min(from + currentPageSize, users.size());
        return new PageResult<>(users.subList(from, to).stream().map(user -> new LeadAssignmentUserRespVO()
                .setId(user.getId()).setNickname(user.getNickname()).setMaskedMobile(user.getMobile())
                .setDeptId(user.getDeptId()).setStatus(user.getStatus())).toList(), (long) users.size());
    }

    @Override
    public boolean hasActiveInvitation(String mobile) {
        return invitationMapper.hasActiveByMobile(StrUtil.trim(mobile), LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartnerAccountDO activate(PartnerActivateReqVO reqVO) {
        if (!Objects.equals(reqVO.getPassword(), reqVO.getConfirmPassword())) {
            throw exception(PARTNER_INVITATION_PASSWORD_CONFIRM_MISMATCH);
        }
        String mobile = StrUtil.trim(reqVO.getMobile());
        String inviteCode = StrUtil.trim(reqVO.getInviteCode()).toUpperCase();
        if (partnerAccountService.getByMobile(mobile) != null) {
            throw exception(PARTNER_MOBILE_DUPLICATE);
        }
        PartnerInvitationDO invitation = invitationMapper.selectActiveByMobileAndCodeForUpdate(mobile, inviteCode);
        if (invitation == null) {
            throwInvitationState(mobile, inviteCode);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!invitation.getExpiresAt().isAfter(now)) {
            invitation.setStatus(PARTNER_INVITATION_STATUS_EXPIRED);
            invitation.setVoidedAt(now);
            invitationMapper.updateById(invitation);
            throw exception(PARTNER_INVITATION_EXPIRED);
        }
        AdminUserRespDTO operator = requireNewMediaOperator(invitation.getAssignedOperatorUserId());
        PartnerDO partner = new PartnerDO()
                .setPartnerNo(generatePartnerNo())
                .setName(invitation.getName())
                .setMobile(mobile)
                .setStatus(PARTNER_STATUS_ENABLED)
                .setBoundSystemUserId(null)
                .setEnabledAt(now)
                .setVersion(0);
        partnerMapper.insert(partner);
        PartnerAccountDO account = partnerAccountService.create(partner.getId(), mobile, reqVO.getPassword());
        assignOwnership(partner.getId(), operator, invitation.getCreatedByUserId(), now);
        invitation.setStatus(PARTNER_INVITATION_STATUS_USED);
        invitation.setUsedAt(now);
        invitation.setPartnerId(partner.getId());
        if (invitationMapper.updateById(invitation) != 1) {
            throw exception(PARTNER_INVITATION_VERSION_CONFLICT);
        }
        return account;
    }

    private void throwInvitationState(String mobile, String inviteCode) {
        PartnerInvitationDO latest = invitationMapper.selectLatestByMobileAndCodeForUpdate(mobile, inviteCode);
        if (latest == null) {
            throw exception(PARTNER_INVITATION_NOT_EXISTS);
        }
        if (PARTNER_INVITATION_STATUS_USED.equals(latest.getStatus())) {
            throw exception(PARTNER_INVITATION_USED);
        }
        if (PARTNER_INVITATION_STATUS_VOIDED.equals(latest.getStatus())) {
            throw exception(PARTNER_INVITATION_VOIDED);
        }
        if (PARTNER_INVITATION_STATUS_EXPIRED.equals(latest.getStatus())
                || !latest.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw exception(PARTNER_INVITATION_EXPIRED);
        }
        throw exception(PARTNER_INVITATION_NOT_EXISTS);
    }

    private AdminUserRespDTO requireNewMediaOperator(Long userId) {
        RoleRespDTO role = requireNewMediaOperatorRole();
        Set<Long> userIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(role.getId()));
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !userIds.contains(userId)) {
            throw exception(PARTNER_INVITATION_OPERATOR_INVALID);
        }
        return user;
    }

    private RoleRespDTO requireNewMediaOperatorRole() {
        RoleRespDTO role = roleApi.getRoleByCode(NEW_MEDIA_OPERATOR);
        if (role == null || !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())) {
            throw exception(PARTNER_INVITATION_OPERATOR_INVALID);
        }
        return role;
    }

    private String generateInviteCode() {
        for (int i = 0; i < CODE_RETRY_LIMIT; i++) {
            String code = randomLetters(4) + randomDigits(4);
            if (invitationMapper.selectByInviteCode(code) == null) {
                return code;
            }
        }
        throw exception(PARTNER_INVITATION_CODE_CONFLICT);
    }

    private String generatePartnerNo() {
        for (int i = 0; i < CODE_RETRY_LIMIT; i++) {
            String no = "PT" + LocalDateTime.now().format(PARTNER_NO_TIME_FORMAT) + randomDigits(4);
            if (partnerMapper.selectByPartnerNo(no) == null) {
                return no;
            }
        }
        throw exception(PARTNER_STATE_INVALID);
    }

    private String randomLetters(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append((char) ('A' + RANDOM.nextInt(26)));
        }
        return result.toString();
    }

    private String randomDigits(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(RANDOM.nextInt(10));
        }
        return result.toString();
    }

    private void assignOwnership(Long partnerId, AdminUserRespDTO operator, Long operatorUserId, LocalDateTime now) {
        PartnerOwnershipDO ownership = new PartnerOwnershipDO()
                .setPartnerId(partnerId)
                .setEmployeeUserId(operator.getId())
                .setEmployeeNameSnapshot(operator.getNickname())
                .setAssignedAt(now)
                .setVersion(0);
        ownershipMapper.insert(ownership);
        PartnerOwnershipLogDO log = new PartnerOwnershipLogDO()
                .setPartnerId(partnerId)
                .setEmployeeUserId(operator.getId())
                .setEmployeeNameSnapshot(operator.getNickname())
                .setActionType("assign")
                .setReason("邀请码激活绑定归属运营")
                .setOperatorUserId(operatorUserId)
                .setOccurredAt(now);
        ownershipLogMapper.insert(log);
    }

    private PartnerInvitationRespVO toResp(PartnerInvitationDO invitation, Map<Long, AdminUserRespDTO> creators) {
        PartnerInvitationRespVO resp = BeanUtils.toBean(invitation, PartnerInvitationRespVO.class);
        resp.setAssignedOperatorName(invitation.getAssignedOperatorNameSnapshot());
        AdminUserRespDTO creator = creators.get(invitation.getCreatedByUserId());
        resp.setCreatedByName(creator == null ? null : creator.getNickname());
        return resp;
    }

    private Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return adminUserApi.getUserMap(ids);
    }
}
