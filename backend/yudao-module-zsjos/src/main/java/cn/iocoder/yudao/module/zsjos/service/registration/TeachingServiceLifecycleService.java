package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.*;

@Service
public class TeachingServiceLifecycleService {
    @Resource private ServiceRelationMapper relations;
    @Resource private TeachingServiceEventMapper events;
    @Resource private DeliveryClassMapper classes;
    @Resource private ExceptionDeliveryClassService exceptionClasses;
    @Resource private DeliveryClassService classService;
    @Resource private DeliveryClassScopeService scope;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private BusinessTaskCommandService tasks;
    @Resource private StudentContactExtensionMapper extensions;
    @Resource private ClassTransferRequestMapper transfers;
    @Resource private BpmProcessInstanceApi processes;
    @Resource private TeachingServiceCloseNotifyPublisher closeNotifications;

    @ZsjosPermission(bizType="student-service",bizId="#relationId",action="read")
    public TeachingServiceLifecycleRespVO detail(Long relationId, Long actor) {
        var row = require(relationId,false);
        var result = new TeachingServiceLifecycleRespVO();
        result.setClosed(closed(row)); result.setPreviousClassId(row.getTeachingPreviousClassId());
        result.setClosedAt(row.getTeachingClosedAt());
        List<String> actions = new ArrayList<>();
        if (!closed(row) && "active".equals(row.getStatus()) && Objects.equals(row.getOwnerUserId(),actor)
            && permissions.hasAnyPermissions(actor,CLOSE)) actions.add("CLOSE_SERVICE");
        if (closed(row) && "active".equals(row.getStatus()) && permissions.hasAnyPermissions(actor,RESTORE) && managed(row,actor)) actions.add("RESTORE_SERVICE");
        result.setAvailableActions(actions);
        result.setHistory(events.history(relationId).stream().map(e -> BeanUtils.toBean(e,TeachingServiceLifecycleRespVO.Event.class)).toList());
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="student-service",bizId="#relationId",action="close-service")
    @ZsjosAudit(action="student-service.close",targetType="student-service")
    public void close(Long relationId, Long actor, TeachingServiceCommandReqVO req) {
        var row = require(relationId,true);
        if (!permissions.hasAnyPermissions(actor,CLOSE) || !Objects.equals(actor,row.getOwnerUserId())) throw exception(STUDENT_PERMISSION_DENIED);
        if (replay(row,actor,"close",req)) return;
        if (closed(row) || !"active".equals(row.getStatus())) throw exception(INVALID_STATE);
        version(row,req);
        var target = exceptionClasses.ensure(row.getOwnerUserId());
        Long from = row.getClassId();
        row.setTeachingClosed(true); row.setTeachingPreviousClassId(from); row.setTeachingClosedAt(LocalDateTime.now());
        row.setClassId(target.getId()); row.setVersion(row.getVersion()+1);
        if (relations.updateById(row) != 1) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        // Mark business requests before synchronous BPM callbacks. All use the same transaction.
        cancelApprovals(row,actor,req.getReason().trim());
        tasks.cancelTeachingService(row.getId(),row.getTeachingClosedAt(),req.getReason().trim());
        record(row,actor,"close",from,target.getId(),req);
        closeNotifications.publish(row,actor,req.getReason().trim());
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="student-service",bizId="#relationId",action="restore-service")
    @ZsjosAudit(action="student-service.restore",targetType="student-service")
    public void restore(Long relationId, Long actor, TeachingServiceCommandReqVO req) {
        var row = require(relationId,true);
        if (!permissions.hasAnyPermissions(actor,RESTORE) || !managed(row,actor)) throw exception(STUDENT_PERMISSION_DENIED);
        if (replay(row,actor,"restore",req)) return;
        if (!closed(row) || !"active".equals(row.getStatus())) throw exception(INVALID_STATE);
        version(row,req);
        Long targetId = req.getTargetClassId() == null ? row.getTeachingPreviousClassId() : req.getTargetClassId();
        var target = targetId == null ? null : classes.selectByIdForUpdate(targetId,row.getTenantId());
        if (!DeliveryClassKind.assignable(target) || !"SERVING".equals(target.getStatus())
            || !scope.containsRead(actor,target.getDeptId())) throw exception(TARGET_INVALID);
        classService.validateHomeroom(target.getHomeroomUserId());
        Long from = row.getClassId();
        row.setClassId(target.getId()); row.setOwnerUserId(target.getHomeroomUserId());
        row.setTeachingClosed(false); row.setTeachingRestored(true); row.setVersion(row.getVersion()+1);
        if (relations.updateById(row) != 1) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        record(row,actor,"restore",from,target.getId(),req);
    }

    @ZsjosPermission(bizType="student-service",bizId="#relationId",action="restore-service")
    public java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassOptionRespVO> restoreTargets(Long relationId,Long actor) {
        var row = require(relationId,false);
        if (!permissions.hasAnyPermissions(actor,RESTORE) || !managed(row,actor)) throw exception(STUDENT_PERMISSION_DENIED);
        var visible = scope.resolveRead(actor);
        if (!visible.allDepartments() && visible.deptIds().isEmpty()) return java.util.List.of();
        return classes.selectList(new LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO>()
            .in(cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getClassKind,List.of(DeliveryClassKind.NORMAL,DeliveryClassKind.UNSCHEDULED))
            .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getStatus,"SERVING")
            .in(!visible.allDepartments(),cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getDeptId,visible.deptIds())
            .orderByAsc(cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO::getClassName)).stream().filter(c -> {
                try { classService.validateHomeroom(c.getHomeroomUserId());return true; }
                catch (cn.iocoder.yudao.framework.common.exception.ServiceException invalid) { return false; }
            }).map(c -> {
                var result = new cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassOptionRespVO();
                result.setId(c.getId()); result.setClassName(c.getClassName()); result.setClassNo(c.getClassNo());
                result.setSystemClass(c.getSystemClass()); result.setClassKind(c.getClassKind()); result.setHomeroomUserId(c.getHomeroomUserId()); result.setHomeroomUserName(c.getHomeroomUserNameSnapshot());
                return result;
            }).toList();
    }

    private boolean managed(ServiceRelationDO row,Long actor) {
        var owner = row.getOwnerUserId() == null ? null : users.getUser(row.getOwnerUserId());
        return owner != null && scope.containsRead(actor,owner.getDeptId());
    }
    private ServiceRelationDO require(Long id,boolean lock) {
        var row = lock ? relations.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId()) : relations.selectById(id);
        if (!StudentDeliveryParticipation.includes(row)) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        return row;
    }
    private void version(ServiceRelationDO row,TeachingServiceCommandReqVO req) {
        if (!Objects.equals(row.getVersion(),req.getVersion())) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
    }
    private String fingerprint(Long id,Long actor,String action,TeachingServiceCommandReqVO req) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(id,actor,action,req.getVersion(),req.getTargetClassId(),req.getReason().trim())));
    }
    private boolean replay(ServiceRelationDO row,Long actor,String action,TeachingServiceCommandReqVO req) {
        var event = events.byKey(req.getIdempotencyKey());
        if (event == null) return false;
        if (!Objects.equals(event.getFingerprint(),fingerprint(row.getId(),actor,action,req))) throw exception(KEY_CONFLICT);
        return true;
    }
    private void record(ServiceRelationDO row,Long actor,String action,Long from,Long target,TeachingServiceCommandReqVO req) {
        var user = users.getUser(actor); var owner = users.getUser(row.getOwnerUserId()); var targetClass = classes.selectById(target);
        var event = new TeachingServiceEventDO(); event.setServiceRelationId(row.getId()); event.setAction(action);
        event.setReason(req.getReason().trim()); event.setOperatorUserId(actor); event.setOperatorName(user == null ? null : user.getNickname());
        event.setFromClassId(from); event.setTargetClassId(target); event.setTargetOwnerUserId(row.getOwnerUserId());
        event.setTargetClassName(targetClass == null ? null : targetClass.getClassName()); event.setTargetOwnerName(owner == null ? null : owner.getNickname());
        event.setIdempotencyKey(req.getIdempotencyKey()); event.setFingerprint(fingerprint(row.getId(),actor,action,req));
        event.setOccurredAt(LocalDateTime.now());
        try {
            events.insert(event);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            // Only the command key constraint is a business conflict; other integrity failures stay visible.
            if (isCommandKeyConflict(conflict)) throw exception(KEY_CONFLICT);
            throw conflict;
        }
    }
    static boolean isCommandKeyConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && java.util.regex.Pattern.compile("(?i)\\buk_teaching_event_key\\b").matcher(cause.getMessage()).find()) return true;
        }
        return false;
    }
    private void cancelApprovals(ServiceRelationDO row,Long actor,String reason) {
        var pending = extensions.selectList(new LambdaQueryWrapperX<StudentContactExtensionDO>()
            .eq(StudentContactExtensionDO::getServiceRelationId,row.getId()).eq(StudentContactExtensionDO::getStatus,"pending")
            .orderByAsc(StudentContactExtensionDO::getId).last("FOR UPDATE"));
        for (var extension : pending) {
            if (extensions.transitionPending(extension.getId(),extension.getVersion(),"cancelled","服务关闭："+reason,null,LocalDateTime.now()) != 1)
                throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
            if (extension.getProcessInstanceId() != null) processes.terminateProcessInstanceByBusiness(actor,
                extension.getProcessInstanceId(),CLOSE,"服务关闭："+reason);
        }
        var transfer = transfers.selectPendingByRelation(row.getId());
        if (transfer != null) {
            transfer = transfers.selectByIdForUpdate(transfer.getId(),row.getTenantId());
            if (transfer != null && "pending".equals(transfer.getStatus())) {
                transfer.setStatus("cancelled"); transfer.setResolutionReason("服务关闭："+reason);
                transfer.setFinishedAt(LocalDateTime.now()); transfer.setVersion(transfer.getVersion()+1); transfers.updateById(transfer);
                if (transfer.getProcessInstanceId() != null) processes.terminateProcessInstanceByBusiness(actor,
                    transfer.getProcessInstanceId(),CLOSE,"服务关闭："+reason);
            }
        }
    }
}
