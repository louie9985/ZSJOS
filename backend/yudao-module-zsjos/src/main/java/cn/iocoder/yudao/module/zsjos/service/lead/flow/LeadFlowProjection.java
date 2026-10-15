package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.flow.LeadFlowHistoryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Historical snapshots pass through the same live identity and related-business access boundaries. */
@Service
public class LeadFlowProjection {
    @Resource private LeadIdentityMaskingService masking;
    @Resource private SecurityFrameworkService security;
    @Resource private LeadObjectPermissionService leadPermission;
    @Resource private LeadSubmitterFeedbackPermissionProvider feedbackPermission;
    @Resource private LeadDuplicateReviewObjectPermissionProvider reviewPermission;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadSubmitterFeedbackMapper feedbackMapper;

    public static LeadFlowSnapshot snapshot(BusinessEventDO event) {
        if (!"lead_flow_operation".equals(event.getEventType()) || event.getRelatedObjectRefs()==null) return null;
        Map<?,?> refs=JsonUtils.parseObject(event.getRelatedObjectRefs(), Map.class);
        if (!(refs.get("flowSnapshot") instanceof Map<?,?> data)) return null;
        var snapshot=JsonUtils.parseObject(JsonUtils.toJsonString(data),LeadFlowSnapshot.class);
        return snapshot.version()==1 ? snapshot : null;
    }
    public LeadFlowHistoryRespVO project(BusinessEventDO event, LeadFlowSnapshot snapshot, LeadDO lead, Long viewer,
                                         LeadIdentityMaskingService.LeadIdentityViewContext context) {
        var vo=new LeadFlowHistoryRespVO();
        vo.setId("event:"+event.getId()); vo.setOccurredAt(event.getOccurredAt()); vo.setSnapshotVersion(1);
        vo.setBusinessObject("客资"); vo.setFlowNode(snapshot.title());
        vo.setSource("SYSTEM".equals(snapshot.actorType()) ? "系统任务" : "PARTNER".equals(snapshot.actorType()) ? "兼职端" : "员工工作台");
        vo.setOperator(identity(snapshot.actorType(),snapshot.actorId(),snapshot.actorName(),context));
        boolean readable=canReadRelated(snapshot,lead,viewer);
        vo.setOperationResult(snapshot.result());
        vo.setReason(readable ? snapshot.reason() : null);
        if (readable) { vo.setRelatedType(snapshot.relatedType()); vo.setRelatedId(snapshot.relatedId()); vo.setRelatedNo(snapshot.relatedNo()); }
        vo.setChanges(snapshot.changes().stream().map(change -> new LeadFlowHistoryRespVO.FieldChangeVO(
                change.field(),change.label(),value(change.before(),context),value(change.after(),context))).toList());
        for (var change : vo.getChanges()) {
            if ("status".equals(change.field())) { vo.setLeadStatusBefore(change.before().text()); vo.setLeadStatusAfter(change.after().text()); }
            if ("assignment".equals(change.field())) { vo.setAssignmentStatusBefore(change.before().text()); vo.setAssignmentStatusAfter(change.after().text()); }
            if ("owner".equals(change.field())) { vo.setFromOwner(change.before().text()); vo.setToOwner(change.after().text()); }
        }
        return vo;
    }
    public boolean canReadRelated(LeadFlowSnapshot snapshot, LeadDO lead, Long viewer) {
        String type=Objects.toString(snapshot.relatedType(),"lead");
        boolean partnerManager=leadPermission.canReadSubordinatePartnerLead(lead,viewer);
        return switch(type) {
            case "lead", "cycle" -> true;
            case "appeal" -> partnerManager || PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType()) && Objects.equals(lead.getProviderOwnerId(),viewer)
                    || security.hasAnyPermissions(PERMISSION_DETAIL_APPEAL_READ,PERMISSION_APPEAL_REVIEW_SALES_MANAGER,PERMISSION_APPEAL_REVIEW_QUALITY,PERMISSION_APPEAL_REVIEW_CHAIRMAN);
            case "complaint" -> partnerManager || security.hasPermission(PERMISSION_DETAIL_COMPLAINT_READ);
            case "feedback" -> {
                var row=snapshot.relatedId()==null?null:feedbackMapper.selectById(snapshot.relatedId());
                yield security.hasPermission(cn.iocoder.yudao.module.zsjos.enums.LeadSubmitterFeedbackConstants.READ_PERMISSION)
                        && feedbackPermission.canRead(lead,viewer) && row!=null && Objects.equals(row.getLeadId(),lead.getId())
                        && (Objects.equals(lead.getOwnerUserId(),viewer)
                        || "ADMIN".equals(row.getSubmitterSubjectType()) && Objects.equals(row.getSubmitterUserId(),viewer));
            }
            case "assist" -> security.hasPermission(PERMISSION_SUBMITTER_ASSIST_READ);
            case "order" -> {
                var order=snapshot.relatedId()==null?null:orderMapper.selectById(snapshot.relatedId());
                yield (partnerManager || security.hasPermission(PERMISSION_DETAIL_ORDER_READ)) && order!=null && Objects.equals(order.getPersonId(),lead.getPersonId());
            }
            // There is no existing Lead-detail read contract for these business forms.
            case "review" -> snapshot.relatedId()!=null && reviewPermission.hasPermission(snapshot.relatedId(),"read",viewer);
            case "transfer" -> false;
            default -> false;
        };
    }
    private LeadFlowHistoryRespVO.FieldValueVO value(LeadFlowSnapshot.Value value, LeadIdentityMaskingService.LeadIdentityViewContext context) {
        if (value==null) return new LeadFlowHistoryRespVO.FieldValueVO("UNRECORDED",null);
        return new LeadFlowHistoryRespVO.FieldValueVO(value.state(),identity(value.identityType(),value.identityId(),value.text(),context));
    }
    private String identity(String type, Long id, String name, LeadIdentityMaskingService.LeadIdentityViewContext context) {
        if ("ADMIN".equals(type)) return masking.snapshotName(context,name,id,LeadIdentityRole.OPERATOR);
        if ("PARTNER".equals(type)) return masking.partnerName(context,name);
        return name;
    }
}
