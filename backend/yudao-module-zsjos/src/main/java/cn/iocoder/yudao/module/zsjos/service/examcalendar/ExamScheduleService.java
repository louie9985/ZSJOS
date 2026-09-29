package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleObjectPermissionProvider.BIZ_TYPE;

@Service
public class ExamScheduleService {
    public static final String PERMISSION_MANAGE = "zsjos:exam-calendar:manage";
    public static final String UPCOMING_DAYS_CONFIG_KEY = "zsjos.exam-calendar.upcoming-days";
    public static final int DEFAULT_UPCOMING_DAYS = 3;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TYPES = Set.of("EXACT", "ROUGH");

    @Resource private ExamScheduleMapper mapper;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ConfigApi configApi;
    @Resource private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService notificationSnapshots;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.product.ProductCategoryLocks categoryLocks;

    public List<ExamProductScopeRespVO> productOptions(Long userId) {
        requireManage(userId);
        return productSkuService.getExamProductOptions();
    }

    public PageResult<ExamScheduleRespVO> exactPage(ExamSchedulePageReqVO req, Long userId) {
        validateQueryRange(req);
        boolean manager = hasManagePermission(userId);
        List<ExamScheduleRespVO> filtered = mapper.selectExactList(req, manager).stream()
                .map(this::toResponse)
                .filter(row -> req.getDisplayStatus() == null || req.getDisplayStatus().isBlank()
                        || row.getDisplayStatus().equalsIgnoreCase(req.getDisplayStatus()))
                .toList();
        int from = Math.min((req.getPageNo() - 1) * req.getPageSize(), filtered.size());
        int to = Math.min(from + req.getPageSize(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), (long) filtered.size());
    }

    public PageResult<ExamScheduleRespVO> roughPage(ExamSchedulePageReqVO req, Long userId) {
        validateQueryRange(req);
        PageResult<ExamScheduleDO> page = mapper.selectRoughPage(req, hasManagePermission(userId));
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }

    public List<ExamCategoryOptionRespVO> categoryOptions() {
        List<ZsjosProductCategoryDO> all = categoryMapper.selectList();
        List<ZsjosProductCategoryDO> categories = all.stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .sorted(Comparator.comparing(ZsjosProductCategoryDO::getSort)
                        .thenComparing(ZsjosProductCategoryDO::getId))
                .toList();
        Map<Long, ZsjosProductCategoryDO> allById = new HashMap<>();
        all.forEach(item -> allById.put(item.getId(), item));
        return categories.stream().map(item -> new ExamCategoryOptionRespVO(
                item.getId(), item.getName(), buildPath(item, allById))).toList();
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long create(ExamScheduleSaveReqVO req, Long userId) {
        requireManage(userId);
        validateSchedule(req);
        ExamScheduleDO schedule = BeanUtils.toBean(req, ExamScheduleDO.class)
                .setScheduleType(req.getScheduleType().toUpperCase(Locale.ROOT))
                .setRecordStatus("DRAFT").setCalendarVersion(1);
        applyFreeName(schedule, req);
        normalizeDates(schedule);
        mapper.insert(schedule);
        notificationSnapshots.captureExam(schedule, "CREATED");
        return schedule.getId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "update")
    public void update(Long id, ExamScheduleSaveReqVO req, Long userId) {
        requireManage(userId);
        ExamScheduleDO current = requireExists(id);
        if (!"DRAFT".equals(current.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
        if ("EXACT".equals(current.getScheduleType()) && current.getExactDate() != null
                && current.getExactDate().isBefore(today())) throw exception(EXAM_SCHEDULE_ENDED_IMMUTABLE);
        validateSchedule(req);
        rejectPastExactDate(req);
        ExamScheduleDO update = BeanUtils.toBean(req, ExamScheduleDO.class)
                .setId(id)
                .setScheduleType(req.getScheduleType().toUpperCase(Locale.ROOT));
        applyFreeName(update, req);
        normalizeDates(update);
        int version = current.getCalendarVersion() == null ? 1 : current.getCalendarVersion();
        update.setCalendarVersion(sameNotificationContent(current, update) ? version : version + 1);
        notificationSnapshots.captureExam(current, "MANUAL");
        // Clear obsolete catalog links explicitly; MyBatis skips null entity fields.
        mapper.update(update, new LambdaUpdateWrapper<ExamScheduleDO>().eq(ExamScheduleDO::getId, id)
                .set(ExamScheduleDO::getExactDate, update.getExactDate())
                .set(ExamScheduleDO::getRoughStartDate, update.getRoughStartDate())
                .set(ExamScheduleDO::getRoughEndDate, update.getRoughEndDate())
                .set(ExamScheduleDO::getCategoryId, null)
                .set(ExamScheduleDO::getCategoryNameSnapshot, null)
                .set(ExamScheduleDO::getCategoryPathSnapshot, null)
                .set(ExamScheduleDO::getFrozenSkusJson, null)
                .set(ExamScheduleDO::getProductId, null)
                .set(ExamScheduleDO::getProductNameSnapshot, update.getProductNameSnapshot())
                .set(ExamScheduleDO::getSelectedAttrsJson, update.getSelectedAttrsJson())
                .set(ExamScheduleDO::getSelectedSpecsJson, update.getSelectedSpecsJson())
                .set(ExamScheduleDO::getRemark, update.getRemark()));
        update.setRecordStatus(current.getRecordStatus());
        notificationSnapshots.captureExam(update, "UPDATED");
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "publish")
    public void publish(Long id, Long userId) {
        requireManage(userId);
        ExamScheduleDO current = requireExists(id);
        if (!"DRAFT".equals(current.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
        if ("EXACT".equals(current.getScheduleType()) && current.getExactDate() != null
                && current.getExactDate().isBefore(today())) throw exception(EXAM_SCHEDULE_ENDED_IMMUTABLE);
        notificationSnapshots.captureExam(current, "MANUAL");
        mapper.updateById(current.setRecordStatus("PUBLISHED").setPublishedAt(LocalDateTime.now(BUSINESS_ZONE))
                .setCalendarVersion((current.getCalendarVersion() == null ? 1 : current.getCalendarVersion()) + 1));
        notificationSnapshots.captureExam(current, "PUBLISHED");
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "revoke")
    public void revoke(Long id, Long userId) {
        requireManage(userId);
        ExamScheduleDO current = requireExists(id);
        if (!"PUBLISHED".equals(current.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
        notificationSnapshots.captureExam(current, "MANUAL");
        mapper.updateById(new ExamScheduleDO().setId(id).setRecordStatus("REVOKED")
                .setCalendarVersion((current.getCalendarVersion() == null ? 1 : current.getCalendarVersion()) + 1));
        current.setRecordStatus("REVOKED").setCalendarVersion((current.getCalendarVersion() == null ? 1 : current.getCalendarVersion()) + 1);
        notificationSnapshots.captureExam(current, "REVOKED");
    }

    public ExamScheduleDO previewTransition(Long id, String event, Long userId) {
        requireManage(userId);
        var stored = mapper.selectById(id);
        if (stored == null) throw exception(EXAM_SCHEDULE_NOT_EXISTS);
        var row = BeanUtils.toBean(stored, ExamScheduleDO.class);
        if ("PUBLISHED".equals(event)) {
            if (!"DRAFT".equals(row.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
            if ("EXACT".equals(row.getScheduleType()) && row.getExactDate() != null && row.getExactDate().isBefore(today()))
                throw exception(EXAM_SCHEDULE_ENDED_IMMUTABLE);
            row.setRecordStatus("PUBLISHED");
        } else if ("REVOKED".equals(event)) {
            if (!"PUBLISHED".equals(row.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
            row.setRecordStatus("REVOKED");
        } else throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        return row.setCalendarVersion((stored.getCalendarVersion() == null ? 1 : stored.getCalendarVersion()) + 1);
    }

    private boolean sameNotificationContent(ExamScheduleDO before, ExamScheduleDO after) {
        return Objects.equals(before.getScheduleName(), after.getScheduleName())
                && Objects.equals(before.getScheduleType(), after.getScheduleType())
                && Objects.equals(before.getExactDate(), after.getExactDate())
                && Objects.equals(before.getRoughStartDate(), after.getRoughStartDate())
                && Objects.equals(before.getRoughEndDate(), after.getRoughEndDate())
                && Objects.equals(before.getProductId(), after.getProductId())
                && (before.getProductId() != null || Objects.equals(before.getCategoryId(), after.getCategoryId()))
                && Objects.equals(before.getProductNameSnapshot(), after.getProductNameSnapshot())
                && Objects.equals(before.getCategoryNameSnapshot(), after.getCategoryNameSnapshot())
                && Objects.equals(before.getCategoryPathSnapshot(), after.getCategoryPathSnapshot())
                && parseAttrs(before.getSelectedAttrsJson()).equals(parseAttrs(after.getSelectedAttrsJson()))
                && Objects.equals(before.getSelectedSpecsJson(), after.getSelectedSpecsJson())
                && Objects.equals(before.getRemark(), after.getRemark());
    }

    String displayStatus(ExamScheduleDO schedule, LocalDate currentDate, int upcomingDays) {
        if (!"PUBLISHED".equals(schedule.getRecordStatus()) || !"EXACT".equals(schedule.getScheduleType())) {
            return schedule.getRecordStatus();
        }
        LocalDate examDate = schedule.getExactDate();
        if (currentDate.isAfter(examDate)) return "ENDED";
        if (currentDate.isEqual(examDate)) return "IN_PROGRESS";
        if (!currentDate.isBefore(examDate.minusDays(upcomingDays))) return "UPCOMING";
        return "PUBLISHED";
    }

    int configuredUpcomingDays() {
        try {
            int value = Integer.parseInt(configApi.getConfigValueByKey(UPCOMING_DAYS_CONFIG_KEY));
            return value >= 0 ? value : DEFAULT_UPCOMING_DAYS;
        } catch (RuntimeException ignored) {
            return DEFAULT_UPCOMING_DAYS;
        }
    }

    private ExamScheduleRespVO toResponse(ExamScheduleDO schedule) {
        // JSON snapshots have different HTTP shapes; generic bean conversion cannot decode them.
        ExamScheduleRespVO response = new ExamScheduleRespVO();
        response.setId(schedule.getId()); response.setScheduleType(schedule.getScheduleType());
        response.setExactDate(schedule.getExactDate()); response.setRoughStartDate(schedule.getRoughStartDate());
        response.setRoughEndDate(schedule.getRoughEndDate()); response.setCategoryId(schedule.getCategoryId());
        response.setCategoryNameSnapshot(schedule.getCategoryNameSnapshot()); response.setProductId(schedule.getProductId());
        response.setProductNameSnapshot(schedule.getProductNameSnapshot()); response.setRecordStatus(schedule.getRecordStatus());
        response.setRemark(schedule.getRemark()); response.setPublishedAt(schedule.getPublishedAt());
        response.setCreateTime(schedule.getCreateTime()); response.setUpdateTime(schedule.getUpdateTime());
        response.setCalendarVersion(schedule.getCalendarVersion() == null ? 1 : schedule.getCalendarVersion());
        response.setCategoryPathSnapshot(schedule.getCategoryPathSnapshot() == null ? List.of()
                : JsonUtils.parseArray(schedule.getCategoryPathSnapshot(), ZsjosProductCategoryPathNodeVO.class));
        response.setDisplayStatus(displayStatus(schedule, today(), configuredUpcomingDays()));
        response.setSelectedAttrs(parseAttrs(schedule.getSelectedAttrsJson()));
        response.setSelectedSpecs(schedule.getSelectedSpecsJson() == null ? List.of()
                : JsonUtils.parseArray(schedule.getSelectedSpecsJson(), ProductSpecVO.class));
        response.setFrozenSkus(schedule.getFrozenSkusJson() == null ? List.of()
                : JsonUtils.parseArray(schedule.getFrozenSkusJson(), ExamProductScopeRespVO.Sku.class));
        response.setScheduleName(displayName(schedule));
        return response;
    }

    private void applyFreeName(ExamScheduleDO target, ExamScheduleSaveReqVO req) {
        target.setScheduleName(req.getScheduleName().trim()).setProductId(null).setCategoryId(null)
                .setProductNameSnapshot(null).setCategoryNameSnapshot(null).setCategoryPathSnapshot(null)
                .setSelectedAttrsJson(null).setSelectedSpecsJson(null).setFrozenSkusJson(null);
    }

    public static String displayName(ExamScheduleDO schedule) {
        if (schedule.getScheduleName() != null) return schedule.getScheduleName();
        // Legacy records retain their original stored labels without consulting today's catalog.
        String name = schedule.getProductId() == null ? schedule.getCategoryNameSnapshot() : schedule.getProductNameSnapshot();
        var specs = schedule.getSelectedSpecsJson() == null ? List.<ProductSpecVO>of()
                : JsonUtils.parseArray(schedule.getSelectedSpecsJson(), ProductSpecVO.class);
        return (name == null ? "未命名考期" : name) + specs.stream().map(spec -> "，" + spec.displayText())
                .collect(java.util.stream.Collectors.joining());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseAttrs(String json) {
        return json == null ? Map.of() : JsonUtils.parseObject(json, Map.class);
    }

    private List<ZsjosProductCategoryPathNodeVO> buildPath(ZsjosProductCategoryDO category,
                                                            Map<Long, ZsjosProductCategoryDO> allById) {
        LinkedList<ZsjosProductCategoryPathNodeVO> path = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        ZsjosProductCategoryDO current = category;
        while (current != null && visited.add(current.getId())) {
            path.addFirst(new ZsjosProductCategoryPathNodeVO(current.getId(), current.getName()));
            current = Objects.equals(current.getParentId(), 0L) ? null : allById.get(current.getParentId());
        }
        return List.copyOf(path);
    }

    private void validateSchedule(ExamScheduleSaveReqVO req) {
        String type = req.getScheduleType() == null ? "" : req.getScheduleType().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw exception(EXAM_SCHEDULE_TYPE_INVALID);
        if ("EXACT".equals(type)) {
            if (req.getExactDate() == null || req.getRoughStartDate() != null || req.getRoughEndDate() != null) {
                throw exception(EXAM_SCHEDULE_TIME_INVALID);
            }
        } else if (req.getExactDate() != null || req.getRoughStartDate() == null || req.getRoughEndDate() == null
                || req.getRoughEndDate().isBefore(req.getRoughStartDate())) {
            throw exception(EXAM_SCHEDULE_TIME_INVALID);
        }
    }

    private void validateQueryRange(ExamSchedulePageReqVO req) {
        if (req.getRangeStart() != null && req.getRangeEnd() != null
                && req.getRangeEnd().isBefore(req.getRangeStart())) throw exception(EXAM_SCHEDULE_TIME_INVALID);
    }

    private void rejectPastExactDate(ExamScheduleSaveReqVO req) {
        if ("EXACT".equalsIgnoreCase(req.getScheduleType()) && req.getExactDate().isBefore(today())) {
            throw exception(EXAM_SCHEDULE_ENDED_IMMUTABLE);
        }
    }

    private void normalizeDates(ExamScheduleDO schedule) {
        if ("EXACT".equals(schedule.getScheduleType())) {
            schedule.setRoughStartDate(null).setRoughEndDate(null);
        } else {
            schedule.setExactDate(null);
        }
    }

    private ExamScheduleDO requireExists(Long id) {
        ExamScheduleDO schedule = mapper.selectForUpdate(id);
        if (schedule == null) throw exception(EXAM_SCHEDULE_NOT_EXISTS);
        return schedule;
    }

    private boolean hasManagePermission(Long userId) {
        return permissionApi.hasAnyPermissions(userId, PERMISSION_MANAGE);
    }

    private void requireManage(Long userId) {
        if (!hasManagePermission(userId)) throw exception(EXAM_SCHEDULE_PERMISSION_DENIED);
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private record CategorySnapshot(String name, List<ZsjosProductCategoryPathNodeVO> path) {}
}
