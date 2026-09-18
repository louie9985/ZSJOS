package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerAssignmentOptionsRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.userrelation.UserRelationSceneMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadAssignmentConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Partner IDs are interpreted only inside the immutable Partner scene, never as ADMIN users. */
@Service
public class PartnerLeadAssignmentService {
    @Resource private UserRelationSceneMapper sceneMapper;
    @Resource private LeadAssignmentRelationMapper relationMapper;
    @Resource private LeadAssignmentRelationLogMapper logMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private AdminUserApi userApi;
    @Resource private PermissionApi permissionApi;

    public record Target(Long userId, String ownerIdentity) {}

    public PartnerAssignmentOptionsRespVO options(Long partnerId) {
        var scene = sceneMapper.selectByCode(PARTNER_SCENE);
        if (scene == null || !Integer.valueOf(0).equals(scene.getStatus())) {
            return new PartnerAssignmentOptionsRespVO(false, false, null);
        }
        var rows = active(partnerId);
        if (rows.isEmpty()) return new PartnerAssignmentOptionsRespVO(false, false, null);
        if (rows.size() != 1) return new PartnerAssignmentOptionsRespVO(true, false, "指定分配配置异常，请联系管理员");
        var row = rows.getFirst();
        boolean valid = validIdentity(row.getOwnerIdentity()) && eligible(row.getTargetUserId());
        return new PartnerAssignmentOptionsRespVO(true, valid, valid ? null : "指定接单人暂不可用，请选择自动分配或联系管理员");
    }

    public Target resolve(Long partnerId) {
        var scene = sceneMapper.selectByCode(PARTNER_SCENE);
        if (scene == null || !Integer.valueOf(0).equals(scene.getStatus())) throw exception(PARTNER_ASSIGNMENT_NOT_CONFIGURED);
        var rows = active(partnerId);
        if (rows.isEmpty()) throw exception(PARTNER_ASSIGNMENT_NOT_CONFIGURED);
        if (rows.size() != 1) throw exception(PARTNER_ASSIGNMENT_MULTIPLE);
        var row = rows.getFirst();
        validateTarget(row.getTargetUserId(), row.getOwnerIdentity());
        return new Target(row.getTargetUserId(), row.getOwnerIdentity());
    }

    // Review approval uses the frozen target, not the current relationship.
    public void validateTarget(Long userId, String identity) {
        if (!validIdentity(identity)) throw exception(PARTNER_ASSIGNMENT_IDENTITY_INVALID);
        if (!eligible(userId)) throw exception(PARTNER_ASSIGNMENT_UNAVAILABLE);
    }

    private boolean eligible(Long userId) {
        var user = userId == null ? null : userApi.getUser(userId);
        return user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                && permissionApi.hasAnyPermissions(userId, PERMISSION_ACCEPT);
    }

    private boolean validIdentity(String identity) { return OWNER_SALES.equals(identity) || OWNER_EDUCATION.equals(identity); }

    private List<LeadAssignmentRelationDO> active(Long partnerId) {
        return relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(partnerId)).stream()
                .filter(r -> Integer.valueOf(0).equals(r.getStatus())).toList();
    }

    public List<LeadAssignmentUserRespVO> targets() {
        return userApi.getUserList(permissionApi.getEnabledUserIdsByPermission(PERMISSION_ACCEPT)).stream()
                .filter(u -> Integer.valueOf(0).equals(u.getStatus())).map(u -> {
                    var vo = new LeadAssignmentUserRespVO(); vo.setId(u.getId()); vo.setNickname(u.getNickname());
                    vo.setStatus(u.getStatus()); vo.setDeptId(u.getDeptId());
                    vo.setMaskedMobile(DesensitizedUtil.mobilePhone(u.getMobile())); return vo;
                }).toList();
    }

    public PageResult<UserRelationRespVO> page(UserRelationPageReqVO req) {
        var list = partnerMapper.selectList().stream()
                .filter(p -> StrUtil.isBlank(req.getKeyword()) || StrUtil.containsIgnoreCase(p.getName(), req.getKeyword())
                        || StrUtil.contains(p.getPartnerNo(), req.getKeyword()))
                .sorted(Comparator.comparing(PartnerDO::getId).reversed()).map(p -> {
                    var rows = active(p.getId()); var vo = new UserRelationRespVO();
                    vo.setId(p.getId()); vo.setNickname(p.getName()); vo.setSourceType("partner");
                    vo.setMaskedMobile(DesensitizedUtil.mobilePhone(p.getMobile()));
                    vo.setStatus(PARTNER_STATUS_ENABLED.equals(p.getStatus()) ? 0 : 1);
                    vo.setTargetUsers(rows.stream().map(r -> {
                        var u = userApi.getUser(r.getTargetUserId()); var target = new LeadAssignmentUserRespVO();
                        target.setId(r.getTargetUserId()); target.setNickname(u == null ? "已失效人员" : u.getNickname());
                        return target;
                    }).toList());
                    int valid = (int) rows.stream().filter(r -> validIdentity(r.getOwnerIdentity()) && eligible(r.getTargetUserId())).count();
                    vo.setValidTargetCount(valid); vo.setInvalidTargetCount(rows.size() - valid);
                    if (rows.size() == 1) { vo.setOwnerIdentity(rows.getFirst().getOwnerIdentity()); vo.setUpdateTime(rows.getFirst().getUpdateTime()); }
                    return vo;
                }).filter(v -> req.getConfigured() == null || req.getConfigured().equals(!v.getTargetUsers().isEmpty())).toList();
        int from = Math.min((req.getPageNo() - 1) * req.getPageSize(), list.size());
        return new PageResult<>(list.subList(from, Math.min(from + req.getPageSize(), list.size())), (long) list.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(UserRelationSaveReqVO req, Long operatorId) {
        // Lock an existing scene row even for a source with no relations. Locks survive until commit.
        var scene = sceneMapper.lockByCode(PARTNER_SCENE);
        if (scene == null || !Integer.valueOf(0).equals(scene.getStatus())) throw exception(USER_RELATION_SCENE_DISABLED);
        if (!Set.of(MODE_APPEND, MODE_REPLACE, MODE_REMOVE).contains(req.getMode())) throw exception(USER_RELATION_MODE_INVALID);
        Set<Long> requested = req.getTargetUserIds() == null ? Set.of() : req.getTargetUserIds();
        for (Long partnerId : req.getSourceUserIds().stream().sorted().toList()) {
            var partner = partnerMapper.selectById(partnerId);
            if (partner == null || !PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(USER_RELATION_SOURCE_INVALID);
            var existing = relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(partnerId));
            var desired = existing.stream().filter(r -> Integer.valueOf(0).equals(r.getStatus()))
                    .map(LeadAssignmentRelationDO::getTargetUserId).collect(Collectors.toCollection(HashSet::new));
            if (MODE_REPLACE.equals(req.getMode())) { desired.clear(); desired.addAll(requested); }
            else if (MODE_APPEND.equals(req.getMode())) desired.addAll(requested);
            else desired.removeAll(requested);
            if (desired.size() > 1) throw exception(PARTNER_ASSIGNMENT_MULTIPLE);
            if (!MODE_REMOVE.equals(req.getMode())) for (Long id : desired) validateTarget(id, req.getOwnerIdentity());
            for (var row : existing) {
                row.setStatus(desired.contains(row.getTargetUserId()) ? 0 : 1);
                if (row.getStatus() == 0 && !MODE_REMOVE.equals(req.getMode())) row.setOwnerIdentity(req.getOwnerIdentity());
                relationMapper.updateById(row);
            }
            for (Long id : desired) if (existing.stream().noneMatch(r -> id.equals(r.getTargetUserId()))) {
                var row = new LeadAssignmentRelationDO(); row.setScene(PARTNER_SCENE); row.setSourceUserId(partnerId);
                row.setTargetUserId(id); row.setOwnerIdentity(req.getOwnerIdentity()); row.setStatus(0); relationMapper.insert(row);
            }
        }
        var log = new LeadAssignmentRelationLogDO(); log.setScene(PARTNER_SCENE);
        log.setSourceUserIds(join(req.getSourceUserIds())); log.setTargetUserIds(join(requested));
        log.setActionType(req.getMode()); log.setOperatorUserId(operatorId); logMapper.insert(log);
    }

    private String join(Set<Long> ids) { return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")); }
}
