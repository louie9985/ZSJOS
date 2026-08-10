package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;

@Component
public class LeadNotifySceneProvider implements NotifySceneProvider {

    private static final String QUERY_ALL_PERMISSION = "zsjos:lead:query-all";
    private static final List<String> ACTIONS = List.of(NotifyActionType.NONE,
            NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL);

    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper productMapper;
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private DeptApi deptApi;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(
                scene(CREATED, "客资新建", ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(ACTIVATED, "重复客资激活", ROLE_SUBMITTER, ROLE_OWNER, ROLE_OPERATOR),
                scene(ASSIGNED, "首次派单", ROLE_PENDING_SALES, ROLE_SUBMITTER),
                scene(REASSIGNED, "重新派单", ROLE_PENDING_SALES, ROLE_SUBMITTER),
                scene(ACCEPTED, "接单成功", ROLE_OWNER, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(REJECTED, "拒绝接单", ROLE_PENDING_SALES, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(EXPIRED, "接单超时", ROLE_PENDING_SALES, ROLE_SUBMITTER),
                scene(PUBLIC_POOL, "进入抢单池", ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(CLAIMED, "抢单成功", ROLE_OWNER, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(TRANSFERRED, "管理员转派", ROLE_PREVIOUS_OWNER, ROLE_NEW_OWNER, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(FOLLOW_UP_RECORDED, "新增跟进", ROLE_OWNER, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(CATEGORY_CHANGED, "客资分类变化", ROLE_OWNER, ROLE_SUBMITTER, ROLE_OPERATOR),
                scene(QUALIFICATION_SUSPENDED, "客资判定超时挂起", ROLE_OWNER, ROLE_QUALIFICATION_MANAGERS),
                scene(QUALIFICATION_RESTORED, "挂起客资恢复", ROLE_OWNER, ROLE_OPERATOR, ROLE_QUALIFICATION_MANAGERS),
                scene(QUALIFICATION_TRANSFERRED, "异常客资转派", ROLE_PREVIOUS_OWNER, ROLE_NEW_OWNER,
                        ROLE_OPERATOR, ROLE_QUALIFICATION_MANAGERS),
                scene(QUALIFICATION_RECYCLED, "挂起客资回收", ROLE_PREVIOUS_OWNER, ROLE_OPERATOR,
                        ROLE_QUALIFICATION_MANAGERS),
                scene(QUALIFICATION_RELEASED, "异常客资释放到抢单池", ROLE_PREVIOUS_OWNER, ROLE_OPERATOR,
                        ROLE_QUALIFICATION_MANAGERS),
                scene(APPEAL_SUBMITTED, "客资申诉待处理", ROLE_APPEAL_REVIEWERS),
                scene(APPEAL_OVERTURNED, "客资申诉改判有效", ROLE_SUBMITTER),
                scene(APPEAL_UPHELD, "客资申诉维持无效", ROLE_SUBMITTER));
    }

    @Override
    public Set<Long> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles) {
        Set<Long> users = new LinkedHashSet<>();
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        for (String role : recipientRoles) {
            if (ROLE_QUALIFICATION_MANAGERS.equals(role)) {
                users.addAll(resolveQualificationManagers(payload));
                continue;
            }
            if (ROLE_APPEAL_REVIEWERS.equals(role)) {
                users.addAll(longValues(payload.get("appeal.reviewerUserIds")));
                continue;
            }
            Long id = switch (role) {
                case ROLE_SUBMITTER -> longValue(payload.get("submitterUserId"));
                case ROLE_PENDING_SALES -> longValue(payload.get("pendingSalesUserId"));
                case ROLE_OWNER -> longValue(payload.get("ownerUserId"));
                case ROLE_OPERATOR -> event.getOperatorUserId();
                case ROLE_PREVIOUS_OWNER -> longValue(payload.get("previousOwnerUserId"));
                case ROLE_NEW_OWNER -> longValue(payload.get("newOwnerUserId"));
                default -> null;
            };
            if (id != null && id > 0) users.add(id);
        }
        return users;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, Long recipientUserId) {
        LeadDO lead = leadMapper.selectById(event.getBizId());
        if (lead == null) return Map.of();
        boolean fullContact = canReadFullContact(lead, recipientUserId);
        List<LeadIntendedProductDO> products = productMapper.selectListByLeadId(lead.getId());
        List<LeadAttachmentDO> attachments = attachmentMapper.selectListByLeadId(lead.getId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("lead.id", lead.getId());
        values.put("lead.name", fullContact ? lead.getSubmittedName() : DesensitizedUtil.chineseName(lead.getSubmittedName()));
        values.put("lead.mobile", fullContact ? lead.getSubmittedMobile() : DesensitizedUtil.mobilePhone(lead.getSubmittedMobile()));
        values.put("lead.wechatId", fullContact ? lead.getSubmittedWechatId() : maskWechat(lead.getSubmittedWechatId()));
        values.put("lead.sourceType", lead.getSourceType());
        values.put("lead.sourceChannel", dictLabel(DICT_SOURCE_CHANNEL, lead.getSourceChannelId()));
        values.put("lead.province", lead.getProvinceName());
        values.put("lead.city", lead.getCityName());
        values.put("lead.category", dictLabel(DICT_CATEGORY, lead.getLeadCategory()));
        values.put("lead.remark", lead.getRemark());
        values.put("lead.status", lead.getStatus());
        values.put("lead.assignmentStatus", lead.getAssignmentStatus());
        values.put("lead.dispatchMode", lead.getDispatchMode());
        values.put("lead.submittedAt", lead.getSubmittedAt());
        values.put("lead.pendingExpiresAt", lead.getPendingExpiresAt());
        values.put("lead.publicPoolAt", lead.getPublicPoolAt());
        values.put("lead.lastFollowUpAt", lead.getLastFollowUpAt());
        values.put("lead.nextFollowUpAt", lead.getNextFollowUpAt());
        values.put("lead.followUpCount", lead.getFollowUpCount());
        values.put("lead.qualificationDeadlineAt", lead.getQualificationDeadlineAt());
        values.put("lead.suspendedAt", lead.getSuspendedAt());
        values.put("product.names", products.stream().sorted(Comparator.comparing(LeadIntendedProductDO::getSort))
                .map(LeadIntendedProductDO::getProductNameSnapshot).filter(Objects::nonNull)
                .collect(Collectors.joining("、")));
        values.put("product.primaryName", products.stream().filter(p -> Boolean.TRUE.equals(p.getIsPrimary()))
                .map(LeadIntendedProductDO::getProductNameSnapshot).findFirst().orElse(""));
        values.put("product.count", products.size());
        values.put("attachment.names", attachments.stream().sorted(Comparator.comparing(LeadAttachmentDO::getSort))
                .map(LeadAttachmentDO::getOriginalName).filter(Objects::nonNull).collect(Collectors.joining("、")));
        values.put("attachment.count", attachments.size());
        putUser(values, "submitter", lead.getSourceUserId());
        putUser(values, "owner", lead.getOwnerUserId());
        putUser(values, "pendingSales", lead.getPendingAssigneeUserId());
        putUser(values, "operator", event.getOperatorUserId());
        values.put("event.time", event.getOccurredAt());
        values.put("event.scene", event.getSceneCode());
        if (event.getPayload() != null) {
            copyContext(values, event.getPayload(), "assignment.attempt", "assignment.reason",
                    "category.before", "category.after", "followUp.method", "followUp.result",
                    "followUp.remark", "followUp.nextAt", "qualification.reason", "appeal.id",
                    "appeal.roundNo", "appeal.stage", "appeal.reason", "appeal.decisionReason");
        }
        return values;
    }

    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        return new NotifySceneRespDTO(code, name, variables(code), Arrays.stream(roles)
                .map(role -> new NotifySceneRoleRespDTO(role, roleLabel(role))).toList(), ACTIONS);
    }

    private List<NotifySceneVariableRespDTO> variables(String sceneCode) {
        List<NotifySceneVariableRespDTO> variables = new ArrayList<>(List.of(
                variable("lead.id", "客资编号"), variable("lead.name", "客户姓名", true),
                variable("lead.mobile", "手机号码", true), variable("lead.wechatId", "微信号", true),
                variable("lead.sourceType", "来源类型"), variable("lead.sourceChannel", "来源渠道"),
                variable("lead.province", "省份"), variable("lead.city", "城市"),
                variable("lead.category", "客资分类"), variable("lead.remark", "客资备注"),
                variable("lead.status", "客资状态"), variable("lead.assignmentStatus", "分配状态"),
                variable("lead.dispatchMode", "派单方式"), variable("lead.submittedAt", "提交时间"),
                variable("lead.pendingExpiresAt", "接单截止时间"), variable("lead.publicPoolAt", "进入抢单池时间"),
                variable("lead.lastFollowUpAt", "最近跟进时间"), variable("lead.nextFollowUpAt", "下次跟进时间"),
                variable("lead.followUpCount", "跟进次数"), variable("lead.qualificationDeadlineAt", "判定截止时间"),
                variable("lead.suspendedAt", "挂起时间"), variable("product.names", "意向产品"),
                variable("product.primaryName", "主要意向产品"), variable("product.count", "意向产品数量"),
                variable("attachment.names", "附件名称"), variable("attachment.count", "附件数量"),
                variable("submitter.id", "提交人编号"), variable("submitter.name", "提交人"),
                variable("owner.id", "销售编号"), variable("owner.name", "销售专员"),
                variable("pendingSales.id", "待接销售编号"), variable("pendingSales.name", "待接销售"),
                variable("operator.id", "操作人编号"), variable("operator.name", "操作人"),
                variable("event.time", "事件时间"), variable("event.scene", "事件场景")));
        if (ASSIGNED.equals(sceneCode) || REASSIGNED.equals(sceneCode)) {
            variables.add(variable("assignment.attempt", "派单轮次"));
            variables.add(variable("assignment.reason", "分配原因"));
        } else if (PUBLIC_POOL.equals(sceneCode) || EXPIRED.equals(sceneCode)) {
            variables.add(variable("assignment.reason", "分配原因"));
        } else if (CATEGORY_CHANGED.equals(sceneCode)) {
            variables.add(variable("category.before", "变更前分类"));
            variables.add(variable("category.after", "变更后分类"));
        } else if (FOLLOW_UP_RECORDED.equals(sceneCode)) {
            variables.add(variable("followUp.method", "跟进方式"));
            variables.add(variable("followUp.result", "跟进结果"));
            variables.add(variable("followUp.remark", "跟进内容"));
            variables.add(variable("followUp.nextAt", "下次跟进时间"));
        } else if (Set.of(QUALIFICATION_RESTORED, QUALIFICATION_TRANSFERRED,
                QUALIFICATION_RECYCLED, QUALIFICATION_RELEASED).contains(sceneCode)) {
            variables.add(variable("qualification.reason", "处置理由"));
        } else if (Set.of(APPEAL_SUBMITTED, APPEAL_OVERTURNED, APPEAL_UPHELD).contains(sceneCode)) {
            variables.add(variable("appeal.id", "申诉编号"));
            variables.add(variable("appeal.roundNo", "申诉轮次"));
            variables.add(variable("appeal.stage", "审核阶段"));
            variables.add(variable("appeal.reason", "申诉理由"));
            variables.add(variable("appeal.decisionReason", "裁决理由"));
        }
        return List.copyOf(variables);
    }

    private NotifySceneVariableRespDTO variable(String key, String label) {
        return variable(key, label, false);
    }

    private NotifySceneVariableRespDTO variable(String key, String label, boolean sensitive) {
        return new NotifySceneVariableRespDTO(key, label, sensitive);
    }

    private String roleLabel(String role) {
        return switch (role) {
            case ROLE_SUBMITTER -> "提交人"; case ROLE_PENDING_SALES -> "待接销售";
            case ROLE_OWNER -> "当前负责人"; case ROLE_OPERATOR -> "操作人";
            case ROLE_PREVIOUS_OWNER -> "原负责人"; case ROLE_NEW_OWNER -> "新负责人";
            case ROLE_QUALIFICATION_MANAGERS -> "原销售部门及上级部门负责人";
            case ROLE_APPEAL_REVIEWERS -> "本轮申诉处理人";
            default -> role;
        };
    }

    private Set<Long> resolveQualificationManagers(Map<String, Object> payload) {
        Long ownerUserId = longValue(payload.get("previousOwnerUserId"));
        if (ownerUserId == null) ownerUserId = longValue(payload.get("ownerUserId"));
        AdminUserRespDTO owner = ownerUserId == null ? null : adminUserApi.getUser(ownerUserId);
        if (owner == null || owner.getDeptId() == null) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        Set<Long> visited = new HashSet<>();
        Long deptId = owner.getDeptId();
        while (deptId != null && visited.add(deptId)) {
            DeptRespDTO dept = deptApi.getDept(deptId);
            if (dept == null) break;
            if (dept.getLeaderUserId() != null) result.add(dept.getLeaderUserId());
            deptId = dept.getParentId();
        }
        return result;
    }

    private boolean canReadFullContact(LeadDO lead, Long userId) {
        return Objects.equals(userId, lead.getSourceUserId()) || Objects.equals(userId, lead.getOwnerUserId())
                || permissionApi.hasAnyPermissions(userId, QUERY_ALL_PERMISSION)
                || managesOwnerDepartment(userId, lead.getOwnerUserId());
    }

    private boolean managesOwnerDepartment(Long userId, Long ownerUserId) {
        if (ownerUserId == null) return false;
        AdminUserRespDTO owner = adminUserApi.getUser(ownerUserId);
        if (owner == null || owner.getDeptId() == null) return false;
        for (DeptRespDTO managed : deptApi.getDeptListByLeaderUserId(userId)) {
            if (Objects.equals(managed.getId(), owner.getDeptId()) || deptApi.getChildDeptList(managed.getId())
                    .stream().anyMatch(child -> Objects.equals(child.getId(), owner.getDeptId()))) return true;
        }
        return false;
    }

    private void putUser(Map<String, Object> values, String prefix, Long userId) {
        AdminUserRespDTO user = userId == null ? null : adminUserApi.getUser(userId);
        values.put(prefix + ".id", userId);
        values.put(prefix + ".name", user == null ? "" : user.getNickname());
    }

    private String dictLabel(String type, String value) {
        if (value == null) return "";
        return dictDataApi.getDictDataList(type).stream().filter(item -> Objects.equals(item.getValue(), value))
                .map(DictDataRespDTO::getLabel).findFirst().orElse(value);
    }

    private void copyContext(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) if (source.containsKey(key)) target.put(key, source.get(key));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string && !string.isBlank()) return Long.valueOf(string);
        return null;
    }

    private Set<Long> longValues(Object value) {
        if (!(value instanceof Collection<?> collection)) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        for (Object item : collection) {
            Long id = longValue(item);
            if (id != null && id > 0) result.add(id);
        }
        return result;
    }

    private String maskWechat(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() <= 2) return "*".repeat(value.length());
        return value.charAt(0) + "*".repeat(Math.min(6, value.length() - 2)) + value.substring(value.length() - 1);
    }
}
