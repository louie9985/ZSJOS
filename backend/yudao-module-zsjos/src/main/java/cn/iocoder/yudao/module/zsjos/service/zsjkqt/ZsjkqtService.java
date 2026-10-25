package cn.iocoder.yudao.module.zsjos.service.zsjkqt;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo.ZsjkqtVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.*;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Service
@org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('zsjos:zsjkqt:access')")
public class ZsjkqtService {
    @Resource private ZsjkqtDraftMapper drafts;
    @Resource private ZsjkqtConfigMapper configs;
    @Resource private ZsjkqtExcelEngine excel;
    public PageResult<Draft> page(int page, int size, String keyword, Integer year, Integer month) {
        var query=new LambdaQueryWrapperX<ZsjkqtDraftDO>().likeIfPresent(ZsjkqtDraftDO::getName, keyword)
        .eqIfPresent(ZsjkqtDraftDO::getYear, year).eqIfPresent(ZsjkqtDraftDO::getMonth, month)
        .orderByDesc(ZsjkqtDraftDO::getUpdateTime).orderByDesc(ZsjkqtDraftDO::getId);
        query.select(ZsjkqtDraftDO.class, field -> !"snapshot_json".equals(field.getColumn()));
        var result=drafts.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(d->view(d, false)).toList(), result.getTotal());
    }
    private ZsjkqtDraftDO require(Long id) {
        var d=drafts.selectById(id);
        if(d==null)throw exception(ZsjkqtErrors.NOT_FOUND);
        return d;
    }
    @ZsjosPermission(bizType="zsjkqt-draft", bizId="#id", action="read")
    public Draft get(Long id) {
        return view(require(id), true);
    }
    @Transactional(rollbackFor=Exception.class)
    public Draft create(Save req) {
        validateSnapshot(req.snapshot());
        if(req.version()!=0)throw exception(ZsjkqtErrors.INVALID, "新草稿版本必须为 0");
        var existing=drafts.selectOne(ZsjkqtDraftDO::getClientId, req.clientId());
        if(existing!=null) return replayCreate(existing, req);
        var row=new ZsjkqtDraftDO();
        row.setClientId(req.clientId());
        row.setVersion(0);
        fill(row, req);
        try {
            drafts.insert(row);
        }
        catch(org.springframework.dao.DuplicateKeyException e) {
            if (!clientIdConflict(e)) throw e;
            // The first lookup may have established a repeatable-read snapshot before the winner committed.
            return replayCreate(drafts.selectByClientIdForUpdate(req.clientId(), TenantContextHolder.getRequiredTenantId()), req);
        }
        return view(row, true);
    }
    private Draft replayCreate(ZsjkqtDraftDO existing, Save req) {
        // A tombstone reserves its client ID; retries must never resurrect a deleted project.
        if (existing == null || !Objects.equals(existing.getName(), req.name())
                || !JsonUtils.getObjectMapper().readTree(existing.getSnapshotJson()).equals(req.snapshot())) {
            throw exception(ZsjkqtErrors.CONFLICT);
        }
        return view(existing, true);
    }
    private static boolean clientIdConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("uk_zsjkqt_client")) return true;
        }
        return false;
    }
    @ZsjosPermission(bizType="zsjkqt-draft", bizId="#id", action="update")
    @Transactional(rollbackFor=Exception.class)
    public Draft save(Long id, Save req) {
        validateSnapshot(req.snapshot());
        var current=require(id);
        if(!current.getClientId().equals(req.clientId()))throw exception(ZsjkqtErrors.INVALID, "项目标识不匹配");
        // Loaded audit fields must not suppress the framework's current-operator/time fill.
        var update=new ZsjkqtDraftDO();
        fill(update, req);
        update.setVersion(req.version()+1);
        if(drafts.update(update, new LambdaUpdateWrapper<ZsjkqtDraftDO>().eq(ZsjkqtDraftDO::getId, id).eq(ZsjkqtDraftDO::getVersion, req.version()))!=1)throw exception(ZsjkqtErrors.CONFLICT);
        return view(require(id), true);
    }
    @ZsjosPermission(bizType="zsjkqt-draft", bizId="#id", action="update")
    public void rename(Long id, Rename req) {
        var update=new ZsjkqtDraftDO();
        update.setName(req.name());
        update.setVersion(req.version()+1);
        update.setUpdaterName(operatorName());
        if(drafts.update(update, new LambdaUpdateWrapper<ZsjkqtDraftDO>().eq(ZsjkqtDraftDO::getId, id).eq(ZsjkqtDraftDO::getVersion, req.version()))!=1)throw exception(ZsjkqtErrors.CONFLICT);
    }
    @ZsjosPermission(bizType="zsjkqt-draft", bizId="#id", action="delete")
    public void delete(Long id, int version) {
        if(drafts.delete(new LambdaQueryWrapperX<ZsjkqtDraftDO>().eq(ZsjkqtDraftDO::getId, id).eq(ZsjkqtDraftDO::getVersion, version))!=1)throw exception(ZsjkqtErrors.CONFLICT);
    }
    @ZsjosPermission(bizType="zsjkqt-draft", bizId="#id", action="export")
    public byte[] export(Long id, int version, boolean issues) {
        var row=require(id);
        if(!Objects.equals(row.getVersion(), version))throw exception(ZsjkqtErrors.CONFLICT);
        JsonNode snapshot=JsonUtils.getObjectMapper().readTree(row.getSnapshotJson());
        JsonNode project=snapshot.path("project");
        if(!project.isObject()||project.path("employees").isEmpty())throw exception(ZsjkqtErrors.INVALID, "请先生成员工确认工作台");
        try {
            return issues?excel.exportIssues(project):excel.exportReport(project);
        }
        catch(IOException|IllegalArgumentException e) {
            throw exception(ZsjkqtErrors.EXCEL, e.getMessage());
        }
    }
    private void fill(ZsjkqtDraftDO row, Save req) {
        JsonNode s=req.snapshot(), p=s.path("project").isObject()?s.path("project"):s.path("month");
        row.setName(req.name());
        row.setYear(p.path("year").asInt());
        row.setMonth(p.path("month").asInt());
        row.setSourceFileName(ZsjkqtExcelEngine.text(p, "sourceFileName"));
        row.setSourceFileHash(ZsjkqtExcelEngine.text(p, "sourceFileHash"));
        row.setSnapshotJson(JsonUtils.toJsonString(s));
        row.setEmployeeCount(p.path("employees").size());
        int confirmed=0;
        for(JsonNode e:p.path("employees"))if(e.path("confirmed").asBoolean())confirmed++;
        row.setConfirmedCount(confirmed);
        row.setUpdaterName(operatorName());
    }
    static void validateSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject() || !integer(snapshot.path("schemaVersion"), 1, 1)) {
            throw exception(ZsjkqtErrors.INVALID, "快照版本不支持");
        }
        if (snapshot.has("step") && (!snapshot.path("step").isTextual()
        || !Set.of("home", "department-settings", "settings", "reimport-settings", "review")
        .contains(snapshot.path("step").asText()))) {
            throw exception(ZsjkqtErrors.INVALID, "页面状态不支持");
        }
        JsonNode project = snapshot.path("project");
        JsonNode source = project.isObject() ? project : snapshot.path("month");
        if (!source.isObject() || !integer(source.path("year"), 2000, 2099)
        || !integer(source.path("month"), 1, 12)
        || !source.path("employees").isArray() || source.path("employees").size() > 10000) {
            throw exception(ZsjkqtErrors.INVALID, "年月或人员列表不正确");
        }
        optionalText(source, "sourceFileName", 255);
        optionalText(source, "sourceFileHash", 64);
        if (JsonUtils.toJsonString(snapshot).length() > 12_000_000) {
            throw exception(ZsjkqtErrors.INVALID, "项目过大");
        }
        int daysInMonth = YearMonth.of(source.path("year").asInt(), source.path("month").asInt()).lengthOfMonth();
        for (String key : List.of("shouldAttendanceDays", "payrollDays")) {
            optionalNumber(snapshot, key);
            if (project.isObject()) optionalNumber(project, key);
        }
        for (String key : List.of("restDays", "paidRestDays")) {
            optionalDays(snapshot, key, daysInMonth);
            if (project.isObject()) optionalDays(project, key, daysInMonth);
        }
        if (snapshot.has("departmentSchedules")) validateSchedules(snapshot.path("departmentSchedules"));
        if (snapshot.has("personnelProfiles")) {
            JsonNode profiles = snapshot.path("personnelProfiles");
            if (!profiles.isObject() || profiles.size() > 10000) {
                throw exception(ZsjkqtErrors.INVALID, "人员资料快照格式不正确");
            }
            for (var entry : profiles.properties()) {
                JsonNode profile = entry.getValue();
                if (entry.getKey().isBlank() || entry.getKey().length() > 200 || !profile.isObject()) {
                    throw exception(ZsjkqtErrors.INVALID, "人员资料快照标识或内容不正确");
                }
                for (String key : List.of("name", "department", "entry_date")) optionalText(profile, key, 200);
                if (profile.has("enabled") && !profile.path("enabled").isBoolean()) {
                    throw exception(ZsjkqtErrors.INVALID, "人员资料导出状态不正确");
                }
            }
        }
        int employeeIndex = 0;
        for (JsonNode employee : source.path("employees")) {
            employeeIndex++;
            if (!employee.isObject()) throw exception(ZsjkqtErrors.INVALID, "人员记录必须为对象");
            validateExcelText(employee, "第" + employeeIndex + "位员工的考勤内容");
            if (!project.isObject()) continue;
            optionalText(employee, "displayName", 200);
            optionalText(employee, "displayDepartment", 200);
            for (String key : List.of("enabled", "confirmed")) {
                if (employee.has(key) && !employee.path(key).isBoolean()) {
                    throw exception(ZsjkqtErrors.INVALID, "人员确认或导出状态不正确");
                }
            }
            if (!employee.path("days").isArray() || employee.path("days").size() > 31) {
                throw exception(ZsjkqtErrors.INVALID, "每日记录不正确");
            }
            if (employee.has("summary") && !employee.path("summary").isObject()) {
                throw exception(ZsjkqtErrors.INVALID, "汇总必须为对象");
            }
            for (JsonNode value : employee.path("summary")) {
                if ((value.isObject() || value.isArray()) || (value.isNumber() && !Double.isFinite(value.asDouble()))) {
                    throw exception(ZsjkqtErrors.INVALID, "汇总值必须为文本、有限数值或空值");
                }
            }
            Set<Integer> seen = new HashSet<>();
            for (JsonNode day : employee.path("days")) {
                if (!day.isObject() || !integer(day.path("day"), 1, daysInMonth)
                || !seen.add(day.path("day").asInt())) {
                    throw exception(ZsjkqtErrors.INVALID, "日期无效或重复");
                }
                for (String key : List.of("finalMorning", "finalAfternoon", "systemMorning", "systemAfternoon")) {
                    optionalText(day, key, 20);
                    if (!ZsjkqtExcelEngine.STATUSES.contains(ZsjkqtExcelEngine.text(day, key))) {
                        throw exception(ZsjkqtErrors.INVALID, "考勤状态不支持");
                    }
                }
                String notes = java.util.stream.Stream.of(ZsjkqtExcelEngine.text(day, "morningNote"), ZsjkqtExcelEngine.text(day, "afternoonNote"))
                        .filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.joining("；"));
                ZsjkqtExcelEngine.requireCellText(notes, "第" + employeeIndex + "位员工第" + day.path("day").asInt() + "日的合并备注");
                List<String> issues = new ArrayList<>();
                for (JsonNode issue : day.path("issues")) issues.add(ZsjkqtExcelEngine.text(issue, "message"));
                ZsjkqtExcelEngine.requireCellText(String.join("；", issues), "第" + employeeIndex + "位员工的异常说明");
            }
        }
    }
    private static void validateExcelText(JsonNode value, String label) {
        if (value.isTextual()) ZsjkqtExcelEngine.requireCellText(value.asText(), label);
        else if (value.isContainer()) for (JsonNode child : value) validateExcelText(child, label);
    }
    private static boolean integer(JsonNode value, int min, int max) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= min && value.asInt() <= max;
    }
    private static void optionalText(JsonNode object, String key, int limit) {
        JsonNode value = object.path(key);
        if (value.isMissingNode()) return;
        if (!value.isTextual() || value.asText().length() > limit) {
            throw exception(ZsjkqtErrors.INVALID, key + " 文本格式或长度不正确");
        }
    }
    private static void optionalNumber(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (value.isMissingNode() || value.isNull()) return;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw exception(ZsjkqtErrors.INVALID, key + " 必须是有限数值或空值");
        }
    }
    private static void optionalDays(JsonNode object, String key, int daysInMonth) {
        JsonNode days = object.path(key);
        if (days.isMissingNode()) return;
        if (!days.isArray()) throw exception(ZsjkqtErrors.INVALID, key + " 必须是日期列表");
        Set<Integer> seen = new HashSet<>();
        for (JsonNode day : days) {
            if (!integer(day, 1, daysInMonth) || !seen.add(day.asInt())) {
                throw exception(ZsjkqtErrors.INVALID, key + " 包含无效或重复日期");
            }
        }
    }
    private static String operatorName() {
        String nickname=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname();
        return nickname==null?"":nickname;
    }
    private Draft view(ZsjkqtDraftDO d, boolean snapshot) {
        return new Draft(d.getId(), d.getClientId(), d.getName(), d.getYear(), d.getMonth(), d.getSourceFileName(), d.getSourceFileHash(), d.getEmployeeCount(), d.getConfirmedCount(), d.getVersion(), d.getUpdateTime(), d.getUpdater(), d.getUpdaterName(), snapshot?JsonUtils.getObjectMapper().readTree(d.getSnapshotJson()):null);
    }
    public Config config(String key) {
        if(!Set.of("schedules", "profiles").contains(key))throw exception(ZsjkqtErrors.INVALID, "配置类型不支持");
        var row=configs.selectOne(ZsjkqtConfigDO::getConfigKey, key);
        if(row!=null)return new Config(row.getVersion(), JsonUtils.getObjectMapper().readTree(row.getValueJson()));
        if(key.equals("schedules"))try(var in=new ClassPathResource("zsjkqt/department-schedules.json").getInputStream()) {
            return new Config(0, JsonUtils.getObjectMapper().readTree(in));
        }
        catch(IOException e) {
            throw new IllegalStateException("考勤时段初始配置缺失", e);
        }
        return new Config(0, JsonUtils.getObjectMapper().createObjectNode());
    }
    @Transactional(rollbackFor=Exception.class)
    public Config saveSchedules(ConfigSave req) {
        validateSchedules(req.value());
        var row=configs.selectOne(ZsjkqtConfigDO::getConfigKey, "schedules");
        if(row==null)throw exception(ZsjkqtErrors.INVALID, "部门时段存储尚未初始化");
        var update=new ZsjkqtConfigDO();
        update.setValueJson(JsonUtils.toJsonString(req.value()));
        update.setVersion(req.version()+1);
        if(configs.update(update, new LambdaUpdateWrapper<ZsjkqtConfigDO>().eq(ZsjkqtConfigDO::getId, row.getId()).eq(ZsjkqtConfigDO::getVersion, req.version()))!=1)throw exception(ZsjkqtErrors.CONFIG_CONFLICT);
        return config("schedules");
    }
    static void validateSchedules(JsonNode s) {
        if(!s.isObject()||!s.path("default").isObject()||!s.path("departments").isObject())throw exception(ZsjkqtErrors.INVALID, "部门时段结构不正确");
        validateSchedule(s.path("default"));
        for(JsonNode d:s.path("departments")) {
            String mode=d.path("mode").asText();
            if(!Set.of("custom", "default").contains(mode))throw exception(ZsjkqtErrors.INVALID, "部门时段模式不正确");
            if(mode.equals("custom"))validateSchedule(d);
        }
    }
    static void validateSchedule(JsonNode s) {
        try {
            LocalTime ms=LocalTime.parse(s.path("morning").path("start").asText()), me=LocalTime.parse(s.path("morning").path("end").asText()), as=LocalTime.parse(s.path("afternoon").path("start").asText()), ae=LocalTime.parse(s.path("afternoon").path("end").asText());
            if(!ms.isBefore(me)||!as.isBefore(ae)||me.isAfter(as))throw new IllegalArgumentException();
        }
        catch(Exception e) {
            throw exception(ZsjkqtErrors.INVALID, "时段时间无效或重叠");
        }
    }
    @Transactional(rollbackFor=Exception.class)
    public void profile(Profile req) {
        if (req.entryDate() != null && !req.entryDate().isBlank()) {
            try {
                LocalDate.parse(req.entryDate());
            }
            catch (java.time.format.DateTimeParseException e) {
                throw exception(ZsjkqtErrors.INVALID, "入职日期无效");
            }
        }
        // Serialize field-level updates so two users updating different people cannot lose each other's changes.
        var row=configs.selectOne(new LambdaQueryWrapperX<ZsjkqtConfigDO>().eq(ZsjkqtConfigDO::getConfigKey, "profiles").last("FOR UPDATE"));
        if(row==null)throw exception(ZsjkqtErrors.INVALID, "人员资料存储尚未初始化");
        JsonNode root=JsonUtils.getObjectMapper().readTree(row.getValueJson());
        upsertProfile(root, req);
        var update=new ZsjkqtConfigDO();
        update.setId(row.getId());
        update.setValueJson(JsonUtils.toJsonString(root));
        update.setVersion(row.getVersion()+1);
        configs.updateById(update);
    }
    static void upsertProfile(JsonNode root, Profile req) {
        if(!root.isObject())throw exception(ZsjkqtErrors.INVALID, "人员档案顶层必须是 JSON 对象");
        String key=req.key().trim();
        if(key.isEmpty())throw exception(ZsjkqtErrors.INVALID, "人员档案缺少员工标识");
        JsonNode old=root.get(key);
        if(old!=null&&!old.isObject())throw exception(ZsjkqtErrors.INVALID, "该员工档案必须是 JSON 对象");
        ObjectNode p=old==null?((ObjectNode)root).putObject(key):(ObjectNode)old;
        p.put("name", req.name()==null?"":req.name().trim());
        p.put("department", req.department()==null?"":req.department().trim());
        p.put("entry_date", req.entryDate()==null?"":req.entryDate().trim());
        p.put("enabled", req.enabled());
    }
}
