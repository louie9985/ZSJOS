package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 审批内容查询服务实现。
 *
 * <p>解析链路：taskId → {@link BpmProcessTaskApi} 取任务（同时完成归属校验）→ businessKey
 * → 注册表按前缀选 Provider → Provider 回查业务表并做业务级权限校验。
 *
 * <p>刻意"失败即降级"：任何环节解析不出来都返回 null，让审批中心退回通用展示，
 * 而不是抛异常打断整张列表。<b>审批中心的可用性优先于业务内容展示的完整性</b>——
 * 审批人看不到业务摘要仍能完成审批，但列表整个报错就什么都做不了。
 */
@Slf4j
@Service
@Validated
public class BpmApprovalContentServiceImpl implements BpmApprovalContentService {

    private static final String VIEW_DONE = "done";

    @Resource
    private BpmProcessTaskApi processTaskApi;
    @Resource
    private BpmApprovalContentRegistry registry;

    @Override
    public BpmApprovalBriefVO getBrief(String taskId, String view, Long viewerId) {
        Optional<Resolved> resolved = resolve(taskId, view, viewerId);
        if (resolved.isEmpty()) {
            return null;
        }
        Resolved target = resolved.get();
        try {
            return target.provider().brief(target.businessId(), viewerId);
        } catch (Exception ex) {
            // 单个业务域解析失败不应影响其他任务，也不应把异常信息暴露给前端。
            log.debug("[getBrief][任务({}) 业务内容解析失败：{}]", taskId, ex.toString());
            return null;
        }
    }

    @Override
    public Map<String, BpmApprovalBriefVO> getBriefMap(List<String> taskIds, String view, Long viewerId) {
        Map<String, BpmApprovalBriefVO> result = new LinkedHashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return result;
        }
        for (String taskId : taskIds) {
            if (taskId == null || taskId.isBlank()) {
                continue;
            }
            BpmApprovalBriefVO brief = getBrief(taskId, view, viewerId);
            if (brief != null) {
                result.put(taskId, brief);
            }
        }
        return result;
    }

    @Override
    public BpmApprovalDetailVO getDetail(String taskId, String view, Long viewerId) {
        Optional<Resolved> resolved = resolve(taskId, view, viewerId);
        if (resolved.isEmpty()) {
            return null;
        }
        Resolved target = resolved.get();
        try {
            return target.provider().detail(target.businessId(), viewerId);
        } catch (Exception ex) {
            log.debug("[getDetail][任务({}) 业务详情解析失败：{}]", taskId, ex.toString());
            return null;
        }
    }

    /**
     * taskId → Provider + 业务主键。任何一步不满足都返回空。
     */
    private Optional<Resolved> resolve(String taskId, String view, Long viewerId) {
        if (taskId == null || taskId.isBlank() || viewerId == null) {
            return Optional.empty();
        }
        boolean done = VIEW_DONE.equals(view);
        // getTodoTask/getDoneTask 内部按 userId 校验归属：非本人任务直接返回 null。
        BpmTaskRespDTO task = done
                ? processTaskApi.getDoneTask(viewerId, taskId)
                : processTaskApi.getTodoTask(viewerId, taskId);
        if (task == null || task.getBusinessKey() == null) {
            return Optional.empty();
        }
        return registry.resolve(task.getBusinessKey())
                .flatMap(provider -> {
                    String businessId = provider.parseBusinessId(task.getBusinessKey());
                    return businessId == null
                            ? Optional.<Resolved>empty()
                            : Optional.of(new Resolved(provider, businessId));
                });
    }

    private record Resolved(BpmApprovalContentProvider provider, String businessId) {
    }
}
