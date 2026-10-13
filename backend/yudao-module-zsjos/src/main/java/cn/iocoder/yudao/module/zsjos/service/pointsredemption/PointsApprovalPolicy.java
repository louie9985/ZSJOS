package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PointsApprovalPolicy {
    public static final String PROCESS_KEY = "zsjos_points_redemption";
    @Resource private BpmDefinitionReadApi definitions;
    @Resource private AdminUserApi users;
    @Resource private DeptApi depts;
    public BpmProcessDefinitionMetadataRespDTO validate(Long applicant) {
        var definition = definitions.getPublishedProcessDefinition(PROCESS_KEY);
        validateStructure(definition);
        var user = users.getUser(applicant);
        var dept = user == null || user.getDeptId() == null ? null : depts.getDept(user.getDeptId());
        if (dept == null || dept.getLeaderUserId() == null || Objects.equals(dept.getLeaderUserId(), applicant))
            throw PointsErrors.invalid("申请人部门必须配置有效的其他负责人");
        users.validateUser(dept.getLeaderUserId());
        for (var node : orderedTasks(definition)) {
            if (Objects.equals(node.getCandidateStrategy(), 30)) {
                List<Long> ids;
                try {
                    ids = Arrays.stream(node.getCandidateParam().split(",", -1)).map(String::trim).map(Long::valueOf).toList();
                } catch (NumberFormatException | NullPointerException e) {
                    throw PointsErrors.processApprover("积分审批的指定成员节点必须配置一名有效审批人，请修改后重新发布");
                }
                if (ids.size() != 1) throw PointsErrors.processApprover("积分审批的指定成员节点只能配置一名审批人，请修改后重新发布");
                if (ids.contains(applicant)) throw PointsErrors.processApprover("申请人不能同时担任该积分申请的指定审批人，请核对终审及其他指定成员节点");
                try {
                    users.validateUserList(ids);
                } catch (ServiceException e) {
                    throw PointsErrors.processApprover("积分审批的指定审批人不存在或已停用，请修改后重新发布");
                }
            }
        }
        return definition;
    }
    public List<Long> approverIds(BpmProcessDefinitionMetadataRespDTO d, Long applicant) {
        var user = users.getUser(applicant);
        var dept = depts.getDept(user.getDeptId());
        return orderedTasks(d).stream().map(t -> Objects.equals(t.getCandidateStrategy(), 37)
                ? dept.getLeaderUserId() : Long.valueOf(t.getCandidateParam().trim())).toList();
    }
    static List<BpmUserTaskMetadataRespDTO> orderedTasks(BpmProcessDefinitionMetadataRespDTO d) {
        return d.getOrderedUserTaskKeys().stream().filter(k -> !"StartUserNode".equals(k))
                .map(k -> d.getUserTasks().stream().filter(t -> k.equals(t.getKey())).findFirst().orElseThrow(PointsErrors::process)).toList();
    }
    static void validateStructure(BpmProcessDefinitionMetadataRespDTO d) {
        if (d == null || Boolean.TRUE.equals(d.getSuspended())) throw PointsErrors.processNotPublished();
        if ((!Boolean.TRUE.equals(d.getMandatoryHumanChain())
                && !Boolean.TRUE.equals(d.getConsecutiveApprovalDeduplicationChain())) || d.getOrderedUserTaskKeys() == null
                || d.getUserTasks() == null) throw PointsErrors.processStructure();
        var tasks = orderedTasks(d);
        if (tasks.size() < 2) throw PointsErrors.process("积分审批至少需要上级和董事长两个审批节点，请修改后重新发布");
        if (tasks.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getMandatoryHumanApproval())))
            throw PointsErrors.process("积分审批节点必须人工审批、无人时自动拒绝且禁止同人自动跳过，请修改后重新发布");
        if (tasks.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getRejectEndsProcess()) || !"SINGLE".equals(t.getExecutionMode())))
            throw PointsErrors.process("积分审批节点必须单人处理且拒绝后终止流程，请修改后重新发布");
        if (tasks.stream().anyMatch(t -> t.getCandidateStrategy() == null || !Set.of(30, 37).contains(t.getCandidateStrategy())))
            throw PointsErrors.process("积分审批节点仅支持本部门负责人或指定成员，请修改后重新发布");
        // These are BPM protocol strategy identifiers, never employee/role identifiers.
        if (!Objects.equals(tasks.getFirst().getCandidateStrategy(), 37)
                || !Objects.equals(tasks.getFirst().getCandidateParam(), "1")
                || !Objects.equals(tasks.getLast().getCandidateStrategy(), 30))
            throw PointsErrors.process("积分首个审批节点必须为本部门负责人，最后节点必须指定一名董事长审批人，请修改后重新发布");
    }
}
