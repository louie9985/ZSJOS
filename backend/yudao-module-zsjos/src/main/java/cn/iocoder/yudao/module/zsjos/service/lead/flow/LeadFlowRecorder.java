package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowSnapshot.Value;

/** Captures only persisted business fields; never serializes a request or an arbitrary entity. */
@Service
public class LeadFlowRecorder {
    @Resource private LeadFlowEvidence evidenceService;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper productMapper;
    @Resource private LeadPublicSeaPoolCycleMapper cycleMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadAssignmentHistoryMapper assignmentMapper;
    @Resource private LeadPublicSeaPoolEventMapper publicSeaPoolEventMapper;
    @Resource private AdminUserApi userApi;
    @Resource private PartnerMapper partnerMapper;

    public static final Map<String, String> LABELS;
    static {
        var labels = new LinkedHashMap<String, String>();
        String[] pairs = {"name","姓名","mobile","手机号","wechatId","微信","region","地区",
                "category","客资类别","products","意向产品","status","客资状态","assignment","分配状态",
                "owner","负责人","ownerIdentity","负责人身份","pendingOwner","待接人员",
                "salesStage","销售阶段","lastFollowUpAt","上次跟进时间","nextFollowUpAt","下次跟进时间",
                "qualificationRound","判定轮次","qualificationDeadline","判定截止时间","suspendedAt","挂起时间",
                "validDescription","有效判定说明","invalidReason","无效原因","invalidDescription","无效说明",
                "closedAt","关闭时间","closeReason","关闭原因","remark","备注",
                "sourceType","来源类型","sourceChannel","来源渠道","provider","提供方",
                "publicSeaStatus","公海状态","collaborator","协作人"};
        for (int i=0; i<pairs.length; i+=2) labels.put(pairs[i], pairs[i+1]);
        LABELS = Collections.unmodifiableMap(labels);
    }
    public record State(Map<String, Value> values, Set<String> records, boolean exists) {}

    public State capture(Long id) {
        if (id == null) return new State(Map.of(), Set.of(), false);
        LeadDO lead = leadMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (lead == null) return new State(Map.of(), Set.of(), false);
        var values = new LinkedHashMap<String, Value>();
        values.put("name", Value.text(lead.getSubmittedName()));
        values.put("mobile", Value.text(lead.getSubmittedMobile()));
        values.put("wechatId", Value.text(lead.getSubmittedWechatId()));
        values.put("region", choice(Objects.toString(lead.getProvinceCode(),"")+"/"+Objects.toString(lead.getCityCode(),""),
                Objects.toString(lead.getProvinceName(),"")+" "+Objects.toString(lead.getCityName(),"")));
        values.put("category", choice(lead.getLeadCategory(),lead.getLeadCategoryLabelSnapshot()));
        var products = productMapper.selectListByLeadId(id);
        values.put("products", choice(JsonUtils.toJsonString(products.stream().map(p -> Arrays.asList(
                p.getSpuRef(), p.getSkuRef(), p.getSelectedSpecsJson(), p.getIsPrimary())).toList()),
                String.join("；", products.stream().map(p -> Objects.toString(p.getSpuNameSnapshot(),p.getProductNameSnapshot())
                        + " / " + Objects.toString(p.getSkuNameSnapshot(),"规格未知")).toList())));
        values.put("status", choice(lead.getStatus(), status(lead.getStatus())));
        values.put("assignment", choice(lead.getAssignmentStatus(), assignment(lead.getAssignmentStatus())));
        values.put("owner", identity("ADMIN",lead.getOwnerUserId(),null));
        values.put("pendingOwner", identity("ADMIN",lead.getPendingAssigneeUserId(),null));
        values.put("ownerIdentity", choice(lead.getOwnerIdentity(),lead.getOwnerIdentity()==null?null:
                cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ownerIdentityLabel(lead.getOwnerIdentity())));
        values.put("salesStage",choice(lead.getSalesStage(),lead.getSalesStageLabelSnapshot()));
        values.put("lastFollowUpAt",Value.text(lead.getLastFollowUpAt()));
        values.put("nextFollowUpAt",Value.text(lead.getNextFollowUpAt()));
        values.put("qualificationRound",Value.text(lead.getQualificationRoundNo()));
        values.put("qualificationDeadline",Value.text(lead.getQualificationDeadlineAt()));
        values.put("suspendedAt",Value.text(lead.getSuspendedAt()));
        values.put("validDescription",Value.text(lead.getValidDescription()));
        values.put("invalidReason",choice(lead.getInvalidReason(),lead.getInvalidReasonLabelSnapshot()));
        values.put("invalidDescription",Value.text(lead.getInvalidDescription()));
        values.put("closedAt",Value.text(lead.getClosedAt()));
        values.put("closeReason",Value.text(lead.getCloseReason()));
        values.put("remark",Value.text(lead.getRemark()));
        values.put("sourceType",choice(lead.getSourceType(),source(lead.getSourceType())));
        values.put("sourceChannel",choice(lead.getSourceChannelId(),lead.getSourceChannelLabelSnapshot()));
        values.put("provider",identity("partner".equals(lead.getProviderOwnerType())?"PARTNER":"ADMIN",
                lead.getProviderOwnerId(),lead.getProviderOwnerNameSnapshot()));
        var cycle = cycleMapper.selectActiveByLeadId(id);
        values.put("publicSeaStatus",cycle==null?Value.text(null):choice(cycle.getStatus(),pool(cycle.getStatus())));
        values.put("collaborator",cycle==null?Value.text(null):identity("ADMIN",cycle.getCollaboratorUserId(),null));
        return new State(values, records(id), true);
    }
    public Set<String> records(Long id) {
        var result = new LinkedHashSet<String>();
        eventMapper.selectByLeadId(id).forEach(e -> result.add("event:"+e.getId()));
        assignmentMapper.selectByLeadId(id).forEach(e -> result.add("assignment:"+e.getId()));
        publicSeaPoolEventMapper.selectByLeadId(id).forEach(e -> result.add("public-sea-pool:"+e.getId()));
        return result;
    }
    public Value identity(String type,Long id,String snapshot) {
        if (id==null) return Value.text(null);
        String name=snapshot;
        if (name==null && "ADMIN".equals(type)) { var user=userApi.getUser(id); name=user==null?"未知账号":user.getNickname(); }
        if (name==null && "PARTNER".equals(type)) { var partner=partnerMapper.selectById(id); name=partner==null?"未知账号":partner.getName(); }
        return new Value("VALUE",id.toString(),name,type,id);
    }
    public void record(Long leadId, State before, String title, String actorType, Long actorId,
                       String relatedType, Long relatedId, String relatedNo, String result, String reason,
                       boolean businessChanged, String stableKey) {
        State after=capture(leadId);
        if (!after.exists()) return;
        var changes=LeadFlowSnapshot.diff(before.values(),after.values(),LABELS);
        var covered=new LinkedHashSet<>(after.records()); covered.removeAll(before.records());
        if ("修改基础信息".equals(title) && changes.isEmpty()) return;
        if (changes.isEmpty() && covered.isEmpty() && !businessChanged) return;
        String key=stableKey==null?"lead-flow:"+UUID.randomUUID():"lead-flow:"+stableKey;
        if (eventMapper.selectByIdempotencyKey(key)!=null) return;
        if (!before.exists()) covered.add("lead:"+leadId);
        var newEvents=eventMapper.selectByLeadId(leadId).stream().filter(item -> covered.contains("event:"+item.getId())).toList();
        if (reason==null) reason=newEvents.stream().map(BusinessEventDO::getReason).filter(Objects::nonNull).findFirst().orElse(null);
        if (reason==null) reason=assignmentMapper.selectByLeadId(leadId).stream().filter(item -> covered.contains("assignment:"+item.getId())).map(LeadAssignmentHistoryDO::getReason).filter(Objects::nonNull).findFirst().orElse(null);
        if (reason==null) reason=publicSeaPoolEventMapper.selectByLeadId(leadId).stream().filter(item -> covered.contains("public-sea-pool:"+item.getId())).map(LeadPublicSeaPoolEventDO::getReason).filter(Objects::nonNull).findFirst().orElse(null);
        String name="SYSTEM".equals(actorType)?"系统":identity(actorType,actorId,null).text();
        var snapshot=new LeadFlowSnapshot(1,key,title,actorType,actorId,name,result,reason,relatedType,relatedId,relatedNo,changes,List.copyOf(covered));
        var event=new BusinessEventDO();
        event.setAggregateType("lead"); event.setAggregateId(leadId); event.setEventType("lead_flow_operation");
        event.setOperatorUserId("ADMIN".equals(actorType)?actorId:null);
        var evidence=evidenceService.capture(leadId,relatedType,relatedId,title,newEvents,!before.exists());
        var refs=new LinkedHashMap<String,Object>(); refs.put("flowSnapshot",snapshot); refs.put("flowRemark",evidence.remark());
        event.setRelatedObjectRefs(JsonUtils.toJsonString(refs));
        event.setEvidenceRefs(JsonUtils.toJsonString(evidence.attachments()));
        event.setOccurredAt(LocalDateTime.now()); event.setIdempotencyKey(key);
        eventMapper.insert(event);
    }
    private static Value choice(String code,String label) {
        return code==null?Value.text(null):new Value("VALUE",code,label==null||label.isBlank()?"历史标签未记录":label,null,null);
    }
    private static String status(String s) { return lookup(s,Map.of("submitted","待判定","suspended","挂起","valid","有效","converted","有效","invalid","无效","won","已成交","closed","已关闭")); }
    private static String assignment(String s) { return lookup(s,Map.of("unassigned","未分配","pending_acceptance","待接单","owned","已归属","public_pool","抢单池","recycle_pending","回收待处理","closed","已关闭")); }
    private static String pool(String s) { return lookup(s,Map.of("waiting_assignment","待分配协作人","assigned","协作中","deal_pending","成交审批中","converted","已成交","exited","已退出")); }
    private static String source(String s) { return lookup(s,Map.of("partner","兼职","internal_new_media","新媒体","sales_self_sourced","销售自拓","education_self_sourced","教务自拓")); }
    private static String lookup(String s, Map<String,String> labels) { return s==null?null:labels.get(s); }
}
