package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.BUSINESS_TASK_BUCKET_INVALID;

@Service
public class BusinessTaskServiceImpl implements BusinessTaskService {

    private static final Set<String> BUCKETS = Set.of("unscheduled", "overdue", "today", "future");

    private final BusinessTaskMapper taskMapper;
    private final Map<String, BusinessTaskSceneProvider> sceneProviders;
    private final Clock clock;

    @Autowired
    public BusinessTaskServiceImpl(BusinessTaskMapper taskMapper, List<BusinessTaskSceneProvider> providers) {
        this(taskMapper, providers, Clock.systemDefaultZone());
    }

    BusinessTaskServiceImpl(BusinessTaskMapper taskMapper, List<BusinessTaskSceneProvider> providers, Clock clock) {
        this.taskMapper = taskMapper;
        this.clock = clock;
        this.sceneProviders = new HashMap<>();
        for (BusinessTaskSceneProvider provider : providers) {
            BusinessTaskSceneProvider previous = sceneProviders.put(provider.getBizType(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate business task provider: " + provider.getBizType());
            }
        }
    }

    @Override
    public BusinessTaskSummaryRespVO getMySummary(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new BusinessTaskSummaryRespVO(
                count(userId, "unscheduled", now), count(userId, "overdue", now),
                count(userId, "today", now), count(userId, "future", now));
    }

    @Override
    public PageResult<BusinessTaskRespVO> getMyPage(Long userId, String bucket, int pageNo, int pageSize) {
        BusinessTaskPageReqVO reqVO = new BusinessTaskPageReqVO();
        reqVO.setBucket(bucket);
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        return getMyPage(userId, reqVO);
    }

    @Override
    public PageResult<BusinessTaskRespVO> getMyPage(Long userId, BusinessTaskPageReqVO reqVO) {
        if (reqVO.getBucket() != null && !BUCKETS.contains(reqVO.getBucket())) {
            throw exception(BUSINESS_TASK_BUCKET_INVALID);
        }
        if (!"pending".equals(reqVO.getStatus()) && reqVO.getBucket() != null) {
            throw exception(BUSINESS_TASK_BUCKET_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        PageResult<BusinessTaskDO> page = taskMapper.selectMyPage(userId, reqVO, now);
        Map<Long, BusinessTaskDisplay> displays = loadDisplays(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(task -> convert(task, displays.get(task.getId()), now)).toList(), page.getTotal());
    }

    private long count(Long userId, String bucket, LocalDateTime now) {
        return taskMapper.selectMyPendingCount(userId, bucket, now);
    }

    private Map<Long, BusinessTaskDisplay> loadDisplays(List<BusinessTaskDO> tasks) {
        Map<Long, BusinessTaskDisplay> result = new HashMap<>();
        tasks.stream().collect(java.util.stream.Collectors.groupingBy(BusinessTaskDO::getBizType))
                .forEach((bizType, group) -> {
                    BusinessTaskSceneProvider provider = sceneProviders.get(bizType);
                    if (provider != null) {
                        result.putAll(provider.getDisplayMap(group));
                    }
                });
        return result;
    }

    private BusinessTaskRespVO convert(BusinessTaskDO task, BusinessTaskDisplay display, LocalDateTime now) {
        BusinessTaskRespVO result = new BusinessTaskRespVO();
        result.setId(task.getId());
        result.setTaskType(task.getTaskType());
        result.setBizType(task.getBizType());
        result.setBizId(task.getBizId());
        result.setTitle(firstNonBlank(task.getTitleSnapshot(), display == null ? null : display.title(),
                "业务任务 #" + task.getId()));
        result.setSummary(firstNonBlank(task.getSummarySnapshot(), display == null ? null : display.summary(), null));
        result.setStatus(task.getStatus());
        result.setDueAt(task.getDueAt());
        result.setRemindAt(task.getRemindAt());
        result.setCompletedAt(task.getCompletedAt());
        result.setCancelledAt(task.getCancelledAt());
        result.setCreateTime(task.getCreateTime());
        result.setOverdue("pending".equals(task.getStatus()) && task.getDueAt() != null && task.getDueAt().isBefore(now));
        String actionCode = firstNonBlank(task.getActionCode(), display == null ? null : display.actionCode(), null);
        result.setActionCode(actionCode);
        result.setActionable("pending".equals(task.getStatus()) && actionCode != null);
        if ("student_service".equals(task.getBizType())) {
            result.setServiceRelationId(task.getBizId());
            result.setTargetTab("overview");
            result.setTargetRecordId(task.getId());
        }
        return result;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }
}
