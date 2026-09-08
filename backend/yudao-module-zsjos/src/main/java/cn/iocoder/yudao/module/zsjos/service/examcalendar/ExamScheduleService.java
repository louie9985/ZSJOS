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
                .setRecordStatus("DRAFT");
        applyScope(schedule, req, false);
        normalizeDates(schedule);
        mapper.insert(schedule);
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
        var scope = applyScope(update, req, false);
        if (current.getProductId() != null && Objects.equals(current.getProductId(), req.getProductId())) {
            var requested = req.getSelectedAttrs() == null ? Map.<String, String>of() : req.getSelectedAttrs();
            var cleared = req.getClearedInvalidAttrs() == null ? Set.<String>of() : req.getClearedInvalidAttrs();
            for (var old : parseAttrs(current.getSelectedAttrsJson()).entrySet()) {
                boolean valid = scope.attrs().stream().anyMatch(a -> a.attrKey().equals(old.getKey())
                        && a.values().stream().anyMatch(v -> Objects.equals(v.value(), old.getValue())));
                if (!valid && !requested.containsKey(old.getKey()) && !cleared.contains(old.getKey())) {
                    throw exception(EXAM_SCHEDULE_SCOPE_CLEAR_REQUIRED);
                }
            }
        }
        if (Objects.equals(current.getProductId(), update.getProductId())
                && (current.getProductId() != null || Objects.equals(current.getCategoryId(), update.getCategoryId()))
                && parseAttrs(current.getSelectedAttrsJson()).equals(parseAttrs(update.getSelectedAttrsJson()))) {
            update.setCategoryNameSnapshot(current.getCategoryNameSnapshot()).setCategoryPathSnapshot(current.getCategoryPathSnapshot())
                    .setProductNameSnapshot(current.getProductNameSnapshot()).setSelectedSpecsJson(current.getSelectedSpecsJson());
        }
        normalizeDates(update);
        // Explicit null assignments are needed when switching exact/rough or product/category scopes.
        mapper.update(update, new LambdaUpdateWrapper<ExamScheduleDO>().eq(ExamScheduleDO::getId, id)
                .set(ExamScheduleDO::getExactDate, update.getExactDate())
                .set(ExamScheduleDO::getRoughStartDate, update.getRoughStartDate())
                .set(ExamScheduleDO::getRoughEndDate, update.getRoughEndDate())
                .set(ExamScheduleDO::getProductId, update.getProductId())
                .set(ExamScheduleDO::getProductNameSnapshot, update.getProductNameSnapshot())
                .set(ExamScheduleDO::getSelectedAttrsJson, update.getSelectedAttrsJson())
                .set(ExamScheduleDO::getSelectedSpecsJson, update.getSelectedSpecsJson())
                .set(ExamScheduleDO::getRemark, update.getRemark()));
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "publish")
    public void publish(Long id, Long userId) {
        requireManage(userId);
        ExamScheduleDO current = requireExists(id);
        if (!"DRAFT".equals(current.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
        if ("EXACT".equals(current.getScheduleType()) && current.getExactDate() != null
                && current.getExactDate().isBefore(today())) throw exception(EXAM_SCHEDULE_ENDED_IMMUTABLE);
        ExamScheduleSaveReqVO request = BeanUtils.toBean(current, ExamScheduleSaveReqVO.class);
        if (current.getProductId() != null) request.setCategoryId(null);
        request.setSelectedAttrs(parseAttrs(current.getSelectedAttrsJson()));
        applyScope(current, request, true);
        mapper.updateById(current.setRecordStatus("PUBLISHED").setPublishedAt(LocalDateTime.now(BUSINESS_ZONE)));
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "revoke")
    public void revoke(Long id, Long userId) {
        requireManage(userId);
        ExamScheduleDO current = requireExists(id);
        if (!"PUBLISHED".equals(current.getRecordStatus())) throw exception(EXAM_SCHEDULE_STATE_INVALID);
        mapper.updateById(new ExamScheduleDO().setId(id).setRecordStatus("REVOKED"));
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
        response.setCategoryPathSnapshot(schedule.getCategoryPathSnapshot() == null ? List.of()
                : JsonUtils.parseArray(schedule.getCategoryPathSnapshot(), ZsjosProductCategoryPathNodeVO.class));
        response.setDisplayStatus(displayStatus(schedule, today(), configuredUpcomingDays()));
        response.setSelectedAttrs(parseAttrs(schedule.getSelectedAttrsJson()));
        response.setSelectedSpecs(schedule.getSelectedSpecsJson() == null ? List.of()
                : JsonUtils.parseArray(schedule.getSelectedSpecsJson(), ProductSpecVO.class));
        response.setFrozenSkus(schedule.getFrozenSkusJson() == null ? List.of()
                : JsonUtils.parseArray(schedule.getFrozenSkusJson(), ExamProductScopeRespVO.Sku.class));
        String name = schedule.getProductId() == null ? schedule.getCategoryNameSnapshot() : schedule.getProductNameSnapshot();
        response.setScheduleName(name + response.getSelectedSpecs().stream().map(s -> "，" + s.displayText())
                .collect(java.util.stream.Collectors.joining()));
        return response;
    }

    private ExamProductScopeRespVO applyScope(ExamScheduleDO target, ExamScheduleSaveReqVO req, boolean publishing) {
        if (req.getProductId() == null) {
            if (req.getCategoryId() == null || req.getSelectedAttrs() != null && !req.getSelectedAttrs().isEmpty()) {
                throw exception(EXAM_SCHEDULE_SCOPE_INVALID);
            }
            CategorySnapshot category = snapshotCategory(req.getCategoryId());
            target.setCategoryId(req.getCategoryId()).setCategoryNameSnapshot(category.name())
                    .setCategoryPathSnapshot(JsonUtils.toJsonString(category.path()));
            return null;
        }
        if (req.getCategoryId() != null) throw exception(EXAM_SCHEDULE_SCOPE_INVALID);
        ExamProductScopeRespVO scope = productSkuService.resolveExamScope(req.getProductId(), req.getSelectedAttrs());
        target.setProductId(scope.productId()).setProductNameSnapshot(scope.productName())
                .setCategoryId(scope.categoryId()).setCategoryNameSnapshot(scope.categoryPath().getLast().name())
                .setCategoryPathSnapshot(JsonUtils.toJsonString(scope.categoryPath()))
                .setSelectedAttrsJson(JsonUtils.toJsonString(req.getSelectedAttrs() == null ? Map.of() : new TreeMap<>(req.getSelectedAttrs())))
                .setSelectedSpecsJson(JsonUtils.toJsonString(scope.selectedSpecs()));
        if (publishing) target.setFrozenSkusJson(JsonUtils.toJsonString(scope.skus()));
        return scope;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseAttrs(String json) {
        return json == null ? Map.of() : JsonUtils.parseObject(json, Map.class);
    }

    private CategorySnapshot snapshotCategory(Long categoryId) {
        var allById = categoryLocks.paths(List.of(categoryId));
        ZsjosProductCategoryDO category = allById.get(categoryId);
        if (category == null || !CommonStatusEnum.ENABLE.getStatus().equals(category.getStatus())) {
            throw exception(EXAM_SCHEDULE_CATEGORY_INVALID);
        }
        if (allById.values().stream().anyMatch(row -> !CommonStatusEnum.ENABLE.getStatus().equals(row.getStatus()))) {
            throw exception(EXAM_SCHEDULE_CATEGORY_INVALID);
        }
        return new CategorySnapshot(category.getName(), buildPath(category, allById));
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
