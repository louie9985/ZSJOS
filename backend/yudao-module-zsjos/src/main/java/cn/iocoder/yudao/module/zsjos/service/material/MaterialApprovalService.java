package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalErrors.*;

@Service
public class MaterialApprovalService {
    @Resource private BpmProcessTaskApi taskApi;
    @Resource private MaterialVersionMapper versionMapper;
    @Resource private MaterialMapper materialMapper;
    @Resource private MaterialTypeMapper typeMapper;
    @Resource private MaterialApprovalRoundMapper roundMapper;
    @Resource private MaterialService materialService;

    public List<MaterialApprovalTypeRespVO> types() {
        return typeMapper.selectList().stream().filter(t -> MaterialApprovalContract.isViral(t.getCode()))
                .map(t -> new MaterialApprovalTypeRespVO().setCode(t.getCode()).setName(t.getName())).toList();
    }

    public PageResult<MaterialApprovalRespVO> page(MaterialApprovalPageReqVO req, Long userId) {
        MaterialTypeDO type = typeMapper.selectByCode(req.getTypeCode());
        if (type == null || !MaterialApprovalContract.isViral(type.getCode())) throw exception(INVALID_TASK);
        BpmTaskPageReqDTO query = new BpmTaskPageReqDTO();
        query.setProcessDefinitionKey(MaterialApprovalContract.processKey(type));
        query.setPageNo(req.getPageNo()); query.setPageSize(req.getPageSize());
        PageResult<BpmTaskRespDTO> tasks = req.isDone() ? taskApi.getDoneTaskPage(userId, query) : taskApi.getTodoTaskPage(userId, query);
        return new PageResult<>(tasks.getList().stream().map(t -> project(t, false)).toList(), tasks.getTotal());
    }

    @ZsjosPermission(bizType = "material-approval", bizId = "#versionId", action = "read")
    public MaterialApprovalRespVO get(Long versionId, String taskId, boolean done, Long userId) {
        BpmTaskRespDTO task = requireTask(versionId, taskId, done, userId);
        MaterialApprovalRespVO result = project(task, true);
        if (!result.isSnapshotAvailable()) throw exception(STALE_SNAPSHOT);
        return result;
    }

    @ZsjosPermission(bizType = "material-approval", bizId = "#versionId", action = "decide")
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long versionId, String taskId, String reason, boolean approve, Long userId) {
        MaterialVersionDO source = versionMapper.selectById(versionId);
        if (source == null) throw exception(INVALID_TASK);
        // Match submit/result callback lock order: material first, then version.
        MaterialDO material = materialMapper.selectByIdForUpdate(source.getMaterialId(), TenantContextHolder.getRequiredTenantId());
        MaterialVersionDO version = versionMapper.selectByIdForUpdate(versionId, TenantContextHolder.getRequiredTenantId());
        BpmTaskRespDTO task = requireTask(versionId, taskId, false, userId);
        if (material == null || version == null || !VERSION_IN_APPROVAL.equals(version.getStatus())
                || !Objects.equals(material.getCurrentDraftVersionId(), versionId)
                || !Objects.equals(version.getProcessInstanceId(), task.getProcessInstanceId())) throw exception(TASK_CHANGED);
        BpmTaskDecisionReqDTO command = new BpmTaskDecisionReqDTO().setTaskId(taskId).setReason(reason);
        if (approve) taskApi.approveTask(userId, command); else taskApi.rejectTask(userId, command);
    }

    public BpmTaskRespDTO requireTask(Long versionId, String taskId, boolean done, Long userId) {
        BpmTaskRespDTO task = done ? taskApi.getDoneTask(userId, taskId) : taskApi.getTodoTask(userId, taskId);
        if (task == null || !Objects.equals(versionId, versionId(task))) throw exception(INVALID_TASK);
        project(task, false); // Validate the owning type and recorded approval round as well as the BPM assignee.
        return task;
    }

    private MaterialApprovalRespVO project(BpmTaskRespDTO task, boolean detail) {
        Long id = versionId(task);
        MaterialVersionDO version = id == null ? null : versionMapper.selectById(id);
        MaterialDO material = version == null ? null : materialMapper.selectById(version.getMaterialId());
        MaterialTypeDO type = material == null ? null : typeMapper.selectById(material.getMaterialTypeId());
        MaterialApprovalRoundDO round = roundMapper.selectByProcessInstanceId(task.getProcessInstanceId());
        if (type == null || !MaterialApprovalContract.isViral(type.getCode())
                || !Objects.equals(MaterialApprovalContract.processKey(type), task.getProcessDefinitionKey())
                || round == null || !Objects.equals(round.getProcessDefinitionKey(), task.getProcessDefinitionKey())
                || !Objects.equals(round.getMaterialVersionId(), id)
                || !Objects.equals(round.getBusinessKey(), task.getBusinessKey())) throw exception(INVALID_TASK);
        boolean available = Objects.equals(version.getProcessInstanceId(), task.getProcessInstanceId())
                && !VERSION_DRAFT.equals(version.getStatus());
        MaterialApprovalRespVO result = new MaterialApprovalRespVO().setTask(task).setVersionId(id)
                .setMaterialNo(material.getMaterialNo()).setSnapshotAvailable(available)
                .setTitle(available ? version.getTitle() : "历史审批（原快照不可用）");
        if (detail && available) result.setSnapshot(materialService.toVersionResp(version));
        return result;
    }

    public static Long versionId(BpmTaskRespDTO task) {
        String key = task.getBusinessKey();
        if (key == null || !key.startsWith(BUSINESS_KEY_PREFIX)) return null;
        try { return Long.valueOf(key.substring(BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException e) { return null; }
    }
}
