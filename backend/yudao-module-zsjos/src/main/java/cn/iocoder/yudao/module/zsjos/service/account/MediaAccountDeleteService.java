package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
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
    @Resource private DeptApi depts;
    @Resource private PermissionApi permissions;
    @Resource private MediaWorkflowEventService events;
    @Resource private MediaAccountDeleteRequestMapper requestMapper;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private ProductionTicketMapper tickets;
    @Resource private ContentReviewBatchMapper reviewBatches;

    @ZsjosPermission(bizType=BIZ_TYPE_MEDIA_ACCOUNT, bizId="#id", action="delete")
    @Transactional(rollbackFor=Exception.class)
    public String submit(Long id, String reason, Long userId) {
        MediaAccountDO a=mapper.selectByIdForUpdate(id,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        if(a==null || (!Objects.equals(a.getOwnerOperatorUserId(),userId)&&!Objects.equals(a.getDirectorUserId(),userId))) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        if("pending".equals(a.getDeleteStatus())||"deleted".equals(a.getRunStatus())) throw exception(MEDIA_ACCOUNT_STATE_INVALID);
        Long reviewer=supervisor(userId); BpmProcessInstanceCreateReqDTO req=new BpmProcessInstanceCreateReqDTO(); req.setProcessDefinitionKey(PROCESS_KEY_DELETE);
        req.setBusinessKey("media-account-delete:"+id+":"+System.nanoTime()); req.setVariables(new java.util.HashMap<>(Map.of("accountId",id,"assignee",reviewer,"coll_userList",List.of(reviewer))));
        String process=bpm.createProcessInstance(userId,req);
        if(mapper.claimDelete(id,a.getVersion(),process,userId,reviewer,reason.trim())==0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        requestMapper.insert(new MediaAccountDeleteRequestDO().setAccountId(id).setProcessInstanceId(process)
                .setAccountSnapshotJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(Map.of("accountNo",a.getAccountNo(),"nickname",a.getNickname(),"platform",a.getPlatformLabelSnapshot())))
                .setReason(reason.trim()).setRequestedByUserId(userId).setReviewerUserId(reviewer).setStatus("pending").setAttemptCount(0).setVersion(0));
        events.transition(BIZ_TYPE_MEDIA_ACCOUNT,id,userId,a.getRunStatus(),"delete_pending",reason,"media-account-delete-submit:"+id+":"+process); return process;
    }
    @ZsjosPermission(bizType=BIZ_TYPE_MEDIA_ACCOUNT, bizId="#id", action="delete")
    @Transactional(rollbackFor=Exception.class)
    public void withdraw(Long id,Long userId){ MediaAccountDO a=mapper.selectByIdForUpdate(id,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()); if(a==null||!Objects.equals(a.getDeleteRequestedByUserId(),userId)||!"pending".equals(a.getDeleteStatus())) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED); bpm.cancelProcessInstanceByStartUser(userId,a.getDeleteProcessInstanceId(),"申请人撤回"); mapper.finishDelete(id,a.getVersion(),a.getDeleteProcessInstanceId(),"withdrawn","申请人撤回"); }
    @Transactional(rollbackFor=Exception.class)
    public void onResult(BpmProcessInstanceStatusEvent e){
        MediaAccountDO a=mapper.selectByDeleteProcessInstanceId(e.getId()); if(a==null||!"pending".equals(a.getDeleteStatus())) return;
        MediaAccountDeleteRequestDO request=requestMapper.byProcess(e.getId());
        if(!BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(e.getStatus())) { mapper.finishDelete(a.getId(),a.getVersion(),e.getId(),"rejected",e.getReason()); if(request!=null) requestMapper.result(request.getId(),request.getVersion(),"rejected",e.getReason(),null); return; }
        boolean completed = false;
        try { terminateInFlight(a.getId()); mapper.finishDelete(a.getId(),a.getVersion(),e.getId(),"approved",e.getReason()); if(request!=null) requestMapper.result(request.getId(),request.getVersion(),"completed",e.getReason(),null); completed = true; }
        catch(Exception ex) { if(request!=null) requestMapper.result(request.getId(),request.getVersion(),"approved_pending",e.getReason(),ex.getMessage()); }
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
    private Long supervisor(Long userId){ var u=users.getUser(userId); Long d=u==null?null:u.getDeptId(); for(int i=0;i<20&&d!=null;i++){var dept=depts.getDept(d); Long l=dept==null?null:dept.getLeaderUserId(); var x=l==null?null:users.getUser(l); if(x!=null&&!userId.equals(l)&&CommonStatusEnum.ENABLE.getStatus().equals(x.getStatus())&&permissions.hasAnyPermissions(l,"zsjos:media-account:delete-approve")) return l; d=dept==null?null:dept.getParentId();} throw exception(MEDIA_REBIND_REVIEWER_INVALID);}
}
