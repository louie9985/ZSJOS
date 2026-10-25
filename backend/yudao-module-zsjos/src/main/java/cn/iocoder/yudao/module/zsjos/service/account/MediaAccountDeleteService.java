package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.service.registration.MediaServiceReadiness;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountDeleteRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDeleteRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MediaAccountDeleteService {
    @Resource private MediaAccountMapper mapper;
    @Resource private BpmProcessInstanceApi bpm;
    @Resource private AdminUserApi users;
    @Resource private ServiceRelationMapper relations;
    @Resource private PermissionApi permissions;
    @Resource private MediaWorkflowEventService events;
    @Resource private MediaAccountDeleteRequestMapper requestMapper;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private ProductionTicketMapper tickets;
    @Resource private ContentReviewBatchMapper reviewBatches;
    @Resource private PlatformTransactionManager transactionManager;

    @ZsjosPermission(bizType=BIZ_TYPE_MEDIA_ACCOUNT, bizId="#id", action="delete-request")
    @Transactional(rollbackFor=Exception.class)
    public String submit(Long id, String reason, Long userId) {
        MediaAccountDO a=mapper.selectByIdForUpdate(id,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        requireDeletable(a);
        var owners = owners(a);
        if (!Objects.equals(owners.operator(), userId) || Objects.equals(owners.director(), userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        Long reviewer = owners.director();
        var reviewerUser = reviewer == null ? null : users.getUser(reviewer);
        if (reviewerUser == null || !CommonStatusEnum.ENABLE.getStatus().equals(reviewerUser.getStatus())
                || !permissions.hasAnyPermissions(reviewer, "zsjos:media-account:delete-approve")) throw exception(MEDIA_ACCOUNT_DELETE_REVIEWER_INVALID);
        BpmProcessInstanceCreateReqDTO req=new BpmProcessInstanceCreateReqDTO(); req.setProcessDefinitionKey(PROCESS_KEY_DELETE);
        req.setBusinessKey("media-account-delete:"+id+":"+System.nanoTime()); req.setVariables(new java.util.HashMap<>(Map.of("accountId",id,"assignee",reviewer,"coll_userList",List.of(reviewer))));
        req.setStartUserSelectAssignees(Map.of("review", List.of(reviewer)));
        String process=bpm.createProcessInstance(userId,req);
        if(mapper.claimDelete(id,a.getVersion(),process,userId,reviewer,reason.trim())==0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        // Empty newly created accounts still need a reproducible deletion snapshot.
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("accountNo", a.getAccountNo()); snapshot.put("nickname", a.getNickname()); snapshot.put("platform", a.getPlatformLabelSnapshot());
        var requester = users.getUser(userId);
        snapshot.put("requestedByName", requester == null ? null : requester.getNickname());
        snapshot.put("reviewerName", reviewerUser.getNickname());
        requestMapper.insert(new MediaAccountDeleteRequestDO().setAccountId(id).setProcessInstanceId(process)
                .setAccountSnapshotJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(snapshot))
                .setReason(reason.trim()).setRequestedByUserId(userId).setReviewerUserId(reviewer).setStatus("pending").setAttemptCount(0).setVersion(0));
        events.transition(BIZ_TYPE_MEDIA_ACCOUNT,id,userId,a.getRunStatus(),"delete_pending",reason,"media-account-delete-submit:"+id+":"+process); return process;
    }
    @ZsjosPermission(bizType=BIZ_TYPE_MEDIA_ACCOUNT, bizId="#id", action="delete-direct")
    @Transactional(rollbackFor=Exception.class)
    public void deleteDirectly(Long id, String reason, Long userId) {
        MediaAccountDO account = mapper.selectByIdForUpdate(id,
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        requireDeletable(account);
        if (!Objects.equals(owners(account).director(), userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        if (mapper.deleteDirectly(id, account.getVersion(), userId, reason.trim()) == 0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        // Linked exclusions and direct deletion must commit together, so failed cleanup remains retryable.
        terminateInFlight(id);
        events.transition(BIZ_TYPE_MEDIA_ACCOUNT, id, userId, account.getRunStatus(), "deleted", reason.trim(),
                "media-account-delete-direct:" + id + ":" + account.getVersion());
    }

    @ZsjosPermission(bizType=BIZ_TYPE_MEDIA_ACCOUNT, bizId="#id", action="withdraw-delete")
    @Transactional(rollbackFor=Exception.class)
    public void withdraw(Long id,Long userId){ MediaAccountDO a=mapper.selectByIdForUpdate(id,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()); if(a==null||!Objects.equals(a.getDeleteRequestedByUserId(),userId)||!"pending".equals(a.getDeleteStatus())) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED); bpm.cancelProcessInstanceByStartUser(userId,a.getDeleteProcessInstanceId(),"申请人撤回"); mapper.finishDelete(id,a.getVersion(),a.getDeleteProcessInstanceId(),"withdrawn","申请人撤回"); }
    @Transactional(rollbackFor=Exception.class)
    public void onResult(BpmProcessInstanceStatusEvent e){
        MediaAccountDO a=mapper.selectByDeleteProcessInstanceId(e.getId()); if(a==null||!"pending".equals(a.getDeleteStatus())) return;
        a = mapper.selectByIdForUpdate(a.getId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        if (a == null || !"pending".equals(a.getDeleteStatus()) || !Objects.equals(a.getDeleteProcessInstanceId(), e.getId())) return;
        MediaAccountDeleteRequestDO request=requestMapper.byProcess(e.getId());
        if(!BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(e.getStatus())) { mapper.finishDelete(a.getId(),a.getVersion(),e.getId(),"rejected",e.getReason()); if(request!=null) requestMapper.result(request.getId(),request.getVersion(),"rejected",e.getReason(),null); return; }
        boolean completed = false;
        try {
            var account = a;
            var cleanup = new TransactionTemplate(transactionManager);
            cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            // A cleanup failure must undo earlier linked updates while allowing BPM approval and retry state to commit.
            cleanup.executeWithoutResult(status -> {
                terminateInFlight(account.getId());
                if (mapper.finishDelete(account.getId(),account.getVersion(),e.getId(),"approved",e.getReason()) != 1)
                    throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
                if(request!=null && requestMapper.result(request.getId(),request.getVersion(),"completed",e.getReason(),null) != 1)
                    throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            });
            completed = true;
        } catch(Exception ex) {
            // Driver/parser messages may contain SQL and exceed last_error; persist only a bounded non-sensitive summary.
            if(request!=null) requestMapper.result(request.getId(),request.getVersion(),"approved_pending",e.getReason(),
                    "关联业务处理失败（" + ex.getClass().getSimpleName() + "），请重试删除处理");
        }
        events.transition(BIZ_TYPE_MEDIA_ACCOUNT,a.getId(),a.getDeleteReviewerUserId(),"delete_pending", completed ? "deleted":"delete_pending",e.getReason(),"media-account-delete-result:"+a.getId()+":"+e.getId());
    }
    public void retry(Long requestId){ MediaAccountDeleteRequestDO r=requestMapper.selectById(requestId); if(r==null||!"approved_pending".equals(r.getStatus())) return; MediaAccountDO a=mapper.selectById(r.getAccountId()); if(a==null)return; terminateInFlight(a.getId()); mapper.finishDelete(a.getId(),a.getVersion(),r.getProcessInstanceId(),"approved",r.getResultReason()); requestMapper.result(r.getId(),r.getVersion(),"completed",r.getResultReason(),null); }
    @Transactional(rollbackFor=Exception.class)
    public void retryByAccount(Long accountId){ MediaAccountDeleteRequestDO r=requestMapper.byAccount(accountId); if(r!=null) retry(r.getId()); }
    private void terminateInFlight(Long accountId){
        taskMapper.cancelByAccountId(accountId, java.time.LocalDateTime.now(), "账号删除审批通过");
        tickets.excludeDeletedAccount(accountId);
        reviewBatches.excludeDeletedAccount(accountId);
    }
    private void requireDeletable(MediaAccountDO account) {
        if (account == null) throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        if ("pending".equals(account.getRebindStatus()) || "starting".equals(account.getRebindStatus()))
            throw exception(MEDIA_ACCOUNT_STATE_INVALID);
        if ("pending".equals(account.getDeleteStatus()) || "delete_pending".equals(account.getRunStatus())
                || "deleted".equals(account.getRunStatus())) throw exception(MEDIA_ACCOUNT_STATE_INVALID);
    }

    private Owners owners(MediaAccountDO account) {
        if (account.getCreateServiceRelationId() == null) return new Owners(account.getOwnerOperatorUserId(), account.getDirectorUserId());
        var source = relations.selectByIdForUpdate(account.getCreateServiceRelationId(),
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        if (source == null || !Objects.equals(source.getTenantId(), account.getTenantId())
                || !Objects.equals(source.getPersonId(), account.getStudentPersonId())
                || !"active".equals(source.getStatus()) || !MediaServiceReadiness.ready(source)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        return new Owners(source.getOperatorUserId(), source.getContentDirectorUserId());
    }

    private record Owners(Long operator, Long director) {}
}
