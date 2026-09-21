package cn.iocoder.yudao.module.zsjos.service.account;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MediaAccountProfileService {
    @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @Resource private cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService businessTaskCommandService;
    @Resource private MediaAccountMapper mapper;
    @Resource private MediaAccountDiagnosisReminderService diagnosisReminders;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper diagnosisTasks;
    @Resource private MediaAccountProfileEntryMapper entries;
    @Resource private MediaAccountFieldConfigService configs;
    @Resource private MediaAccountService accounts;
    @Resource private MediaAccountObjectPermissionProvider objects;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi users;
    @Resource private PersonMapper people;
    @Resource private FileApi fileApi;
    @Resource private PartnerStudentLinkMapper partnerLinks;
    @Resource private PartnerMapper partners;
    @Resource private SalesOrderMapper orders;
    @Resource private PositioningCardSubmissionMapper positioningSubmissions;
    @Resource private cn.iocoder.yudao.module.system.api.dict.DictDataApi diagnosisDicts;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper leadMapper;

    @ZsjosPermission(bizType="media-account",bizId="#id",action="read")
    public MediaAccountProfileVO get(Long id, Long userId) {
        MediaAccountDO a=accounts.require(id);
        var config=configs.getPublished();
        MediaAccountProfileVO result=new MediaAccountProfileVO();
        result.setAccount(accounts.get(id,userId)); result.setConfig(config);
        Map<String,Object> values=readValues(a);
        Map<String,String> notes=new LinkedHashMap<>();
        var person=a.getStudentPersonId()==null?null:people.selectById(a.getStudentPersonId());
        result.setStudentName(person==null?null:person.getName()); result.setCurrentUserName(userName(userId));
        result.setDirectorName(userName(a.getDirectorUserId())); result.setOperatorName(userName(a.getOwnerOperatorUserId()));
        var metrics = partnerMetrics(a.getStudentPersonId());
        result.setPartnerMetrics(metrics);
        for(var f:config.getFields()) {
            if(!"AUTO".equals(f.getOwnerType()) && !Set.of("account_position", "professional_position", "content_format").contains(f.getKey())) continue;
            Object value=switch(f.getKey()) {
                case "account_no" -> a.getAccountNo();
                case "student_name" -> person==null?null:person.getName();
                case "contact" -> person==null?null:person.getMobile();
                case "total_leads" -> metrics.getTotalLeads();
                case "month_leads" -> metrics.getMonthLeads();
                case "total_conversion" -> metrics.getPartnerId()==null?null:metrics.getTotalDealRate();
                case "month_conversion" -> metrics.getPartnerId()==null?null:metrics.getMonthDealRate();
                case "total_amount" -> metrics.getPartnerId()==null?null:metrics.getTotalDealAmount();
                case "month_amount" -> metrics.getPartnerId()==null?null:metrics.getMonthDealAmount();
                case "accompany_days" -> effectiveAccompanyDays(a);
                case "position_rounds" -> appliedPositioning(a).rounds();
                case "account_position", "professional_position", "content_format" -> appliedPositioning(a).values().get(f.getKey());
                case "delivery_goals" -> values.get(f.getKey());
                default -> null;
            };
            values.remove(f.getKey()); if(value!=null)values.put(f.getKey(),value);
            notes.put(f.getKey(),value==null?automaticMissingNote(f.getKey()):
                "student_name".equals(f.getKey())||"contact".equals(f.getKey())?"当前学员档案":"账号已保存的业务数据与标签快照");
            if (Set.of("account_position", "professional_position", "content_format", "position_rounds").contains(f.getKey())) {
                var applied = appliedPositioning(a);
                notes.put(f.getKey(), applied.submission() == null ? "尚未应用定位卡" : "当前应用定位卡，第 " + applied.submission().getSubmissionNo() + " 次提交");
            } else if ("accompany_days".equals(f.getKey())) {
                notes.put(f.getKey(), a.getCreateTime() == null ? "缺少账号创建时间" : "账号创建日起算，扣除有效暂停天数");
            }
            if (Set.of("total_leads","month_leads","total_conversion","month_conversion","total_amount","month_amount").contains(f.getKey())) {
                notes.put(f.getKey(), metrics.getPartnerId()==null ? "学员尚未绑定本系统兼职账号" :
                    switch(f.getKey()) {
                        case "total_leads" -> "绑定兼职账号累计提交的有效客资数";
                        case "month_leads" -> "北京时间本月提交的有效客资数";
                        case "total_conversion" -> "累计有效客资中已成交客资占比；按客资去重";
                        case "month_conversion" -> "本月提交的有效客资中已成交客资占比；按客资去重";
                        case "total_amount" -> "绑定兼职账号已生效订单成交总额；不扣退款";
                        default -> "北京时间本月生效订单成交总额；不扣退款";
                    });
            }
        }
        var diagnosis = entries.latestDiagnosis(id);
        boolean started = diagnosis != null;
        List.of("stage", "current_status", "bottleneck").forEach(values::remove);
        result.setDiagnosisStarted(started);
        if (started) {
            if (a.getSStage() != null) values.put("stage", a.getSStage());
            if (a.getCurrentStatusValue() != null) values.put("current_status", a.getCurrentStatusValue());
            if (a.getPrimaryProblemCodeValue() != null) values.put("bottleneck", a.getPrimaryProblemCodeValue());
        }
        for (String key : List.of("stage", "current_status", "bottleneck"))
            notes.put(key, started ? ("diagnosis_initial".equals(diagnosis.getFieldKey()) ? "启动诊断" : "最新周期诊断") : "待完成启动诊断");
        var snapshots = new ArrayList<>(readSnapshots(a));
        snapshots.removeIf(item -> Set.of("stage", "current_status", "bottleneck").contains(item.getKey()));
        if (started && diagnosis.getSnapshotJson() != null)
            snapshots.addAll(JsonUtils.parseArray(diagnosis.getSnapshotJson(), MediaAccountDetailSnapshotVO.class));
        Map<String, Object> requirements = new LinkedHashMap<>();
        requirements.put("diagnosis_7d", values.get("diagnosis_7d_requirement"));
        requirements.put("diagnosis_14d", values.get("diagnosis_14d_requirement"));
        requirements.put("diagnosis_28d", values.get("diagnosis_28d_requirement"));
        requirements.put("student_commitments", values.get("student_commitments"));
        requirements.put("company_commitments", values.get("company_commitments"));
        requirements.put("delivery_goals", values.get("delivery_goals"));
        result.setPositioningRequirements(requirements);
        result.setDiagnosisContext(cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSource.parse(values.get("_diagnosisContext") instanceof String json ? json : null));
        result.setValues(values);result.setSourceNotes(notes);result.setSnapshots(snapshots);
        boolean write=canMaintain(a,userId);
        boolean director = write && Objects.equals(a.getDirectorUserId(), userId);
        result.setCanSubmitDiagnosis(director && started);
        result.setCanStartDiagnosis(director && !started && appliedPositioning(a).submission() != null);
        result.setEditableFields(config.getFields().stream().filter(f->write&&MediaAccountFieldPolicy.canWrite(f,a,userId)).map(f->f.getKey()).toList());
        var missing=config.getFields().stream().filter(f->Boolean.TRUE.equals(f.getEnabled())&&Boolean.TRUE.equals(f.getRequiredForComplete())
            && !MediaAccountFieldPolicy.POSITIONING_SYNC_FIELDS.contains(f.getKey())
            && Set.of("DIRECTOR","OPERATOR").contains(f.getOwnerType())&&!"record".equals(f.getType())&&MediaAccountFieldPolicy.empty(values.get(f.getKey()))).toList();
        result.setMissingFields(missing.stream().map(f->f.getKey()).toList());
        result.setMissingByOwner(Map.of("DIRECTOR",(int)missing.stream().filter(f->"DIRECTOR".equals(f.getOwnerType())).count(),
            "OPERATOR",(int)missing.stream().filter(f->"OPERATOR".equals(f.getOwnerType())).count()));
        result.setCanViewHistory(permissionApi.hasAnyPermissions(userId,"zsjos:media-account:query","zsjos:media-account:maintenance"));
        Map<String,FileVO> files=new LinkedHashMap<>();
        for(var f:config.getFields())if("image".equals(f.getType())&&values.get(f.getKey()) instanceof Number number) {
            files.put(f.getKey(),storedFile(number.longValue(),id));
        }
        for(var f:config.getFields())if("attachment".equals(f.getType())&&values.get(f.getKey()) instanceof Collection<?> ids) {
            for(Object fileId:ids)if(fileId instanceof Number n) files.put(String.valueOf(n.longValue()),storedFile(n.longValue(),id));
        }
        result.setFiles(files);
        Map<String,Entry> latestRecords = new LinkedHashMap<>();
        for (var field : config.getFields()) if ("record".equals(field.getType()) && Boolean.TRUE.equals(field.getEnabled())) {
            var latestEntry = entries.latestField(id, field.getKey());
            if (latestEntry != null) latestRecords.put(field.getKey(), historyEntry(latestEntry, id));
        }
        result.setLatestRecords(latestRecords); return result;
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#id",action="edit")
    public Integer patch(Long id, Patch req, Long userId) {
        MediaAccountDO a=lock(id);requireMaintain(a,userId);
        var config=configs.getPublished();
        MediaAccountFieldPolicy.validate(config.getFields(),req.getChanges(),a,userId);
        String fingerprint=fingerprint("PROFILE",req.getVersion(),req.getConfigVersionId(),req.getChanges());
        Integer replay=replay(id,userId,req.getIdempotencyKey(),fingerprint);if(replay!=null)return replay;
        requireVersion(a,req.getVersion(),req.getConfigVersionId(),config.getId());
        Map<String,Object> values=readValues(a);values.putAll(req.getChanges());
        Set<String> enabled=new HashSet<>();config.getFields().stream().filter(f->Boolean.TRUE.equals(f.getEnabled())).forEach(f->enabled.add(f.getKey()));
        Map<String,Object> active=new LinkedHashMap<>();values.forEach((key,value)->{if(enabled.contains(key))active.put(key,value);});
        for(var f:config.getFields())if(req.getChanges().containsKey(f.getKey())&&"image".equals(f.getType())&&req.getChanges().get(f.getKey())!=null) {
            Object value=req.getChanges().get(f.getKey());if(!(value instanceof Number number))throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            file(number.longValue(),id,userId);
        }
        for(var f:config.getFields())if(req.getChanges().containsKey(f.getKey())&&"attachment".equals(f.getType())&&req.getChanges().get(f.getKey())!=null) {
            Object raw=req.getChanges().get(f.getKey());
            if(!(raw instanceof Collection<?> ids)||ids.size()>20)throw exception(MEDIA_ACCOUNT_ATTACHMENT_INVALID);
            Collection<?> previous=readValues(a).get(f.getKey()) instanceof Collection<?> list?list:List.of();
            Set<String> existing=previous.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
            for(Object fileId:ids) {
                if(!(fileId instanceof Number n)||n.longValue()<=0||n.doubleValue()!=n.longValue())throw exception(MEDIA_ACCOUNT_ATTACHMENT_INVALID);
                file(n.longValue(),id,existing.contains(String.valueOf(n.longValue()))?null:userId);
            }
        }
        var saved=configs.validateAndSnapshot(active,readSnapshots(a));
        if (!Objects.equals(saved.configVersionId(), config.getId())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        for (String key : List.of("nickname", "uid", "platform")) {
            Object value = saved.values().get(key);
            if (req.getChanges().containsKey(key) && value != null && (!(value instanceof String text)
                    || text.length() > ("platform".equals(key) ? 100 : 255))) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        Map<String,Object> merged=new LinkedHashMap<>(values);enabled.forEach(merged::remove);merged.putAll(saved.values());
        List<MediaAccountDetailSnapshotVO> snapshots=new ArrayList<>(saved.snapshots());
        readSnapshots(a).stream().filter(s->!enabled.contains(s.getKey())).forEach(snapshots::add);
        a.setDetailConfigVersionId(saved.configVersionId()).setDetailValuesJson(JsonUtils.toJsonString(merged)).setDetailSnapshotJson(JsonUtils.toJsonString(snapshots));
        if(req.getChanges().containsKey("nickname"))a.setNickname((String)saved.values().get("nickname"));
        if(req.getChanges().containsKey("uid"))a.setPlatformAccountId((String)saved.values().get("uid"));
        if(req.getChanges().containsKey("platform")) {
            a.setPlatformValue((String)saved.values().get("platform"));
            a.setPlatformLabelSnapshot(saved.snapshots().stream().filter(s->"platform".equals(s.getKey())).map(MediaAccountDetailSnapshotVO::getDisplayValue).findFirst().orElse(null));
        }
        if(mapper.update(null,new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,req.getVersion())
            .set(MediaAccountDO::getDetailConfigVersionId,a.getDetailConfigVersionId()).set(MediaAccountDO::getDetailValuesJson,a.getDetailValuesJson())
            .set(MediaAccountDO::getDetailSnapshotJson,a.getDetailSnapshotJson()).set(MediaAccountDO::getNickname,a.getNickname())
            .set(MediaAccountDO::getPlatformAccountId,a.getPlatformAccountId()).set(MediaAccountDO::getPlatformValue,a.getPlatformValue())
            .set(MediaAccountDO::getPlatformLabelSnapshot,a.getPlatformLabelSnapshot()).set(MediaAccountDO::getVersion,req.getVersion()+1))!=1)throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        var entry=entry(id,userId,req.getIdempotencyKey(),fingerprint,req.getVersion()+1,"PROFILE",null,"账号资料维护",null);
        // Store the complete resulting snapshot, including disabled historical fields, for reproducible history.
        entry.setSnapshotJson(JsonUtils.toJsonString(snapshots));entries.insert(entry);return entry.getResultVersion();
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#id",action="edit")
    public Integer append(Long id, RecordRequest req, Long userId) {
        if (Set.of("diagnosis_7d", "diagnosis_14d", "diagnosis_28d", "adjustment_28d", "diagnosis_initial").contains(req.getFieldKey())
                || req.getFieldKey().startsWith("delivery_s")) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        MediaAccountDO a=lock(id);requireMaintain(a,userId);var config=configs.getPublished();
        var f=config.getFields().stream().filter(x->x.getKey().equals(req.getFieldKey())&&"record".equals(x.getType())).findFirst().orElseThrow(()->exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
        if(!MediaAccountFieldPolicy.canWrite(f,a,userId))throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        List<Long> ids=req.getFileIds()==null?List.of():req.getFileIds().stream().distinct().toList();
        String content=req.getContent()==null?"":req.getContent().trim();
        if(content.isEmpty()&&ids.isEmpty())throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        String fp=fingerprint("RECORD",req.getVersion(),req.getConfigVersionId(),Arrays.asList(req.getFieldKey(),content,ids));
        Integer replay=replay(id,userId,req.getIdempotencyKey(),fp);if(replay!=null)return replay;
        requireVersion(a,req.getVersion(),req.getConfigVersionId(),config.getId());
        List<FileVO> files=ids.stream().map(fileId->file(fileId,id,userId)).toList();
        if(mapper.update(null,new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id).eq(MediaAccountDO::getVersion,req.getVersion())
            .set(MediaAccountDO::getVersion,req.getVersion()+1))!=1)throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        var entry=entry(id,userId,req.getIdempotencyKey(),fp,req.getVersion()+1,"RECORD",f.getKey(),f.getLabel(),content);
        entry.setFilesJson(JsonUtils.toJsonString(files));entries.insert(entry);return entry.getResultVersion();
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#id",action="edit")
    public Integer submitDiagnosis(Long id, DiagnosisRequest req, Long userId) {
        if ("adjustment_28d".equals(req.getTemplateType())) req.setTemplateType("diagnosis_28d");
        boolean initial = "diagnosis_initial".equals(req.getTemplateType());
        if (req.getTemplateType() == null || !Set.of("diagnosis_initial", "diagnosis_7d", "diagnosis_14d", "diagnosis_28d", "adjustment_28d").contains(req.getTemplateType()))
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        var account = lock(id);
        requireMaintain(account, userId);
        if (!Objects.equals(account.getDirectorUserId(), userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        String fp = fingerprint("DIAGNOSIS", req.getVersion(), req.getConfigVersionId(), req);
        Integer replay = replay(id, userId, req.getIdempotencyKey(), fp);
        // A retry must not restore an older diagnosis over a newer one.
        if (replay != null) return replay;
        requireVersion(account, req.getVersion(), req.getConfigVersionId(), configs.getPublished().getId());
        Map<String, Object> previousContent = Map.of();
        Map<String,Object> frozenRequirement = null;
        Object frozenSource = null;
        String completionKey = null;
        if (req.getPreviousEntryId() != null) {
            String field = "diagnosis_28d".equals(req.getTemplateType()) ? "adjustment_28d" : req.getTemplateType();
            var previous = entries.latestField(id, field);
            if (previous == null || !Objects.equals(previous.getId(), req.getPreviousEntryId()) || !"DIAGNOSIS".equals(previous.getKind())) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            var old = cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSource.parse(previous.getContent());
            previousContent = old;
            if (!Objects.equals(old.get("cycle"), req.getCycle()) || (old.get("templateType") != null && !Objects.equals(old.get("templateType"), req.getTemplateType()))) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            frozenRequirement = (Map<String,Object>)old.get("requirementSnapshot"); frozenSource = old.get("requirementSource");
        } else if (!initial) {
            var todo = diagnosisReminders.accountTasks(id,userId).stream()
                    .filter(t -> Objects.equals(t.templateType(), req.getTemplateType()) && Objects.equals(t.cycle(),req.getCycle())
                            && (req.getTaskId() == null || Objects.equals(t.taskId(),req.getTaskId())))
                    .findFirst().orElseThrow(() -> exception(MEDIA_ACCOUNT_DIAGNOSIS_TASK_UNAVAILABLE));
            var task = diagnosisTasks.selectByIdForUpdate(todo.taskId(),TenantContextHolder.getRequiredTenantId());
            if (task == null || !"pending".equals(task.getStatus()) || !Objects.equals(task.getBizId(),id) || !Objects.equals(task.getAssigneeId(),userId)) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            completionKey = task.getIdempotencyKey();
            frozenRequirement = (Map<String,Object>)todo.payload().get("requirementSnapshot"); frozenSource = todo.payload().get("source");
        }
        var latest = entries.latestDiagnosis(id);
        if (initial && latest != null) throw exception(MEDIA_ACCOUNT_DIAGNOSIS_ALREADY_STARTED);
        if (initial && appliedPositioning(account).submission() == null) throw exception(MEDIA_ACCOUNT_DIAGNOSIS_POSITIONING_REQUIRED);
        if (!initial && latest == null) throw exception(MEDIA_ACCOUNT_DIAGNOSIS_INITIAL_REQUIRED);
        if (initial && !Objects.equals(req.getCycle(), 0)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        if (!initial && (req.getCycle() == null || req.getCycle() < 1
                || java.util.stream.Stream.of(req.getCooperationLevel(), req.getCooperationEvidence(), req.getSecondaryProblem(),
                    req.getSecondaryProblemEvidence(), req.getImprovementMeasures(), req.getObservedData()).anyMatch(v -> v == null || v.isBlank())))
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        boolean hasSecondary = req.getSecondaryProblem() != null && !req.getSecondaryProblem().isBlank();
        boolean hasSecondaryEvidence = req.getSecondaryProblemEvidence() != null && !req.getSecondaryProblemEvidence().isBlank();
        if (initial && hasSecondary != hasSecondaryEvidence) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        String stageLabel = diagnosisLabel("zsjos_media_account_stage", req.getCurrentStage());
        String statusLabel = diagnosisLabel("zsjos_media_account_current_status", req.getAccountStatus());
        String problemLabel = diagnosisLabel("zsjos_media_account_primary_problem", req.getPrimaryProblem());
        String cooperationLabel = initial ? null : diagnosisLabel("zsjos_media_account_cooperation_level", req.getCooperationLevel());
        String secondaryLabel = null;
        if (hasSecondary) {
            // An unchanged historical selection keeps its original meaning after dictionary edits.
            secondaryLabel = Objects.equals(previousContent.get("secondaryProblem"), req.getSecondaryProblem())
                    && previousContent.get("secondaryProblemLabel") instanceof String savedLabel ? savedLabel
                    : diagnosisLabel("zsjos_media_account_primary_problem", req.getSecondaryProblem());
        }
        var snapshots = new ArrayList<MediaAccountDetailSnapshotVO>();
        snapshots.add(diagnosisSnapshot("stage", "当前期段", req.getCurrentStage(), stageLabel));
        snapshots.add(diagnosisSnapshot("current_status", "账号状态", req.getAccountStatus(), statusLabel));
        snapshots.add(diagnosisSnapshot("bottleneck", "当前瓶颈", req.getPrimaryProblem(), problemLabel));
        if (mapper.update(null, new LambdaUpdateWrapper<MediaAccountDO>().eq(MediaAccountDO::getId,id)
                .eq(MediaAccountDO::getVersion,req.getVersion()).set(MediaAccountDO::getVersion,req.getVersion()+1)
                .set(MediaAccountDO::getSStage,req.getCurrentStage()).set(MediaAccountDO::getSStageLabelSnapshot,stageLabel)
                .set(MediaAccountDO::getCurrentStatusValue,req.getAccountStatus()).set(MediaAccountDO::getCurrentStatusLabelSnapshot,statusLabel)
                .set(MediaAccountDO::getPrimaryProblemCodeValue,req.getPrimaryProblem()).set(MediaAccountDO::getPrimaryProblemCodeLabelSnapshot,problemLabel)) != 1)
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        Map<String,Object> content = JsonUtils.parseObject(JsonUtils.toJsonString(req), Map.class);
        Map<String, Object> positioning = readValues(account);
        Map<String, Object> requirementSnapshot = new LinkedHashMap<>();
        requirementSnapshot.put("diagnosis_7d", positioning.get("diagnosis_7d_requirement"));
        requirementSnapshot.put("diagnosis_14d", positioning.get("diagnosis_14d_requirement"));
        requirementSnapshot.put("diagnosis_28d", positioning.get("diagnosis_28d_requirement"));
        content.put("requirementSnapshot", initial ? requirementSnapshot : frozenRequirement);
        content.put("requirementSource", initial ? MediaAccountDiagnosisScheduler.context(account) : frozenSource);
        content.put("currentStageLabel", stageLabel); content.put("accountStatusLabel", statusLabel); content.put("primaryProblemLabel", problemLabel);
        if (!initial) {
            content.put("cooperationLevelLabel", cooperationLabel);
        }
        if (hasSecondary) content.put("secondaryProblemLabel", secondaryLabel);
        var entry = entry(id,userId,req.getIdempotencyKey(),fp,req.getVersion()+1,"DIAGNOSIS",req.getTemplateType(),
                initial ? "启动诊断" : "周期诊断",JsonUtils.toJsonString(content));
        entry.setSnapshotJson(JsonUtils.toJsonString(snapshots)); entries.insert(entry);
        if (completionKey != null && !businessTaskCommandService.completeByKey(completionKey, java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))))
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        collaborationNotify.account(MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED, account, userId,
                "diagnosis-completed:" + id + ":" + req.getIdempotencyKey(), Map.of("cycle", req.getCycle()));
        return req.getVersion()+1;
    }

    private String diagnosisLabel(String type, String value) {
        if (value == null || value.isBlank()) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        diagnosisDicts.validateDictDataList(type, List.of(value));
        return diagnosisDicts.getDictDataList(type).stream().filter(d -> value.equals(d.getValue()))
                .map(d -> d.getLabel()).findFirst().orElseThrow(() -> exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
    }

    private MediaAccountDetailSnapshotVO diagnosisSnapshot(String key, String label, String value, String display) {
        var item = new MediaAccountDetailSnapshotVO();
        item.setKey(key); item.setLabel(label); item.setValue(value); item.setDisplayValue(display); item.setOwnerType("AUTO"); item.setType("text");
        return item;
    }

    private record AppliedPositioning(PositioningCardSubmissionDO submission, Map<String, Object> values) {
        Integer rounds() { return submission == null ? null : submission.getSubmissionNo(); }
    }

    private AppliedPositioning appliedPositioning(MediaAccountDO account) {
        var submission = positioningSubmissions.selectCurrentConfirmedByAccount(account.getId());
        if (submission == null || account.getStudentPersonId() == null || account.getCreateServiceRelationId() == null
                || !Objects.equals(TenantContextHolder.getRequiredTenantId(), submission.getTenantId())
                || !Objects.equals(account.getStudentPersonId(), submission.getStudentPersonId())
                || !Objects.equals(account.getCreateServiceRelationId(), submission.getServiceRelationId()))
            return new AppliedPositioning(null, Map.of());
        Map<String, Object> raw = JsonUtils.parseObject(submission.getValuesSnapshotJson(), Map.class);
        Map<String, Object> dict = JsonUtils.parseObject(submission.getDictSnapshotJson(), Map.class);
        if (raw == null) raw = Map.of();
        if (dict == null) dict = Map.of();
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("account_position", historicalDisplay(raw.get("pc_account_position"), dict.get("pc_account_position")));
        projected.put("professional_position", historicalDisplay(raw.get("pc_profession"), dict.get("pc_profession")));
        projected.put("content_format", historicalDisplay(raw.get("pc_content_form"), dict.get("pc_content_form")));
        projected.values().removeIf(Objects::isNull);
        return new AppliedPositioning(submission, projected);
    }

    private String historicalDisplay(Object value, Object snapshot) {
        if (snapshot instanceof Collection<?> list && !list.isEmpty()) {
            List<String> labels = list.stream().map(this::snapshotLabel).filter(Objects::nonNull).toList();
            if (!labels.isEmpty()) return String.join("、", labels);
        }
        if (snapshot instanceof Map<?, ?> map) {
            String label = snapshotLabel(map);
            if (label != null) return label;
        }
        if (value instanceof Collection<?> list) return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("、"));
        return value == null ? null : String.valueOf(value);
    }

    private String snapshotLabel(Object value) {
        if (!(value instanceof Map<?, ?> map)) return value == null ? null : String.valueOf(value);
        Object label = map.containsKey("label") ? map.get("label") : map.get("labelSnapshot");
        return label == null ? null : String.valueOf(label);
    }

    private Long effectiveAccompanyDays(MediaAccountDO account) {
        if (account.getCreateTime() == null) return null;
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var anchor = account.getCreateTime().toLocalDate();
        long days = java.time.temporal.ChronoUnit.DAYS.between(anchor, today) + 1;
        if (account.getMaintenanceStartDate() != null && account.getMaintenanceEndDate() != null
                && !account.getMaintenanceEndDate().isBefore(account.getMaintenanceStartDate())) {
            var end = today.isBefore(account.getMaintenanceEndDate()) ? today : account.getMaintenanceEndDate();
            if (!end.isBefore(account.getMaintenanceStartDate()))
                days -= java.time.temporal.ChronoUnit.DAYS.between(account.getMaintenanceStartDate(), end) + 1;
        }
        return Math.max(days, 0);
    }

    private String automaticMissingNote(String key) {
        return switch (key) {
            case "accompany_days" -> "缺少账号创建时间";
            case "position_rounds", "account_position", "professional_position", "content_format" -> "尚未应用定位卡";
            case "stage", "current_status", "bottleneck" -> "等待最新周期诊断";
            default -> "等待来源数据；不接受人工覆盖";
        };
    }

    private PartnerMetrics partnerMetrics(Long studentPersonId) {
        PartnerMetrics m = new PartnerMetrics();
        if (studentPersonId == null) { m.setSourceStatus("WAITING_PARTNER_ACCOUNT"); return m; }
        var link = partnerLinks.selectActiveByStudent(studentPersonId);
        if (link == null) { m.setSourceStatus("WAITING_PARTNER_ACCOUNT"); return m; }
        var partner = partners.selectById(link.getPartnerId());
        if (partner == null) { m.setSourceStatus("WAITING_PARTNER_ACCOUNT"); return m; }
        m.setPartnerId(partner.getId()); m.setSourceStatus("READY");
        var tenant = TenantContextHolder.getRequiredTenantId();
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        var month = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        // Conversion is a cohort of submitted valid leads, never order count / lead count.
        var total = leadMapper.aggregatePartnerValidCohort(tenant, partner.getId(), null, now);
        var current = leadMapper.aggregatePartnerValidCohort(tenant, partner.getId(), month, now);
        m.setTotalLeads(metricCount(total, "leads")); m.setTotalDeals(metricCount(total, "deals"));
        m.setMonthLeads(metricCount(current, "leads")); m.setMonthDeals(metricCount(current, "deals"));
        m.setTotalDealAmount(java.util.Objects.requireNonNullElse(orders.sumPartnerEffectiveGross(tenant, partner.getId(), null, now), java.math.BigDecimal.ZERO));
        m.setMonthDealAmount(java.util.Objects.requireNonNullElse(orders.sumPartnerEffectiveGross(tenant, partner.getId(), month, now), java.math.BigDecimal.ZERO));
        m.setTotalDealRate(rate(m.getTotalDeals(), m.getTotalLeads())); m.setMonthDealRate(rate(m.getMonthDeals(), m.getMonthLeads()));
        return m;
    }
    private long metricCount(Map<String,Object> row, String key) {
        return row == null || row.get(key) == null ? 0L : ((Number) row.get(key)).longValue();
    }
    private java.math.BigDecimal rate(Long deals, Long leads) { return leads==null||leads==0?java.math.BigDecimal.ZERO:java.math.BigDecimal.valueOf(deals==null?0:deals).divide(java.math.BigDecimal.valueOf(leads),4,java.math.RoundingMode.HALF_UP); }

    /**
     * Projects the latest fully submitted positioning card into the immutable account profile fields.
     * Called from the positioning-card confirmation transaction; drafts never reach this method.
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncLatestPositioning(Long accountId, Map<String, Object> positioningValues,
                                      Map<String, Object> dictSnapshots) {
        MediaAccountDO account = mapper.selectByIdForUpdate(accountId,
                TenantContextHolder.getRequiredTenantId());
        if (account == null) throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        Map<String, Object> values = new LinkedHashMap<>(readValues(account));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("account_position", positioningValues.get("pc_account_position"));
        source.put("professional_position", positioningValues.get("pc_profession"));
        source.put("content_format", positioningValues.get("pc_content_form"));
        source.forEach((key, value) -> { values.remove(key); if (value != null) values.put(key, value); });
        List<MediaAccountDetailSnapshotVO> snapshots = new ArrayList<>(readSnapshots(account));
        snapshots.removeIf(s -> source.containsKey(s.getKey()));
        Map<String, String> snapshotKeys = Map.of("account_position", "pc_account_position",
                "professional_position", "pc_profession", "content_format", "pc_content_form",
                "student_commitments", "pc_student_duties", "company_commitments", "pc_company_duties",
                "delivery_goals", "pc_internal_goal");
        source.forEach((key, value) -> {
            if (value == null) return;
            MediaAccountDetailSnapshotVO snapshot = new MediaAccountDetailSnapshotVO();
            snapshot.setKey(key).setValue(value).setDisplayValue(displaySnapshotValue(value, dictSnapshots.get(snapshotKeys.get(key))));
            snapshots.add(snapshot);
        });
        account.setDetailValuesJson(JsonUtils.toJsonString(values));
        account.setDetailSnapshotJson(JsonUtils.toJsonString(snapshots));
        account.setVersion(account.getVersion() + 1);
        if (mapper.update(null, new LambdaUpdateWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getId, accountId)
                .eq(MediaAccountDO::getVersion, account.getVersion() - 1)
                .set(MediaAccountDO::getDetailValuesJson, account.getDetailValuesJson())
                .set(MediaAccountDO::getDetailSnapshotJson, account.getDetailSnapshotJson())
                .set(MediaAccountDO::getVersion, account.getVersion())) != 1) {
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
    }

    private String displaySnapshotValue(Object value, Object dictSnapshot) {
        if (dictSnapshot instanceof Map<?, ?> map && map.get("labelSnapshot") != null)
            return String.valueOf(map.get("labelSnapshot"));
        if (dictSnapshot instanceof Map<?, ?> map && map.get("displayValue") != null)
            return String.valueOf(map.get("displayValue"));
        if (dictSnapshot instanceof Collection<?> list)
            return list.stream().map(item -> displaySnapshotValue(item, item)).collect(java.util.stream.Collectors.joining("、"));
        return String.valueOf(value);
    }


    @ZsjosPermission(bizType="media-account",bizId="#id",action="read")
    public PageResult<Entry> history(Long id, PageParam page, Long userId) {
        accounts.require(id);var result=entries.page(id,page);
        return new PageResult<>(result.getList().stream().map(row -> historyEntry(row,id)).toList(),result.getTotal());
    }

    @ZsjosPermission(bizType="media-account",bizId="#id",action="read")
    public PageResult<Entry> history(Long id, HistoryQuery page, Long userId) {
        accounts.require(id);
        var result = entries.filteredPage(id, page);
        return new PageResult<>(result.getList().stream().map(row -> historyEntry(row,id)).toList(),result.getTotal());
    }

    private Entry historyEntry(MediaAccountProfileEntryDO row, Long id) {
            Entry v=new Entry();v.setId(row.getId());v.setKind(row.getKind());v.setFieldKey(row.getFieldKey());v.setTitle(row.getTitle());v.setContent(row.getContent());
            v.setOperatedBy(row.getOperatedByName());v.setOperatedAt(row.getCreateTime());v.setResultVersion(row.getResultVersion());
            v.setSnapshots(row.getSnapshotJson()==null?List.of():JsonUtils.parseArray(row.getSnapshotJson(),MediaAccountDetailSnapshotVO.class));
            List<FileVO> files=new ArrayList<>(row.getFilesJson()==null?List.of():JsonUtils.parseArray(row.getFilesJson(),FileVO.class));
            v.getSnapshots().stream().filter(s->"image".equals(s.getType())&&s.getValue() instanceof Number).forEach(s->files.add(storedFile(((Number)s.getValue()).longValue(),id)));
            files.forEach(file->{try{file.setPreviewUrl(fileApi.presignGetUrl(file.getId(),300));}catch(RuntimeException unavailable){file.setPreviewUrl(null);}});v.setFiles(files);return v;
    }

    @ZsjosPermission(bizType="media-account",bizId="#id",action="edit")
    public FileVO upload(Long id,String fieldKey,byte[] content,String name,String type,Long userId) {
        MediaAccountDO a=accounts.require(id);requireMaintain(a,userId);
        var f=configs.getPublished().getFields().stream().filter(x->x.getKey().equals(fieldKey)).findFirst().orElseThrow(()->exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
        if(!MediaAccountFieldPolicy.canWrite(f,a,userId))throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        if(!Set.of("image","record","attachment").contains(f.getType())||content.length==0||content.length>20*1024*1024)throw exception(MEDIA_ACCOUNT_ATTACHMENT_INVALID);
        String verified=detectType(content);
        if(verified==null||"image".equals(f.getType())&&!verified.startsWith("image/"))throw exception(MEDIA_ACCOUNT_ATTACHMENT_INVALID);
        String safeName=name==null?"attachment":name.replaceAll("[\\\\/\\r\\n]","_");
        var info=fileApi.createFileInfo(content,safeName,directory(id)+userId,verified);
        FileVO file=file(info.getId(),id,userId);file.setPreviewUrl(fileApi.presignGetUrl(file.getId(),300));return file;
    }
    private String detectType(byte[] b) {
        if(b.length>=8&&b[0]==(byte)0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "image/png";
        if(b.length>=3&&b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff)return "image/jpeg";
        if(b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "image/webp";
        if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-')return "application/pdf";
        return null;
    }
    private FileVO storedFile(Long fileId, Long accountId) {
        try {
            FileVO value=file(fileId,accountId,null);value.setPreviewUrl(fileApi.presignGetUrl(fileId,300));return value;
        } catch (RuntimeException unavailable) {
            // A removed attachment must not prevent maintaining the remaining profile or reading its history.
            FileVO value=new FileVO();value.setId(fileId);value.setName("附件暂不可用");return value;
        }
    }
    private FileVO file(Long fileId,Long accountId,Long owner) {
        var info=fileApi.getFileInfo(fileId);
        if(info==null||info.getPath()==null||!info.getPath().startsWith(directory(accountId))
            ||owner!=null&&(!String.valueOf(owner).equals(info.getCreator())||!info.getPath().startsWith(directory(accountId)+owner+"/")))throw exception(MEDIA_ACCOUNT_ATTACHMENT_INVALID);
        FileVO f=new FileVO();f.setId(info.getId());f.setName(info.getName());f.setType(info.getType());f.setSize(info.getSize());return f;
    }
    private String directory(Long id){return "zsjos/media-account/"+TenantContextHolder.getRequiredTenantId()+"/"+id+"/";}
    private MediaAccountDO lock(Long id){var a=mapper.selectByIdForUpdate(id,TenantContextHolder.getRequiredTenantId());if(a==null)throw exception(MEDIA_ACCOUNT_NOT_EXISTS);return a;}
    private boolean canMaintain(MediaAccountDO a,Long uid){return objects.hasPermission(a.getId(),"edit",uid)&&permissionApi.hasAnyPermissions(uid,"zsjos:media-account:edit","zsjos:media-account:maintenance");}
    private void requireMaintain(MediaAccountDO a,Long uid){if(!canMaintain(a,uid))throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);}
    private void requireVersion(MediaAccountDO a,Integer version,Long configId,Long currentId){if(!Objects.equals(a.getVersion(),version))throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);if(!Objects.equals(configId,currentId))throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);}
    private Integer replay(Long id,Long userId,String key,String fp){var prior=entries.replay(id,userId,key);if(prior==null)return null;if(!fp.equals(prior.getFingerprint()))throw exception(MEDIA_ACCOUNT_PROFILE_IDEMPOTENCY_CONFLICT);return prior.getResultVersion();}
    private String userName(Long id){var user=id==null?null:users.getUser(id);return user==null?null:user.getNickname();}
    private MediaAccountProfileEntryDO entry(Long id,Long uid,String key,String fp,Integer version,String kind,String field,String title,String content){
        var e=new MediaAccountProfileEntryDO();e.setAccountId(id);e.setOperatedByUserId(uid);e.setOperatedByName(userName(uid));e.setIdempotencyKey(key);e.setFingerprint(fp);e.setResultVersion(version);e.setKind(kind);e.setFieldKey(field);e.setTitle(title);e.setContent(content);return e;
    }
    private Object canonical(Object value){if(value instanceof Map<?,?> map){Map<String,Object> ordered=new TreeMap<>();map.forEach((k,v)->ordered.put(String.valueOf(k),canonical(v)));return ordered;}if(value instanceof Collection<?> list)return list.stream().map(this::canonical).toList();return value;}
    private String fingerprint(String kind,Integer version,Long configId,Object data){return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(kind,version,configId,canonical(data))));}
    @SuppressWarnings("unchecked") private Map<String,Object> readValues(MediaAccountDO a){
        Map<String,Object> values=a.getDetailValuesJson()==null?new LinkedHashMap<>():new LinkedHashMap<>(JsonUtils.parseObject(a.getDetailValuesJson(),Map.class));
        // Older accounts stored these values in dedicated columns, before the configurable profile existed.
        if(a.getNickname()!=null)values.putIfAbsent("nickname",a.getNickname());
        if(a.getPlatformAccountId()!=null)values.putIfAbsent("uid",a.getPlatformAccountId());
        if(a.getPlatformValue()!=null)values.putIfAbsent("platform",a.getPlatformValue());
        return values;
    }
    private List<MediaAccountDetailSnapshotVO> readSnapshots(MediaAccountDO a){
        List<MediaAccountDetailSnapshotVO> snapshots=new ArrayList<>(a.getDetailSnapshotJson()==null?List.of():JsonUtils.parseArray(a.getDetailSnapshotJson(),MediaAccountDetailSnapshotVO.class));
        if(a.getPlatformValue()!=null && a.getPlatformLabelSnapshot()!=null && snapshots.stream().noneMatch(s->"platform".equals(s.getKey()))) {
            var s=new MediaAccountDetailSnapshotVO();s.setKey("platform");s.setLabel("账号平台");s.setType("select");s.setDictType("zsjos_account_platform");s.setValue(a.getPlatformValue());s.setDisplayValue(a.getPlatformLabelSnapshot());snapshots.add(s);
        }
        return snapshots;
    }
}
