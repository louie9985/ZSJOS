package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

@Service
public class ExamCollectionTodoService {
    @Resource private ExamCollectionMemberMapper members;
    @Resource private BusinessTaskCommandService commands;

    /** Changes of roster/lifecycle replace pending generations; submissions update the current generation. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void synchronize(ExamCollectionTaskDO task, boolean replaceGeneration) {
        var all = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>().eq(ExamCollectionMemberDO::getTaskId, task.getId()));
        var byPlanner = all.stream().filter(m -> m.getPlannerUserId() != null)
                .collect(Collectors.groupingBy(ExamCollectionMemberDO::getPlannerUserId));
        var now = ExamCollectionPublicService.now();
        for (var entry : byPlanner.entrySet()) {
            Long planner = entry.getKey();
            var active = entry.getValue().stream().filter(m -> Boolean.TRUE.equals(m.getActive())).toList();
            if (replaceGeneration || !OPEN.equals(task.getStatus()) || active.isEmpty())
                commands.cancel(TASK_TYPE, task.getId(), planner, now, "考试资料收集任务或名单已更新");
            if (!OPEN.equals(task.getStatus()) || active.isEmpty()) continue;
            long collected = active.stream().filter(m -> m.getLastSubmissionId() != null).count();
            if (collected == active.size()) {
                commands.complete(TASK_TYPE, task.getId(), planner, now); continue;
            }
            String classes = active.stream().map(ExamCollectionMemberDO::getClassNameSnapshot).distinct().collect(Collectors.joining("、"));
            String summary = "已收齐 " + collected + "/" + active.size() + " 人 · " + classes;
            var payload = new LinkedHashMap<String, Object>(); payload.put("collectionTaskId", task.getId());
            payload.put("revision", task.getRevision()); payload.put("classIds", active.stream().map(ExamCollectionMemberDO::getClassId).distinct().toList());
            commands.create(new BusinessTaskCreateCommand(TASK_TYPE, BIZ_TYPE, task.getId(), planner,
                    "考试资料收集：" + task.getTitle(), summary, TASK_ACTION, null, now,
                    JsonUtils.toJsonString(payload), "exam-collection:" + task.getId() + ":" + task.getRevision() + ":" + planner));
            commands.updatePending(TASK_TYPE, task.getId(), planner, "考试资料收集：" + task.getTitle(), summary, null, now);
        }
    }
}
