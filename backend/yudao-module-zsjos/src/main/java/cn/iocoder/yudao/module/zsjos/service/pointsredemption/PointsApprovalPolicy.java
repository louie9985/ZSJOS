package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
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
                try {
                    var ids = Arrays.stream(node.getCandidateParam().split(",")).map(String::trim).map(Long::valueOf).toList();
                    if (ids.size() != 1 || ids.contains(applicant)) throw PointsErrors.process();
                    users.validateUserList(ids);
                } catch (RuntimeException e) { throw PointsErrors.process(); }
            }
        }
        var last = orderedTasks(definition).getLast();
        List<Long> finalUsers;
        try { finalUsers = Arrays.stream(last.getCandidateParam().split(",")).map(String::trim).map(Long::valueOf).toList(); }
        catch (RuntimeException e) { throw PointsErrors.process(); }
        if (finalUsers.size() != 1 || finalUsers.contains(applicant)) throw PointsErrors.process();
        users.validateUserList(finalUsers);
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
        if (d == null || Boolean.TRUE.equals(d.getSuspended()) || !Boolean.TRUE.equals(d.getMandatoryHumanChain())
                || d.getOrderedUserTaskKeys() == null || d.getUserTasks() == null) throw PointsErrors.process();
        var tasks = orderedTasks(d);
        if (tasks.size() < 2 || tasks.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getMandatoryHumanApproval())
                || !Boolean.TRUE.equals(t.getRejectEndsProcess()) || !"SINGLE".equals(t.getExecutionMode())
                || t.getCandidateStrategy() == null || !Set.of(30, 37).contains(t.getCandidateStrategy()))) throw PointsErrors.process();
        // These are BPM protocol strategy identifiers, never employee/role identifiers.
        if (!Objects.equals(tasks.getFirst().getCandidateStrategy(), 37)
                || !Objects.equals(tasks.getFirst().getCandidateParam(), "1")
                || !Objects.equals(tasks.getLast().getCandidateStrategy(), 30)) throw PointsErrors.process();
    }
}
