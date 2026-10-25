package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowSnapshot;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.math.*;

@Service
public class DiagnosisMetricService {
    @Resource private DiagnosisMetricMapper metrics;
    @Resource private PartnerStudentLinkMapper links;
    @Resource private PartnerMapper partners;
    @Resource private MediaAccountProfileEntryMapper entries;
    @Resource private MediaAccountMapper accounts;
    @Resource private TenantFrameworkService tenants;
    @Resource private TransactionTemplate transactions;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(DiagnosisMetricService.class);
    public static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);}
    public static BigDecimal rate(long deals,long valid){return valid==0?null:BigDecimal.valueOf(deals).divide(BigDecimal.valueOf(valid),4,RoundingMode.HALF_UP);}
    private static long number(Map<String,Object> row,String key){return row.get(key)==null?0:((Number)row.get(key)).longValue();}
    /** Request-owned cache: never shares visibility or historical cutoffs between requests. */
    public static final class ReadContext {
        private final LocalDateTime now = DiagnosisMetricService.now();
        private final Map<CohortKey,List<Map<String,Object>>> cohorts = new HashMap<>();
        private final Map<LocalDateTime,Map<Long,Optional<String>>> states = new HashMap<>();
        private final Map<Long,Boolean> deals = new HashMap<>();
        private final Map<PublicationKey,Map<String,Object>> publications = new HashMap<>();
        private final Map<Long,List<MediaAccountProfileEntryDO>> likes = new HashMap<>();
    }
    private record CohortKey(Long tenant,Long partner,LocalDateTime from,LocalDateTime cutoff) {}
    private record PublicationKey(Long tenant,Long account,LocalDateTime to,LocalDateTime cutoff) {}
    public Metric read(MediaAccountDO account,String node,String round,Part part,Long likes, boolean persist){
        return read(account,node,round,part,likes,persist,new ReadContext());
    }
    public Metric read(MediaAccountDO account,String node,String round,Part part,Long likes,boolean persist,ReadContext context){
        Long tenant=TenantContextHolder.getRequiredTenantId();
        var rows=metrics.selectList(new LambdaQueryWrapper<DiagnosisMetricDO>().eq(DiagnosisMetricDO::getAccountId,account.getId())
                .eq(DiagnosisMetricDO::getNodeKey,node).eq(DiagnosisMetricDO::getIntervalDays,part.days()));
        DiagnosisMetricDO saved=rows.isEmpty()?null:rows.getFirst();
        if(persist && saved!=null) saved=metrics.lock(tenant,account.getId(),node,part.days());
        LocalDateTime now=context.now;
        if(saved!=null && !"OPEN".equals(saved.getState())) {
            Metric result=JsonUtils.parseObject(saved.getMetricJson(),Metric.class);
            result.setAsOf(now);
            if(result.getValidLeads()!=null) {long deals=metrics.deals(tenant,saved.getId(),now);result.setDeals(deals);result.setConversionRate(rate(deals,result.getValidLeads()));}
            if(likes!=null){
                applyLikes(result,account,round,part,likes,context);
                if(persist && part.days()==28){saved.setMetricJson(JsonUtils.toJsonString(result));metrics.updateById(saved);}
            }
            return result;
        }
        var result=new Metric();result.setAsOf(now);
        LocalDateTime cutoff=now.isBefore(part.to())?now:part.to();
        boolean ended=!now.isBefore(part.to());result.setState(ended?"SETTLED":"OPEN");
        if(part.days()==7 || part.days()==28){
            // Historical snapshots with nonstandard bounds retain the original query contract.
            boolean standard=part.from().equals(part.to().minusDays(part.days()));
            var counts=standard ? context.publications.computeIfAbsent(new PublicationKey(tenant,account.getId(),part.to(),cutoff),
                    key -> metrics.publicationPeriods(key.tenant(),key.account(),key.to().minusDays(7),key.to().minusDays(28),key.to(),key.cutoff()))
                    : metrics.publications(tenant,account.getId(),part.from(),part.to(),cutoff);
            result.setPublishedCount(number(counts,standard ? "published"+part.days() : "published"));result.setMissingPublishTimeCount(number(counts,"missing"));
        }
        Long partner=saved==null?null:saved.getPartnerId(),linkId=saved==null?null:saved.getPartnerLinkId();
        if(partner==null && account.getStudentPersonId()!=null){var link=links.selectActiveByStudent(account.getStudentPersonId());
            if(link!=null){partner=link.getPartnerId();linkId=link.getId();}}
        var memberIds=new ArrayList<Long>();
        if(part.days()>7){
            if(partner==null){result.setReason("学员未绑定兼职账号，暂无法统计");}
            else if(partners.selectById(partner)==null){result.setReason("绑定的兼职主体不存在或不可访问，请核实绑定关系");}
            else {
                var cohortKey = new CohortKey(tenant,partner,part.from(),cutoff);
                var cohort = context.cohorts.computeIfAbsent(cohortKey,
                        key -> metrics.cohort(key.tenant(),key.partner(),key.from(),key.cutoff()));
                var ids = cohort.stream().map(lead -> number(lead,"id")).distinct().toList();
                Map<Long,Optional<String>> states = ended ? historicalStatuses(tenant,ids,cutoff,context) : Map.of();
                boolean unknown=false;long deals=0;
                for(var lead:cohort){
                    Object deleted=lead.get("deleted");
                    if(Boolean.TRUE.equals(deleted) || deleted instanceof Number n && n.intValue()!=0){unknown=true;continue;}
                    long id=number(lead,"id");String status=ended?states.getOrDefault(id,Optional.empty()).orElse(null):Objects.toString(lead.get("status"),null);
                    if(status==null){unknown=true;continue;}
                    if(Set.of("valid","converted","won").contains(status)) memberIds.add(id);
                }
                if(unknown){result.setReason("历史状态证据不足，无法准确还原周期结束时的有效客资");memberIds.clear();}
                else {deals=dealCount(tenant,memberIds,context);result.setValidLeads((long)memberIds.size());result.setDeals(deals);result.setConversionRate(rate(deals,memberIds.size()));}
            }
        }
        if(ended){result.setSettledAt(now);if(result.getReason()!=null)result.setState("UNAVAILABLE");}
        if(saved!=null && ended && !persist){
            result.setState("OPEN");result.setSettledAt(null);
            String pending=saved.getFailureReason()==null?"周期已结束，等待后台结算":saved.getFailureReason();
            result.setReason(result.getReason()==null?pending:result.getReason()+"；"+pending);
        }
        applyLikes(result,account,round,part,likes,context);
        if(persist){
            if(saved==null){saved=new DiagnosisMetricDO();saved.setAccountId(account.getId());saved.setNodeKey(node);saved.setRoundKey(round);
                saved.setIntervalDays(part.days());saved.setIntervalCycle(part.cycle());saved.setPeriodFrom(part.from());saved.setPeriodTo(part.to());saved.setPartnerId(partner);saved.setPartnerLinkId(linkId);
                saved.setSubmissionSnapshotJson(JsonUtils.toJsonString(result));}
            saved.setState(result.getState());saved.setMetricJson(JsonUtils.toJsonString(result));saved.setSettledAt(result.getSettledAt());saved.setFailureReason(null);
            saved.setPartnerId(partner);saved.setPartnerLinkId(linkId);
            if(saved.getId()==null)metrics.insert(saved);else metrics.updateById(saved);
            if(ended && result.getValidLeads()!=null)for(var ids:chunks(memberIds)) metrics.members(tenant,saved.getId(),ids);
        }
        return result;
    }
    private static List<List<Long>> chunks(List<Long> ids) {
        var result=new ArrayList<List<Long>>();
        for(int start=0;start<ids.size();start+=500) result.add(ids.subList(start,Math.min(start+500,ids.size())));
        return result;
    }
    private Map<Long,Optional<String>> historicalStatuses(Long tenant,List<Long> ids,LocalDateTime cutoff,ReadContext context) {
        var states=context.states.computeIfAbsent(cutoff,key -> new HashMap<>());
        var missing=ids.stream().filter(id -> !states.containsKey(id)).toList();
        for(var batch:chunks(missing)) {
            for(var row:metrics.statusesBefore(tenant,batch,cutoff))
                states.put(number(row,"leadId"),Optional.ofNullable(Objects.toString(row.get("toStatus"),null)));
            var legacy=batch.stream().filter(id -> !states.containsKey(id)).toList();
            if(!legacy.isEmpty()) for(var row:metrics.flowsBefore(tenant,legacy,cutoff)) {
                long id=number(row,"leadId");
                if(states.containsKey(id)) continue;
                String state=historicalStatusFromFlow(Objects.toString(row.get("refs"),null));
                if(state!=null) states.put(id,Optional.of(state));
            }
            batch.forEach(id -> states.putIfAbsent(id,Optional.empty()));
        }
        return states;
    }
    private String historicalStatusFromFlow(String json) {
        var root=JsonUtils.parseObject(json,Map.class);
        if(root==null || root.get("flowSnapshot")==null)return null;
        var snapshot=JsonUtils.parseObject(JsonUtils.toJsonString(root.get("flowSnapshot")),LeadFlowSnapshot.class);
        if(snapshot.changes()==null)return null;
        for(var change:snapshot.changes())if("status".equals(change.field()) && change.after()!=null && "VALUE".equals(change.after().state()))return change.after().code();
        return null;
    }
    private long dealCount(Long tenant,List<Long> ids,ReadContext context) {
        var missing=ids.stream().filter(id -> !context.deals.containsKey(id)).toList();
        for(var batch:chunks(missing)) {
            var won=new HashSet<>(metrics.dealtLeadIds(tenant,batch,context.now));
            batch.forEach(id -> context.deals.put(id,won.contains(id)));
        }
        return ids.stream().filter(id -> Boolean.TRUE.equals(context.deals.get(id))).count();
    }
    private void applyLikes(Metric metric,MediaAccountDO account,String round,Part part,Long current,ReadContext context){
        if(part.days()!=28)return;
        var latest=new TreeMap<Integer,Long>();
        var history=context.likes.computeIfAbsent(account.getId(),id -> entries.selectList(new LambdaQueryWrapper<MediaAccountProfileEntryDO>().eq(MediaAccountProfileEntryDO::getAccountId,id)
                .eq(MediaAccountProfileEntryDO::getFieldKey,"diagnosis_7d").orderByAsc(MediaAccountProfileEntryDO::getId)));
        for(var entry:history){
            var body=JsonUtils.parseObject(entry.getContent(),Map.class);
            if(body==null || !Objects.equals(round,Objects.toString(body.get("roundKey"),null)) || !(body.get("highestLikes") instanceof Number value) || !(body.get("week") instanceof Number week))continue;
            int last=part.cycle()*4;
            if(week.intValue()>last-4 && week.intValue()<=last)latest.put(week.intValue(),value.longValue());
        }
        if(current!=null)latest.put(part.cycle()*4,current);
        metric.setLikesCoverage(latest.size());metric.setHighestLikes(latest.values().stream().max(Long::compareTo).orElse(null));
    }
    @Scheduled(cron="15 */5 * * * *",zone="Asia/Shanghai")
    public void settle(){
        for(Long tenant:tenants.getTenantIds())TenantUtils.execute(tenant,()->{
            for(var row:metrics.selectList(new LambdaQueryWrapper<DiagnosisMetricDO>().eq(DiagnosisMetricDO::getState,"OPEN").le(DiagnosisMetricDO::getPeriodTo,now()).last("LIMIT 200"))){
                try {transactions.executeWithoutResult(tx->{var account=accounts.selectByIdForUpdate(row.getAccountId(),tenant);
                    if(account!=null)read(account,row.getNodeKey(),row.getRoundKey(),new Part("diagnosis_"+row.getIntervalDays()+"d",row.getIntervalCycle(),row.getIntervalDays(),row.getPeriodFrom(),row.getPeriodTo(),null,null),null,true);});}
                catch(Exception error){LOG.error("Diagnosis settlement failed metricId={}",row.getId(),error);
                    var failure=new DiagnosisMetricDO();failure.setId(row.getId());failure.setFailureReason("周期结算失败，系统将重试");metrics.updateById(failure);}
            }
        });
    }
}
