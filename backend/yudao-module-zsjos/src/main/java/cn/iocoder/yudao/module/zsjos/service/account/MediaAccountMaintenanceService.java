package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.AccountStageLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountMaintenanceRevisionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.AccountStageLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMaintenanceRevisionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_MEDIA_ACCOUNT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MediaAccountMaintenanceService {
    public static final String DICT_CURRENT_STATUS = "zsjos_media_account_current_status";
    public static final String DICT_STAGE = "zsjos_media_account_stage";
    public static final String DICT_PRIMARY_PROBLEM = "zsjos_media_account_primary_problem";
    public static final String DICT_EXECUTION_MEASURE = "zsjos_media_account_execution_measure";
    public static final String PERMISSION_CALENDAR_QUERY_ALL = "zsjos:media-calendar:query-all";
    private static final String ROLE_CONTENT_DIRECTOR = "content_director";
    private static final String ROLE_NEW_MEDIA_OPERATOR = "new_media_operator";

    private static final LinkedHashMap<String, String> FIELD_NAMES = new LinkedHashMap<>();
    static {
        FIELD_NAMES.put("currentStatus", "当下状态");
        FIELD_NAMES.put("stage", "阶段");
        FIELD_NAMES.put("primaryProblems", "主要问题");
        FIELD_NAMES.put("executionMeasure", "实行措施");
        FIELD_NAMES.put("adjustmentDirection", "修改方向");
        FIELD_NAMES.put("startDate", "开始日期");
        FIELD_NAMES.put("endDate", "结束日期");
    }

    @Resource private MediaAccountMapper accountMapper;
    @Resource private MediaAccountMaintenanceRevisionMapper revisionMapper;
    @Resource private AccountStageLogMapper stageLogMapper;
    @Resource private MediaAccountObjectPermissionProvider objectPermissionProvider;
    @Resource private DictDataApi dictDataApi;
    @Resource private PermissionApi permissionApi;
    @Resource private RoleApi roleApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PersonMapper personMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private MediaDataScopeService mediaDataScopeService;

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "maintenance")
    @Transactional(rollbackFor = Exception.class)
    public Integer maintain(Long accountId, MediaAccountMaintenanceReqVO req, Long operatorUserId) {
        validateDates(req);
        MediaAccountDO account = require(accountId);
        if (!Objects.equals(account.getVersion(), req.getVersion())) {
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
        List<MediaAccountMaintenanceProblemVO> oldProblems = parseProblems(account.getPrimaryProblemsJson());
        Snapshot status = snapshot(DICT_CURRENT_STATUS, normalize(req.getCurrentStatusValue()),
                account.getCurrentStatusValue(), account.getCurrentStatusLabelSnapshot());
        Snapshot stage = snapshot(DICT_STAGE, normalize(req.getStageValue()),
                account.getSStage(), account.getSStageLabelSnapshot());
        List<String> problemValues = normalizeValues(req.getPrimaryProblemValues());
        List<MediaAccountMaintenanceProblemVO> problems = problemValues.equals(values(oldProblems))
                ? oldProblems : snapshotProblems(problemValues);
        Snapshot measure = snapshot(DICT_EXECUTION_MEASURE, normalize(req.getExecutionMeasureValue()),
                account.getExecutionMeasureValue(), account.getExecutionMeasureLabelSnapshot());
        String direction = normalize(req.getAdjustmentDirection());

        LinkedHashMap<String, String> changes = new LinkedHashMap<>();
        compare(changes, "currentStatus", account.getCurrentStatusValue(), status.value(),
                account.getCurrentStatusLabelSnapshot(), status.label());
        compare(changes, "stage", account.getSStage(), stage.value(), account.getSStageLabelSnapshot(), stage.label());
        compare(changes, "primaryProblems", values(oldProblems), values(problems), labels(oldProblems), labels(problems));
        compare(changes, "executionMeasure", account.getExecutionMeasureValue(), measure.value(),
                account.getExecutionMeasureLabelSnapshot(), measure.label());
        compareText(changes, "adjustmentDirection", account.getAdjustmentDirection(), direction);
        compare(changes, "startDate", account.getMaintenanceStartDate(), req.getStartDate(),
                account.getMaintenanceStartDate(), req.getStartDate());
        compare(changes, "endDate", account.getMaintenanceEndDate(), req.getEndDate(),
                account.getMaintenanceEndDate(), req.getEndDate());
        if (changes.isEmpty()) return account.getVersion();

        account.setCurrentStatusValue(status.value()).setCurrentStatusLabelSnapshot(status.label())
                .setSStage(stage.value()).setSStageLabelSnapshot(stage.label())
                .setPrimaryProblemsJson(JsonUtils.toJsonString(problems))
                .setExecutionMeasureValue(measure.value()).setExecutionMeasureLabelSnapshot(measure.label())
                .setAdjustmentDirection(direction).setMaintenanceStartDate(req.getStartDate())
                .setMaintenanceEndDate(req.getEndDate());
        if (accountMapper.updateMaintenance(account, req.getVersion()) == 0) {
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        int revisionNo = revisionMapper.selectMaxRevisionNo(accountId) + 1;
        revisionMapper.insert(new MediaAccountMaintenanceRevisionDO().setAccountId(accountId)
                .setRevisionNo(revisionNo).setCurrentStatusValue(status.value())
                .setCurrentStatusLabelSnapshot(status.label()).setStageValue(stage.value())
                .setStageLabelSnapshot(stage.label()).setPrimaryProblemsJson(JsonUtils.toJsonString(problems))
                .setExecutionMeasureValue(measure.value()).setExecutionMeasureLabelSnapshot(measure.label())
                .setAdjustmentDirection(direction).setStartDate(req.getStartDate()).setEndDate(req.getEndDate())
                .setChangedFieldsJson(JsonUtils.toJsonString(changes.keySet()))
                .setOperatedByUserId(operatorUserId).setOperatedAt(now));
        notifyParticipants(account, operatorUserId, changes, revisionNo);
        return req.getVersion() + 1;
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "read")
    public PageResult<MediaAccountMaintenanceRevisionRespVO> history(Long accountId, PageParam page, Long userId) {
        require(accountId);
        PageResult<MediaAccountMaintenanceRevisionDO> rows = revisionMapper.selectPageByAccountId(page, accountId);
        Map<Long, AdminUserRespDTO> users = userMap(rows.getList().stream()
                .map(MediaAccountMaintenanceRevisionDO::getOperatedByUserId).toList());
        return new PageResult<>(rows.getList().stream().map(row -> toRevision(row, users)).toList(), rows.getTotal());
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "read")
    public PageResult<MediaAccountLegacyStageRespVO> legacyStageHistory(Long accountId, PageParam page, Long userId) {
        require(accountId);
        PageResult<AccountStageLogDO> rows = stageLogMapper.selectPageByAccountId(page, accountId);
        Map<Long, AdminUserRespDTO> users = userMap(rows.getList().stream().map(AccountStageLogDO::getJudgedByUserId).toList());
        return new PageResult<>(rows.getList().stream().map(row -> {
            MediaAccountLegacyStageRespVO vo = new MediaAccountLegacyStageRespVO();
            vo.setId(row.getId()); vo.setFromStage(row.getFromStage()); vo.setToStage(row.getToStage());
            vo.setDirection(row.getDirection()); vo.setJudgmentBasis(row.getJudgmentBasis());
            vo.setJudgedByUserId(row.getJudgedByUserId()); vo.setJudgedAt(row.getJudgedAt());
            vo.setJudgedByUserName(userName(users.get(row.getJudgedByUserId())));
            return vo;
        }).toList(), rows.getTotal());
    }

    public MediaAccountCalendarRespVO calendar(MediaAccountCalendarPageReqVO req, Long userId) {
        if (req.getRangeEnd().isBefore(req.getRangeStart())) throw exception(MEDIA_ACCOUNT_MAINTENANCE_INVALID);
        MediaDataScopeService.Scope scope = mediaDataScopeService.resolve(userId, PERMISSION_CALENDAR_QUERY_ALL);
        return calendarResult(req, scope.userIds(), scope.all());
    }

    public MediaAccountCalendarRespVO allCalendar(MediaAccountCalendarScheduleReqVO req, Long userId) {
        if (req.getRangeEnd().isBefore(req.getRangeStart())) throw exception(MEDIA_ACCOUNT_MAINTENANCE_INVALID);
        MediaAccountCalendarPageReqVO pageReq = new MediaAccountCalendarPageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(PAGE_SIZE_NONE);
        pageReq.setRangeStart(req.getRangeStart());
        pageReq.setRangeEnd(req.getRangeEnd());
        pageReq.setKeyword(req.getKeyword());
        pageReq.setCurrentStatusValue(req.getCurrentStatusValue());
        pageReq.setStageValue(req.getStageValue());
        pageReq.setDirectorUserId(req.getDirectorUserId());
        pageReq.setOperatorUserId(req.getOperatorUserId());
        return calendarResult(pageReq, Set.of(), true);
    }

    public MediaAccountCalendarCandidatesRespVO calendarCandidates(Long userId) {
        MediaAccountCalendarCandidatesRespVO result = new MediaAccountCalendarCandidatesRespVO();
        result.setDirectors(roleUsers(ROLE_CONTENT_DIRECTOR));
        result.setOperators(roleUsers(ROLE_NEW_MEDIA_OPERATOR));
        return result;
    }

    private MediaAccountCalendarRespVO calendarResult(MediaAccountCalendarPageReqVO req,
                                                       Collection<Long> visibleUserIds, boolean all) {
        PageResult<MediaAccountDO> page = accountMapper.selectCalendarPage(req, visibleUserIds, all);
        long unscheduled = accountMapper.selectCalendarUnscheduledCount(req, visibleUserIds, all);
        Map<Long, AdminUserRespDTO> users = userMap(page.getList().stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getDirectorUserId(), row.getOwnerOperatorUserId())).toList());
        Set<Long> personIds = page.getList().stream().map(MediaAccountDO::getStudentPersonId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PersonDO> people = personIds.isEmpty() ? Map.of() : personMapper.selectBatchIds(personIds).stream()
                .collect(Collectors.toMap(PersonDO::getId, Function.identity()));
        MediaAccountCalendarRespVO result = new MediaAccountCalendarRespVO();
        result.setList(page.getList().stream().map(row -> toCalendar(row, users, people)).toList());
        result.setTotal(page.getTotal()); result.setUnscheduledCount(unscheduled);
        return result;
    }

    private List<MediaAccountCalendarCandidatesRespVO.UserRespVO> roleUsers(String roleCode) {
        RoleRespDTO role = roleApi.getRoleByCode(roleCode);
        if (role == null || !Objects.equals(role.getStatus(), CommonStatusEnum.ENABLE.getStatus())) return List.of();
        Set<Long> userIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(role.getId()));
        if (userIds == null || userIds.isEmpty()) return List.of();
        return adminUserApi.getUserList(userIds).stream()
                .filter(user -> user != null && Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .sorted(Comparator.comparing(MediaAccountMaintenanceService::userName)
                        .thenComparing(AdminUserRespDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toCandidate)
                .toList();
    }

    private MediaAccountCalendarCandidatesRespVO.UserRespVO toCandidate(AdminUserRespDTO user) {
        MediaAccountCalendarCandidatesRespVO.UserRespVO vo = new MediaAccountCalendarCandidatesRespVO.UserRespVO();
        vo.setId(user.getId()); vo.setNickname(userName(user)); vo.setUsername(user.getUsername());
        vo.setStatus(user.getStatus()); vo.setDeptId(user.getDeptId());
        return vo;
    }

    private void notifyParticipants(MediaAccountDO account, Long operatorUserId, Map<String, String> changes,
                                    int revisionNo) {
        AdminUserRespDTO operator = adminUserApi.getUser(operatorUserId);
        LinkedHashSet<Long> recipients = new LinkedHashSet<>(Arrays.asList(
                account.getDirectorUserId(), account.getOwnerOperatorUserId()));
        recipients.remove(null); recipients.remove(operatorUserId);
        for (Long recipient : recipients) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bizNo", account.getAccountNo());
            payload.put("accountName", account.getNickname());
            payload.put("operatorName", userName(operator));
            payload.put("changedFields", changes.keySet().stream().map(FIELD_NAMES::get).toList());
            payload.put("changeSummary", String.join("；", changes.values()));
            payload.put("deepLink", account.getStudentPersonId() == null ? "/zsjos/media-students"
                    : "/zsjos/media-students?personId=" + account.getStudentPersonId()
                    + "&tab=accounts&accountId=" + account.getId());
            workflowEventService.notify("media.account.maintenance_changed", BIZ_TYPE_MEDIA_ACCOUNT,
                    account.getId(), recipient, operatorUserId,
                    "media-account-maintenance:" + account.getId() + ":" + revisionNo + ":" + recipient, payload);
        }
    }

    private Snapshot snapshot(String type, String value, String oldValue, String oldLabel) {
        if (Objects.equals(value, oldValue)) return new Snapshot(oldValue, oldLabel);
        if (value == null) return new Snapshot(null, null);
        dictDataApi.validateDictDataList(type, List.of(value));
        String label = dictDataApi.getDictDataList(type).stream().filter(row -> value.equals(row.getValue()))
                .map(DictDataRespDTO::getLabel).findFirst().orElseThrow(() -> exception(MEDIA_ACCOUNT_MAINTENANCE_INVALID));
        return new Snapshot(value, label);
    }

    private List<MediaAccountMaintenanceProblemVO> snapshotProblems(List<String> values) {
        if (values.isEmpty()) return List.of();
        dictDataApi.validateDictDataList(DICT_PRIMARY_PROBLEM, values);
        Map<String, String> labels = dictDataApi.getDictDataList(DICT_PRIMARY_PROBLEM).stream()
                .collect(Collectors.toMap(DictDataRespDTO::getValue, DictDataRespDTO::getLabel, (left, right) -> left));
        return values.stream().map(value -> {
            String label = labels.get(value);
            if (label == null) throw exception(MEDIA_ACCOUNT_MAINTENANCE_INVALID);
            return new MediaAccountMaintenanceProblemVO(value, label);
        }).toList();
    }

    private void validateDates(MediaAccountMaintenanceReqVO req) {
        if ((req.getStartDate() == null) != (req.getEndDate() == null)
                || req.getStartDate() != null && req.getEndDate().isBefore(req.getStartDate())) {
            throw exception(MEDIA_ACCOUNT_MAINTENANCE_INVALID);
        }
    }

    private MediaAccountDO require(Long id) {
        MediaAccountDO account = accountMapper.selectById(id);
        if (account == null) throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        return account;
    }

    private MediaAccountMaintenanceRevisionRespVO toRevision(MediaAccountMaintenanceRevisionDO row,
                                                               Map<Long, AdminUserRespDTO> users) {
        MediaAccountMaintenanceRevisionRespVO vo = new MediaAccountMaintenanceRevisionRespVO();
        vo.setId(row.getId()); vo.setRevisionNo(row.getRevisionNo());
        vo.setCurrentStatusValue(row.getCurrentStatusValue());
        vo.setCurrentStatusLabelSnapshot(row.getCurrentStatusLabelSnapshot());
        vo.setStageValue(row.getStageValue()); vo.setStageLabelSnapshot(row.getStageLabelSnapshot());
        vo.setPrimaryProblems(parseProblems(row.getPrimaryProblemsJson()));
        vo.setExecutionMeasureValue(row.getExecutionMeasureValue());
        vo.setExecutionMeasureLabelSnapshot(row.getExecutionMeasureLabelSnapshot());
        vo.setAdjustmentDirection(row.getAdjustmentDirection()); vo.setStartDate(row.getStartDate());
        vo.setEndDate(row.getEndDate()); vo.setChangedFields(parseStrings(row.getChangedFieldsJson()));
        vo.setOperatedByUserId(row.getOperatedByUserId());
        vo.setOperatedByUserName(userName(users.get(row.getOperatedByUserId()))); vo.setOperatedAt(row.getOperatedAt());
        return vo;
    }

    private MediaAccountCalendarItemRespVO toCalendar(MediaAccountDO row, Map<Long, AdminUserRespDTO> users,
                                                       Map<Long, PersonDO> people) {
        MediaAccountCalendarItemRespVO vo = new MediaAccountCalendarItemRespVO();
        vo.setId(row.getId()); vo.setAccountNo(row.getAccountNo()); vo.setNickname(row.getNickname());
        vo.setPlatformLabelSnapshot(row.getPlatformLabelSnapshot()); vo.setStudentPersonId(row.getStudentPersonId());
        PersonDO person = people.get(row.getStudentPersonId()); vo.setStudentName(person == null ? null : person.getName());
        vo.setDirectorUserId(row.getDirectorUserId()); vo.setDirectorUserName(userName(users.get(row.getDirectorUserId())));
        vo.setOperatorUserId(row.getOwnerOperatorUserId()); vo.setOperatorUserName(userName(users.get(row.getOwnerOperatorUserId())));
        vo.setCurrentStatusValue(row.getCurrentStatusValue()); vo.setCurrentStatusLabelSnapshot(row.getCurrentStatusLabelSnapshot());
        vo.setStageValue(row.getSStage()); vo.setStageLabelSnapshot(row.getSStageLabelSnapshot());
        vo.setStartDate(row.getMaintenanceStartDate()); vo.setEndDate(row.getMaintenanceEndDate());
        return vo;
    }

    private Map<Long, AdminUserRespDTO> userMap(Collection<Long> ids) {
        Set<Long> values = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return values.isEmpty() ? Map.of() : adminUserApi.getUserMap(values);
    }

    private static void compare(Map<String, String> changes, String field, Object before, Object after,
                                Object beforeLabel, Object afterLabel) {
        if (!Objects.equals(before, after)) changes.put(field, FIELD_NAMES.get(field) + "："
                + display(beforeLabel) + " -> " + display(afterLabel));
    }

    private static void compareText(Map<String, String> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) changes.put(field, FIELD_NAMES.get(field) + "：已修改");
    }

    private static String display(Object value) {
        if (value == null || value instanceof Collection<?> collection && collection.isEmpty()) return "未填写";
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf).collect(Collectors.joining("、"));
        return String.valueOf(value);
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static List<String> normalizeValues(List<String> input) {
        if (input == null) return List.of();
        LinkedHashSet<String> values = input.stream().map(MediaAccountMaintenanceService::normalize)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(values);
    }

    private static List<MediaAccountMaintenanceProblemVO> parseProblems(String json) {
        return json == null || json.isBlank() ? List.of()
                : JsonUtils.parseArray(json, MediaAccountMaintenanceProblemVO.class);
    }

    private static List<String> parseStrings(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, String.class);
    }

    private static List<String> values(List<MediaAccountMaintenanceProblemVO> problems) {
        return problems.stream().map(MediaAccountMaintenanceProblemVO::getValue).toList();
    }

    private static List<String> labels(List<MediaAccountMaintenanceProblemVO> problems) {
        return problems.stream().map(MediaAccountMaintenanceProblemVO::getLabelSnapshot).toList();
    }

    private static String userName(AdminUserRespDTO user) {
        return user == null || user.getNickname() == null ? "未知用户" : user.getNickname();
    }

    private record Snapshot(String value, String label) {}
}
