package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class WeeklyDiagnosisService {
    @Resource private MediaAccountMapper accounts;
    @Resource private MediaAccountDiagnosisReminderService reminders;
    @Resource private MediaAccountProfileService profiles;
    @Resource private MediaAccountProfileEntryMapper entries;
    @Resource private BusinessTaskMapper tasks;
    @Resource private BusinessTaskCommandService commands;
    @Resource private DiagnosisMetricService metrics;
    @Resource private DictDataApi dicts;

    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public Overview overview(Long accountId,Long userId){
        var account=accounts.selectById(accountId);requireDirector(account,userId);
        var accountTasks=reminders.accountTasks(accountId,userId);
        // Deadline and availability reads do not need publication or lead statistics.
        return new Overview(accountTasks,pending(account,accountTasks));
    }

    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public List<Node> nodes(Long accountId,Long userId){
        var account=accounts.selectById(accountId);requireDirector(account,userId);
        return pending(account,userId).stream().map(node->withMetrics(account,node,null,false)).toList();
    }
    private void requireDirector(MediaAccountDO account,Long user){if(account==null || !Objects.equals(account.getDirectorUserId(),user))throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);}
    private List<Node> pending(MediaAccountDO account,Long user){
        return pending(account,reminders.accountTasks(account.getId(),user));
    }
    private List<Node> pending(MediaAccountDO account,List<DiagnosisTodo> accountTasks){
        var ctx=MediaAccountDiagnosisScheduler.context(account);
        if(ctx.get("anchorAt")==null)return List.of();
        var anchor=LocalDateTime.parse(ctx.get("anchorAt").toString()).toLocalDate().atStartOfDay();
        String round=Objects.toString(ctx.get("roundKey"));
        Map<LocalDateTime,List<DiagnosisTodo>> groups=new TreeMap<>();
        for(var task:accountTasks)groups.computeIfAbsent(task.dueAt(),k->new ArrayList<>()).add(task);
        var result=new ArrayList<Node>();
        for(var group:groups.entrySet()){
            int week=WeeklyDiagnosisPeriods.week(anchor,group.getKey());
            // The existing scheduler precreates 14/28-day tasks. Only expose the current weekly node and missed nodes.
            var start=group.getKey().minusDays(7);
            if(start.isAfter(DiagnosisMetricService.now()))continue;
            var parts=new ArrayList<Part>();
            for(int days:WeeklyDiagnosisPeriods.intervals(week)){
                var task=group.getValue().stream().filter(t->t.templateType().equals("diagnosis_"+days+"d")).findFirst().orElse(null);
                if(task!=null)parts.add(new Part(task.templateType(),task.cycle(),days,group.getKey().minusDays(days),group.getKey(),task.taskId(),null));
            }
            if(!parts.isEmpty())result.add(new Node(WeeklyDiagnosisPeriods.key(account.getId(),round,group.getKey()),round,week,group.getKey(),parts,parts.stream().map(Part::taskId).toList()));
        }
        return result;
    }
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public Preview preview(Long accountId,String nodeKey,Long likes,Long user){
        var account=accounts.selectById(accountId);requireDirector(account,user);
        var node=pending(account,user).stream().filter(n->n.key().equals(nodeKey)).findFirst().orElseThrow(()->exception(MEDIA_ACCOUNT_DIAGNOSIS_TASK_UNAVAILABLE));
        node=withMetrics(account,node,likes,false);return new Preview(node,token(node));
    }
    private Node withMetrics(MediaAccountDO account,Node node,Long likes,boolean persist){
        return withMetrics(account,node,likes,persist,new DiagnosisMetricService.ReadContext());
    }
    private Node withMetrics(MediaAccountDO account,Node node,Long likes,boolean persist,DiagnosisMetricService.ReadContext context){
        return new Node(node.key(),node.roundKey(),node.week(),node.dueAt(),node.parts().stream().map(p->new Part(p.templateType(),p.cycle(),p.days(),p.from(),p.to(),p.taskId(),metrics.read(account,node.key(),node.roundKey(),p,likes,persist,context))).toList(),node.taskIds());
    }
    private String token(Node node){
        var stable=node.parts().stream().map(p->{var m=p.metric();return Arrays.asList(p.days(),m.getState(),m.getPublishedCount(),m.getMissingPublishTimeCount(),m.getValidLeads(),m.getDeals(),m.getReason());}).toList();
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(stable));
    }
    private String label(String value){
        dicts.validateDictDataList("infra_boolean_string",List.of(value));
        return dicts.getDictDataList("infra_boolean_string").stream().filter(d->value.equals(d.getValue())).map(d->d.getLabel()).findFirst().orElseThrow(()->exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="edit")
    public Integer submit(Long accountId,Submit request,Long user){
        var account=accounts.selectByIdForUpdate(accountId,TenantContextHolder.getRequiredTenantId());requireDirector(account,user);
        var common=request.getCommon();
        String fingerprint=cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        var replay=entries.replay(accountId,user,common.getIdempotencyKey());
        if(replay!=null){
            var saved=JsonUtils.parseObject(replay.getContent(),Map.class);
            if(!Objects.equals(saved.get("weeklyFingerprint"),fingerprint))throw exception(MEDIA_ACCOUNT_PROFILE_IDEMPOTENCY_CONFLICT);
            return replay.getResultVersion();
        }
        Node node;
        Map<String,Object> previousContent=Map.of();
        if(common.getPreviousEntryId()!=null){
            var prior=entries.selectById(common.getPreviousEntryId());
            if(prior==null || !Objects.equals(prior.getAccountId(),accountId))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            var content=JsonUtils.parseObject(prior.getContent(),Map.class);
            previousContent=content;
            if(!request.getNodeKey().equals(content.get("nodeKey")) || content.get("weeklyNode")==null)throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            node=JsonUtils.parseObject(JsonUtils.toJsonString(content.get("weeklyNode")),Node.class);
            // A revision of an older weekly node must not overwrite newer account state.
            var latest=entries.latestDiagnosis(accountId);
            var newest=JsonUtils.parseObject(latest.getContent(),Map.class);
            if(!request.getNodeKey().equals(newest.get("nodeKey")))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }else node=pending(account,user).stream().filter(n->n.key().equals(request.getNodeKey())).findFirst().orElseThrow(()->exception(MEDIA_ACCOUNT_DIAGNOSIS_TASK_UNAVAILABLE));
        var metricContext = new DiagnosisMetricService.ReadContext();
        var preview=withMetrics(account,node,request.getHighestLikes(),false,metricContext);
        if(node.parts().stream().anyMatch(p->p.days()==7) && request.getHighestLikes()==null)throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        if(!token(preview).equals(request.getMetricToken()))throw exception(MEDIA_ACCOUNT_DIAGNOSIS_METRICS_CHANGED);
        String changeLabel=Objects.equals(previousContent.get("changeDirection"),request.getChangeDirection()) && previousContent.get("changeDirectionLabel") instanceof String saved ? saved : label(request.getChangeDirection());
        String trafficLabel=Objects.equals(previousContent.get("trafficNormal"),request.getTrafficNormal()) && previousContent.get("trafficNormalLabel") instanceof String saved ? saved : label(request.getTrafficNormal());
        for(var part:node.parts())if(common.getPreviousEntryId()==null){
            var task=tasks.selectByIdForUpdate(part.taskId(),TenantContextHolder.getRequiredTenantId());
            if(task==null || !"pending".equals(task.getStatus()) || !Objects.equals(task.getAssigneeId(),user) || !Objects.equals(task.getBizId(),accountId))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
        Part primary=node.parts().getFirst();
        if(common.getPreviousEntryId()!=null){
            var latestPrimary=entries.latestField(accountId,primary.templateType().equals("diagnosis_28d")?"adjustment_28d":primary.templateType());
            if(latestPrimary==null || !request.getNodeKey().equals(JsonUtils.parseObject(latestPrimary.getContent(),Map.class).get("nodeKey")))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            common.setPreviousEntryId(latestPrimary.getId());
        }
        common.setTaskId(primary.taskId());common.setCycle(primary.cycle());common.setTemplateType(primary.templateType());
        // The existing service validates dictionaries, startup, account/config version and updates current state exactly once.
        Integer version=profiles.submitDiagnosis(accountId,common,user);
        var primaryEntry=entries.replay(accountId,user,common.getIdempotencyKey());
        node=withMetrics(account,node,request.getHighestLikes(),true,metricContext);
        var content=JsonUtils.parseObject(primaryEntry.getContent(),Map.class);
        content.put("nodeKey",node.key());content.put("roundKey",node.roundKey());content.put("week",node.week());content.put("weeklyNode",node);
        content.put("weeklyFingerprint",fingerprint);content.put("changeDirection",request.getChangeDirection());content.put("changeDirectionLabel",changeLabel);
        content.put("trafficNormal",request.getTrafficNormal());content.put("trafficNormalLabel",trafficLabel);content.put("directionSummary",request.getDirectionSummary());content.put("highestLikes",request.getHighestLikes());
        primaryEntry.setContent(JsonUtils.toJsonString(content));entries.updateById(primaryEntry);
        for(int i=1;i<node.parts().size();i++){
            var part=node.parts().get(i);
            var copy=new MediaAccountProfileEntryDO();copy.setAccountId(accountId);copy.setKind("DIAGNOSIS");copy.setFieldKey(part.templateType());copy.setTitle("周期综合诊断");
            content.put("cycle",part.cycle());content.put("templateType",part.templateType());
            copy.setContent(JsonUtils.toJsonString(content));copy.setSnapshotJson(primaryEntry.getSnapshotJson());copy.setResultVersion(version);copy.setOperatedByUserId(user);copy.setOperatedByName(primaryEntry.getOperatedByName());
            copy.setIdempotencyKey(cn.hutool.crypto.digest.DigestUtil.sha256Hex(common.getIdempotencyKey()+":"+part.days()));copy.setFingerprint(fingerprint);entries.insert(copy);
            if(common.getPreviousEntryId()==null && !commands.completeByKey(tasks.selectById(part.taskId()).getIdempotencyKey(),DiagnosisMetricService.now()))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
        return version;
    }
    @ZsjosPermission(bizType="media-account",bizId="#accountId",action="read")
    public Preview history(Long accountId,Long entryId,Long likes,Long user){
        // The service object check already enforces read access; metrics do not need the full profile projection.
        var account=accounts.selectById(accountId);
        if(account==null)throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        var entry=entries.selectById(entryId);
        if(entry==null || !Objects.equals(entry.getAccountId(),accountId))throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        var content=JsonUtils.parseObject(entry.getContent(),Map.class);
        if(content.get("weeklyNode")==null)throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        var node=JsonUtils.parseObject(JsonUtils.toJsonString(content.get("weeklyNode")),Node.class);
        node=withMetrics(account,node,likes,false);
        return new Preview(node,token(node));
    }
}
