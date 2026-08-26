package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOwnershipLogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOwnershipUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PartnerOwnershipService {
    public static final String QUERY_PERMISSION = "zsjos:subordinate-partner:query";

    @Resource private PartnerOwnershipMapper ownershipMapper;
    @Resource private PartnerOwnershipLogMapper logMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    public PartnerOwnershipDO getByPartnerId(Long partnerId) {
        return ownershipMapper.selectByPartnerId(partnerId);
    }

    public List<PartnerOwnershipDO> getByEmployeeUserId(Long employeeUserId) {
        if (!canQuery(employeeUserId)) return List.of();
        return ownershipMapper.selectByEmployeeUserId(employeeUserId);
    }

    public boolean canQuery(Long employeeUserId) {
        if (!permissionApi.hasAnyPermissions(employeeUserId, QUERY_PERMISSION)) return false;
        AdminUserRespDTO user = adminUserApi.getUser(employeeUserId);
        return user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus());
    }

    public boolean canRead(Long employeeUserId, Long partnerId) {
        PartnerOwnershipDO ownership = getByPartnerId(partnerId);
        return ownership != null && Objects.equals(ownership.getEmployeeUserId(), employeeUserId)
                && permissionApi.hasAnyPermissions(employeeUserId, QUERY_PERMISSION)
                && isEnabledUser(employeeUserId);
    }

    public void checkRead(Long employeeUserId, Long partnerId) {
        if (!canRead(employeeUserId, partnerId)) throw exception(PARTNER_OWNERSHIP_PERMISSION_DENIED);
    }

    public List<LeadAssignmentUserRespVO> getCandidates() {
        return adminUserApi.getUserList(permissionApi.getEnabledUserIdsByPermission(QUERY_PERMISSION)).stream()
                .map(user -> new LeadAssignmentUserRespVO().setId(user.getId()).setNickname(user.getNickname())
                        .setDeptId(user.getDeptId()).setStatus(user.getStatus())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long partnerId, PartnerOwnershipUpdateReqVO reqVO, Long operatorUserId) {
        if (partnerMapper.selectById(partnerId) == null) throw exception(PARTNER_NOT_EXISTS);
        PartnerOwnershipDO existing = ownershipMapper.selectByPartnerId(partnerId);
        Long previousUserId = existing == null ? null : existing.getEmployeeUserId();
        String previousUserName = existing == null ? null : existing.getEmployeeNameSnapshot();
        if (existing != null && !Objects.equals(existing.getVersion(), reqVO.getExpectedVersion())) {
            throw exception(PARTNER_OWNERSHIP_VERSION_CONFLICT);
        }
        AdminUserRespDTO target = null;
        if (reqVO.getAssignedUserId() != null) {
            target = adminUserApi.getUser(reqVO.getAssignedUserId());
            if (target == null || !CommonStatusEnum.ENABLE.getStatus().equals(target.getStatus())
                    || !permissionApi.hasAnyPermissions(target.getId(), QUERY_PERMISSION)) {
                throw exception(PARTNER_OWNERSHIP_TARGET_INVALID);
            }
        }
        if (existing == null && target != null) {
            existing = new PartnerOwnershipDO().setPartnerId(partnerId).setEmployeeUserId(target.getId())
                    .setEmployeeNameSnapshot(target.getNickname()).setAssignedAt(LocalDateTime.now()).setVersion(0);
            ownershipMapper.insert(existing);
        } else if (existing != null && target == null) {
            if (ownershipMapper.deleteByIdAndVersion(existing.getId(), existing.getVersion(),
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()) != 1) {
                throw exception(PARTNER_OWNERSHIP_VERSION_CONFLICT);
            }
        } else if (existing != null && !Objects.equals(existing.getEmployeeUserId(), target.getId())) {
            PartnerOwnershipDO update = new PartnerOwnershipDO().setId(existing.getId()).setVersion(existing.getVersion())
                    .setEmployeeUserId(target.getId()).setEmployeeNameSnapshot(target.getNickname())
                    .setAssignedAt(LocalDateTime.now());
            if (ownershipMapper.updateById(update) != 1) throw exception(PARTNER_OWNERSHIP_VERSION_CONFLICT);
        } else if (existing != null) {
            return;
        } else {
            return;
        }
        PartnerOwnershipLogDO log = new PartnerOwnershipLogDO().setPartnerId(partnerId)
                .setPreviousEmployeeUserId(previousUserId)
                .setPreviousEmployeeNameSnapshot(previousUserName)
                .setEmployeeUserId(target == null ? null : target.getId())
                .setEmployeeNameSnapshot(target == null ? null : target.getNickname())
                .setActionType(previousUserId == null ? "assign" : target == null ? "unassign" : "reassign")
                .setReason(reqVO.getReason().trim()).setOperatorUserId(operatorUserId).setOccurredAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    public PageResult<PartnerOwnershipLogRespVO> getLogPage(Long partnerId, int pageNo, int pageSize) {
        PageResult<PartnerOwnershipLogDO> page = logMapper.selectPageByPartnerId(partnerId, pageNo, pageSize);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(page.getList().stream()
                .map(PartnerOwnershipLogDO::getOperatorUserId).distinct().toList());
        return new PageResult<>(page.getList().stream().map(log -> {
            PartnerOwnershipLogRespVO result = BeanUtils.toBean(log, PartnerOwnershipLogRespVO.class);
            result.setPreviousEmployeeName(log.getPreviousEmployeeNameSnapshot());
            result.setEmployeeName(log.getEmployeeNameSnapshot());
            AdminUserRespDTO operator = users.get(log.getOperatorUserId());
            result.setOperatorName(operator == null ? null : operator.getNickname());
            return result;
        }).toList(), page.getTotal());
    }

    private boolean isEnabledUser(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus());
    }
}
