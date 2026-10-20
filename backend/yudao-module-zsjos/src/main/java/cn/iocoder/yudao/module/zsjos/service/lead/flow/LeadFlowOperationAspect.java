package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Explicit command inventory; captures participate in the same transaction as the command. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class LeadFlowOperationAspect {
    @Resource private LeadFlowRecorder recorder;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private LeadPublicSeaPoolCycleMapper cycleMapper;
    @Resource private LeadAppealMapper appealMapper;
    @Resource private LeadComplaintMapper complaintMapper;
    @Resource private LeadTransferRequestMapper transferMapper;
    @Resource private LeadDuplicateReviewMapper reviewMapper;
    @Resource private LeadSubmitterFeedbackMapper feedbackMapper;
    @Resource private LeadSubmitterAssistRequestMapper assistMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderApprovalRoundMapper roundMapper;
    private final ThreadLocal<Boolean> active = new ThreadLocal<>();

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint join, LeadFlowOperation operation) throws Throwable {
        if (Boolean.TRUE.equals(active.get())) return join.proceed();
        try {
            return transactionTemplate.execute(status -> {
                active.set(true);
                try { return execute(join, operation); }
                catch (Throwable error) { throw new CommandFailure(error); }
                finally { active.remove(); }
            });
        } catch (CommandFailure failure) { throw failure.getCause(); }
    }

    private Object execute(ProceedingJoinPoint join, LeadFlowOperation op) throws Throwable {
        Object[] args=join.getArgs();
        Object rawId=op.id()<0?null:args[op.id()];
        Long id=rawId instanceof Long value?value:null;
        String type=op.target();
        Object related=related(type,rawId);
        Long leadId=leadId(type,id,related);
        // A Lead lock serializes business snapshots with all existing Lead commands.
        if ("review".equals(type)) {
            if (join.getSignature().getName().equals("resolveAutomatically") && args[1] instanceof Long candidate) leadId=candidate;
        }
        var before=recorder.capture(leadId);
        String beforeRelated=type.endsWith("-create") ? creationFingerprint(type,leadId) : businessFingerprint(type, related);
        Object result=join.proceed();
        Object afterRelated;
        if (type.endsWith("-create") && result instanceof Long value) {
            afterRelated=related(type.replace("-create",""),value);
            id=value;
        } else afterRelated=related(type,rawId);
        String afterFingerprint=type.endsWith("-create") ? creationFingerprint(type,leadId) : businessFingerprint(type,afterRelated);
        boolean changed=!Objects.equals(beforeRelated,afterFingerprint);
        String reason=reason(args,afterRelated);
        String outcome=outcome(afterRelated);
        String referenceType=type.replace("-create","").replace("-process","");
        if ("cycle".equals(referenceType)) referenceType="lead";
        if (afterRelated != null) {
            Object relatedId=JsonUtils.parseObject(JsonUtils.toJsonString(afterRelated),Map.class).get("id");
            if (relatedId instanceof Number number) id=number.longValue();
        }
        if ("lead".equals(referenceType)) id=leadId;
        if ("review".equals(type) && leadId==null && afterRelated != null) {
            var review=(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDuplicateReviewDO)afterRelated;
            var afterMap=review.getAfterSnapshot()==null ? Map.of() : JsonUtils.parseObject(review.getAfterSnapshot(),Map.class);
            if(afterMap.get("leadId") instanceof Number number) { leadId=number.longValue(); before=recorder.capture(leadId); }
        }
        if ("order".equals(type) && result instanceof Long successor && !Objects.equals(successor,id)) {
            afterRelated=related("order",successor); id=successor; outcome=outcome(afterRelated);
        }
        recorder.record(leadId,before,op.value(),actorType(args,op),actor(args,op),referenceType,id,
                relatedNo(afterRelated),outcome,reason,changed,null);
        return result;
    }
    private Object related(String type,Object key) {
        if (key==null) return null;
        Long id=key instanceof Long value?value:null;
        return switch(type) {
            case "cycle","transfer-create" -> cycleMapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
            case "appeal" -> appealMapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
            case "complaint" -> complaintMapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
            case "transfer" -> transferMapper.selectById(id);
            case "transfer-process" -> transferMapper.selectByProcessInstanceIdForUpdate(key.toString(),TenantContextHolder.getRequiredTenantId());
            case "review" -> reviewMapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
            case "order" -> orderMapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());
            case "order-process" -> { var round=roundMapper.selectByProcessInstanceId(key.toString()); yield round==null?null:orderMapper.selectByIdForUpdate(round.getOrderId(),TenantContextHolder.getRequiredTenantId()); }
            case "feedback" -> feedbackMapper.selectById(id);
            case "assist" -> assistMapper.selectById(id);
            default -> null;
        };
    }
    private Long leadId(String type,Long id,Object related) {
        if (type.equals("lead") || Set.of("order-create","appeal-create","complaint-create","feedback-create","assist-create").contains(type)) return id;
        if (related==null) return null;
        var tree=JsonUtils.parseObject(JsonUtils.toJsonString(related),Map.class);
        Object value=tree.get("leadId");
        if (value==null && "review".equals(type)) value=tree.get("matchedLeadId");
        return value instanceof Number number?number.longValue():null;
    }
    private static Long actor(Object[] args,LeadFlowOperation op) {
        return op.actor()<0?null:args[op.actor()] instanceof Long id?id:null;
    }
    private static String actorType(Object[] args,LeadFlowOperation op) {
        if (op.actor()<0 || actor(args,op)==null || actor(args,op)==0L) return "SYSTEM";
        return op.actorType();
    }
    private String creationFingerprint(String type, Long leadId) {
        if (leadId==null) return "[]";
        var ids = switch(type) {
            case "order-create" -> orderMapper.selectList(cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO::getLeadId,leadId).stream().map(r -> r.getId()).toList();
            case "transfer-create" -> transferMapper.selectList(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO::getLeadId,leadId).stream().map(r -> r.getId()).toList();
            case "complaint-create" -> complaintMapper.selectList(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO::getLeadId,leadId).stream().map(r -> r.getId()).toList();
            case "feedback-create" -> feedbackMapper.selectList(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterFeedbackDO::getLeadId,leadId).stream().map(r -> r.getId()).toList();
            case "assist-create" -> assistMapper.selectList(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterAssistRequestDO::getLeadId,leadId).stream().map(r -> r.getId()).toList();
            default -> List.<Long>of();
        };
        return JsonUtils.toJsonString(ids.stream().sorted().toList());
    }
    private static String businessFingerprint(String type,Object related) {
        if (related==null || "cycle".equals(type)) return null;
        var map=JsonUtils.parseObject(JsonUtils.toJsonString(related),Map.class);
        var selected=new LinkedHashMap<String,Object>();
        for (String field:List.of("id","status","currentApprovalRoundId","result","resultType","respondedAt")) selected.put(field,map.get(field));
        return JsonUtils.toJsonString(selected);
    }
    private static String outcome(Object related) {
        if (related==null) return "已完成";
        var map=JsonUtils.parseObject(JsonUtils.toJsonString(related),Map.class);
        String status=Objects.toString(map.get("status"),"");
        if (map.containsKey("resultType")) {
            String result=Objects.toString(map.get("resultType"),"");
            if ("allow_flow".equals(result)) return "复核通过";
            if ("close_duplicate".equals(result)) return "重复客资已关闭";
        }
        return switch(status) {
            case "effective" -> "成交已生效"; case "approved" -> "已通过"; case "rejected","revision_required" -> "已驳回";
            case "terminated" -> "已终止"; case "cancelled" -> "已取消"; case "invalidated" -> "已失效";
            case "pending","pending_approval" -> "待处理"; case "overturned" -> "改判有效";
            case "upheld" -> "维持原判"; case "confirmed" -> "已通过"; case "reviewing", "sales_manager_reviewing", "quality_reviewing", "chairman_reviewing" -> "审核中"; case "handled" -> "founded".equals(map.get("result"))?"投诉成立":"投诉不成立";
            default -> "已完成";
        };
    }
    private static String relatedNo(Object related) {
        if (related==null) return null;
        var map=JsonUtils.parseObject(JsonUtils.toJsonString(related),Map.class);
        return Objects.toString(map.get("orderNo"),null);
    }
    private static String reason(Object[] args,Object related) {
        for (int i=args.length-1;i>=0;i--) {
            Object arg=args[i];
            if (arg!=null && arg.getClass().getSimpleName().endsWith("ReqVO")) {
                var map=JsonUtils.parseObject(JsonUtils.toJsonString(arg),Map.class);
                for (String field:List.of("reason","opinion","remark")) { Object value=map.get(field); if(value instanceof String text && !text.isBlank()) return text; }
            }
        }
        if (related!=null) { var map=JsonUtils.parseObject(JsonUtils.toJsonString(related),Map.class); for (String key : List.of("resolutionReason","handlerOpinion","reason")) if(map.get(key) instanceof String text && !text.isBlank()) return text; }
        return null;
    }
    private static final class CommandFailure extends RuntimeException {
        CommandFailure(Throwable cause) { super(cause); }
    }
}
