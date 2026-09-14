package cn.iocoder.yudao.module.zsjos.service.account;

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
    @Resource private cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService businessTaskCommandService;
    @Resource private MediaAccountMapper mapper;
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
        result.setPartnerMetrics(partnerMetrics(a.getStudentPersonId()));
        for(var f:config.getFields()) {
            if(!"AUTO".equals(f.getOwnerType())) continue;
            if (MediaAccountFieldPolicy.POSITIONING_SYNC_FIELDS.contains(f.getKey())) continue;
            Object value=switch(f.getKey()) {
                case "account_no" -> a.getAccountNo();
                case "student_name" -> person==null?null:person.getName();
                case "contact" -> person==null?null:person.getMobile();
                default -> null;
            };
            values.remove(f.getKey()); if(value!=null)values.put(f.getKey(),value);
            notes.put(f.getKey(),value==null?"等待来源数据；不接受人工覆盖":
                "student_name".equals(f.getKey())||"contact".equals(f.getKey())?"当前学员档案":"账号已保存的业务数据与标签快照");
            if("METRICS".equals(f.getGroup())) notes.put(f.getKey(),f.getKey().startsWith("month_")?"本月截至当前；统计口径未配置":"累计至当前；统计口径未配置");
        }
        // Diagnosis fields are configured data. Until a diagnosis is submitted, they remain empty.
        if (a.getSStage() != null) values.put("stage", a.getSStage());
        if (a.getCurrentStatusValue() != null) values.put("current_status", a.getCurrentStatusValue());
        if (a.getPrimaryProblemCodeValue() != null) values.put("bottleneck", a.getPrimaryProblemCodeValue());
        result.setValues(values);result.setSourceNotes(notes);result.setSnapshots(readSnapshots(a));
        boolean write=canMaintain(a,userId);
        result.setCanSubmitPositioning(write && permissionApi.hasAnyPermissions(userId,"zsjos:positioning-card:create")
                && config.getFields().stream().anyMatch(f -> "POSITIONING".equals(f.getGroup()) && MediaAccountFieldPolicy.canWrite(f,a,userId)));
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
        result.setFiles(files);return result;
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
        if (!Set.of("diagnosis_7d", "diagnosis_14d", "diagnosis_28d", "adjustment_28d").contains(req.getTemplateType()))
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        String fieldKey = "diagnosis_28d".equals(req.getTemplateType()) ? "adjustment_28d" : req.getTemplateType();
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("templateType", req.getTemplateType()); data.put("currentStage", req.getCurrentStage());
        data.put("accountStatus", req.getAccountStatus()); data.put("cooperationLevel", req.getCooperationLevel());
        data.put("cooperationEvidence", req.getCooperationEvidence()); data.put("primaryProblem", req.getPrimaryProblem());
        data.put("primaryProblemEvidence", req.getPrimaryProblemEvidence()); data.put("secondaryProblem", req.getSecondaryProblem());
        data.put("secondaryProblemEvidence", req.getSecondaryProblemEvidence()); data.put("conclusion", req.getConclusion());
        data.put("improvementMeasures", req.getImprovementMeasures()); data.put("observedData", req.getObservedData());
        data.put("reposition", req.getReposition());
        RecordRequest record = new RecordRequest(); record.setVersion(req.getVersion()).setConfigVersionId(req.getConfigVersionId())
                .setFieldKey(fieldKey).setIdempotencyKey(req.getIdempotencyKey()).setContent(JsonUtils.toJsonString(data)).setFileIds(List.of());
        Integer result = append(id, record, userId);
        String taskType = switch (req.getTemplateType()) {
            case "diagnosis_7d" -> "media_account_diagnosis_7d";
            case "diagnosis_14d" -> "media_account_diagnosis_14d";
            default -> "media_account_diagnosis_28d";
        };
        businessTaskCommandService.completeByKey("media-diagnosis:" + id + ":" + taskType + ":" + (req.getCycle() == null ? 1 : req.getCycle()), java.time.LocalDateTime.now());
        return result;
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
        var start = partner.getCreateTime() == null ? java.time.LocalDateTime.MIN : partner.getCreateTime();
        var month = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withDayOfMonth(1).toLocalDate().atStartOfDay();
        m.setTotalLeads(leadMapper.countPartnerLeadsSince(tenant, partner.getId(), start));
        m.setMonthLeads(leadMapper.countPartnerLeadsSince(tenant, partner.getId(), month));
        var total = orders.aggregatePartnerDeals(tenant, partner.getId(), start);
        var current = orders.aggregatePartnerDeals(tenant, partner.getId(), month);
        setDeal(m, total, true); setDeal(m, current, false);
        m.setTotalDealRate(rate(m.getTotalDeals(), m.getTotalLeads())); m.setMonthDealRate(rate(m.getMonthDeals(), m.getMonthLeads()));
        // A bound partner account can exist before its first lead/order is sourced. Keep
        // this distinct from a ready data source so the UI can show the agreed waiting state.
        if (m.getTotalLeads() == 0L && m.getTotalDeals() == 0L
                && m.getTotalDealAmount().compareTo(java.math.BigDecimal.ZERO) == 0) {
            m.setSourceStatus("WAITING_PARTNER_DATA");
        }
        return m;
    }
    private void setDeal(PartnerMetrics m, Map<String,Object> row, boolean total) { long d=row==null||row.get("deals")==null?0L:((Number)row.get("deals")).longValue(); java.math.BigDecimal a=row==null||row.get("amount")==null?java.math.BigDecimal.ZERO:new java.math.BigDecimal(row.get("amount").toString()); if(total){m.setTotalDeals(d);m.setTotalDealAmount(a);}else{m.setMonthDeals(d);m.setMonthDealAmount(a);} }
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
        source.put("student_commitments", positioningValues.get("pc_student_duties"));
        source.put("company_commitments", positioningValues.get("pc_company_duties"));
        source.put("delivery_goals", positioningValues.get("pc_internal_goal"));
        source.forEach((key, value) -> { values.remove(key); if (value != null) values.put(key, value); });
        List<MediaAccountDetailSnapshotVO> snapshots = new ArrayList<>(readSnapshots(account));
        snapshots.removeIf(s -> source.containsKey(s.getKey()));
        source.forEach((key, value) -> {
            if (value == null) return;
            MediaAccountDetailSnapshotVO snapshot = new MediaAccountDetailSnapshotVO();
            snapshot.setKey(key).setValue(value).setDisplayValue(displaySnapshotValue(value, dictSnapshots.get(key)));
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
        if (dictSnapshot instanceof Map<?, ?> map && map.get("displayValue") != null)
            return String.valueOf(map.get("displayValue"));
        if (dictSnapshot instanceof Collection<?> list)
            return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("、"));
        return String.valueOf(value);
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="media-account",bizId="#id",action="edit")
    public Integer submitPositioning(Long id, Patch req, Long userId) {
        MediaAccountDO account=lock(id);
        requireMaintain(account,userId);
        if (!permissionApi.hasAnyPermissions(userId,"zsjos:positioning-card:create")) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        var config=configs.getPublished();
        var fields=config.getFields().stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()) && "POSITIONING".equals(f.getGroup())).toList();
        if (fields.stream().noneMatch(f -> MediaAccountFieldPolicy.canWrite(f,account,userId))) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        Set<String> keys=fields.stream().map(MediaAccountFieldConfigRespVO.FieldVO::getKey).collect(java.util.stream.Collectors.toSet());
        if (!keys.containsAll(req.getChanges().keySet())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        String fp=fingerprint("POSITIONING",req.getVersion(),req.getConfigVersionId(),req.getChanges());
        Integer prior=replay(id,userId,req.getIdempotencyKey(),fp);
        if (prior!=null) return prior;
        requireVersion(account,req.getVersion(),req.getConfigVersionId(),config.getId());
        Map<String,Object> prospective=readValues(account);prospective.putAll(req.getChanges());
        if (fields.stream().anyMatch(f -> Boolean.TRUE.equals(f.getRequired()) && !"AUTO".equals(f.getOwnerType())
                && !"record".equals(f.getType()) && MediaAccountFieldPolicy.empty(prospective.get(f.getKey())))) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        // Saving the draft and freezing a submission share this transaction and account lock.
        Patch draft=new Patch();draft.setVersion(req.getVersion());draft.setConfigVersionId(req.getConfigVersionId());
        draft.setChanges(req.getChanges());draft.setIdempotencyKey("positioning-draft-"+DigestUtil.sha256Hex(req.getIdempotencyKey()));
        Integer version=patch(id,draft,userId);
        MediaAccountDO savedAccount=lock(id);
        var snapshot=new PositioningSnapshot();snapshot.setConfigVersionId(config.getId());snapshot.setFields(fields);
        snapshot.setValues(readSnapshots(savedAccount).stream().filter(s -> keys.contains(s.getKey())).toList());
        List<FileVO> frozenFiles=new ArrayList<>();
        for(var s:snapshot.getValues()) {
            if("image".equals(s.getType())&&s.getValue() instanceof Number n)frozenFiles.add(file(n.longValue(),id,null));
            if("attachment".equals(s.getType())&&s.getValue() instanceof Collection<?> ids)
                for(Object fileId:ids)frozenFiles.add(file(((Number)fileId).longValue(),id,null));
        }
        snapshot.setFiles(frozenFiles);
        var entry=entry(id,userId,req.getIdempotencyKey(),fp,version,"POSITIONING",null,"定位卡正式提交",JsonUtils.toJsonString(snapshot));
        entry.setSnapshotJson(JsonUtils.toJsonString(snapshot.getValues()));
        entry.setFilesJson(JsonUtils.toJsonString(snapshot.getFiles()));
        entries.insert(entry);
        return version;
    }

    @ZsjosPermission(bizType="media-account",bizId="#id",action="read")
    public PageResult<Entry> positioningVersions(Long id,PageParam page,Long userId) {
        accounts.require(id);
        var rows=entries.positioningPage(id,page);
        return new PageResult<>(rows.getList().stream().map(row -> {
            Entry value=new Entry();value.setId(row.getId());value.setKind(row.getKind());value.setTitle(row.getTitle());
            value.setOperatedBy(row.getOperatedByName());value.setOperatedAt(row.getCreateTime());value.setResultVersion(row.getResultVersion());
            value.setPositioning(JsonUtils.parseObject(row.getContent(),PositioningSnapshot.class));
            value.setSnapshots(value.getPositioning().getValues());
            var files=value.getPositioning().getFiles()==null?List.<FileVO>of():value.getPositioning().getFiles();
            files.forEach(file -> {try {file.setPreviewUrl(fileApi.presignGetUrl(file.getId(),300));} catch (RuntimeException unavailable) {file.setPreviewUrl(null);}});
            value.setFiles(files);return value;
        }).toList(),rows.getTotal());
    }

    @ZsjosPermission(bizType="media-account",bizId="#id",action="read")
    public PageResult<Entry> history(Long id, PageParam page, Long userId) {
        accounts.require(id);var result=entries.page(id,page);
        return new PageResult<>(result.getList().stream().map(row->{
            Entry v=new Entry();v.setId(row.getId());v.setKind(row.getKind());v.setFieldKey(row.getFieldKey());v.setTitle(row.getTitle());v.setContent(row.getContent());
            v.setOperatedBy(row.getOperatedByName());v.setOperatedAt(row.getCreateTime());v.setResultVersion(row.getResultVersion());
            if ("POSITIONING".equals(row.getKind())) {v.setPositioning(JsonUtils.parseObject(row.getContent(),PositioningSnapshot.class));v.setContent(null);}
            v.setSnapshots(row.getSnapshotJson()==null?List.of():JsonUtils.parseArray(row.getSnapshotJson(),MediaAccountDetailSnapshotVO.class));
            List<FileVO> files=new ArrayList<>(row.getFilesJson()==null?List.of():JsonUtils.parseArray(row.getFilesJson(),FileVO.class));
            v.getSnapshots().stream().filter(s->"image".equals(s.getType())&&s.getValue() instanceof Number).forEach(s->files.add(storedFile(((Number)s.getValue()).longValue(),id)));
            files.forEach(file->{try{file.setPreviewUrl(fileApi.presignGetUrl(file.getId(),300));}catch(RuntimeException unavailable){file.setPreviewUrl(null);}});v.setFiles(files);return v;
        }).toList(),result.getTotal());
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
