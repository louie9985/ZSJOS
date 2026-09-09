package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class DeliveryClassServiceImpl implements DeliveryClassService {
    private static final List<String> STUDENT_TASK_TYPES = List.of(
            StudentContactConstants.TYPE_FIRST_CONTACT, StudentContactConstants.TYPE_STUDY_PLAN,
            StudentContactConstants.TYPE_CONTACT, StudentContactConstants.TYPE_ASSISTANCE);
    @Resource private DeliveryClassMapper mapper;
    @Resource private ExamScheduleMapper scheduleMapper;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private RoleApi roleApi;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private DeliveryClassScopeService scopeService;
    @Resource private DeliveryClassNumberService numberService;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private SalesOrderItemMapper orderItemMapper;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private PersonMapper personMapper;
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private DeliveryClassNotifyPublisher notifyPublisher;

    @Override
    public PageResult<DeliveryClassRespVO> getManagedPage(Long userId, DeliveryClassPageReqVO req) {
        DeliveryClassScopeService.Scope scope = scopeService.resolve(userId);
        PageResult<DeliveryClassDO> page = mapper.selectPage(req, scope.deptIds(), null,
                scope.allDepartments(), true);
        return toPageResult(page);
    }

    @Override
    public PageResult<DeliveryClassRespVO> getMyPage(Long userId, DeliveryClassPageReqVO req) {
        PageResult<DeliveryClassDO> page = mapper.selectPage(req, Set.of(), userId, false, false);
        return toPageResult(page);
    }

    @Override
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "read")
    public DeliveryClassRespVO get(Long id, Long userId) { return toVO(require(id)); }

    private PageResult<DeliveryClassRespVO> toPageResult(PageResult<DeliveryClassDO> page) {
        List<DeliveryClassDO> rows = page.getList();
        Map<Long, ExamScheduleDO> schedules = new HashMap<>();
        Set<Long> scheduleIds = rows.stream().map(DeliveryClassDO::getExamScheduleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!scheduleIds.isEmpty()) scheduleMapper.selectBatchIds(scheduleIds)
                .forEach(schedule -> schedules.put(schedule.getId(), schedule));
        Map<Long, Integer> studentCounts = new HashMap<>();
        Map<Long, List<Long>> idsByTenant = rows.stream().collect(Collectors.groupingBy(
                DeliveryClassDO::getTenantId, Collectors.mapping(DeliveryClassDO::getId, Collectors.toList())));
        idsByTenant.forEach((tenantId, classIds) -> mapper.countStudentsByClassIds(tenantId, classIds).forEach(item ->
                studentCounts.put(((Number) item.get("classId")).longValue(), ((Number) item.get("studentCount")).intValue())));
        return new PageResult<>(rows.stream().map(row -> toVO(row, schedules.get(row.getExamScheduleId()),
                studentCounts.getOrDefault(row.getId(), 0))).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "read")
    public PageResult<DeliveryClassStudentRespVO> students(Long id, Long userId, PageParam pageParam) {
        require(id);
        PageResult<ServiceRelationDO> page = relationMapper.selectPage(pageParam,
                new LambdaQueryWrapperX<ServiceRelationDO>().eq(ServiceRelationDO::getClassId, id)
                        .in(ServiceRelationDO::getStatus, List.of("active", "paused", "completed"))
                        .orderByDesc(ServiceRelationDO::getActivatedAt).orderByDesc(ServiceRelationDO::getId));
        return new PageResult<>(page.getList().stream().map(this::student).toList(), page.getTotal());
    }

    @Override
    public List<DeliveryClassOptionRespVO> options(Long categoryId, boolean includePending) {
        List<DeliveryClassDO> rows = mapper.selectList(new LambdaQueryWrapperX<DeliveryClassDO>()
                .and(q -> q.eq(DeliveryClassDO::getSystemClass, true)
                        .or().and(formal -> formal.eq(DeliveryClassDO::getSystemClass, false)
                                .eq(DeliveryClassDO::getCategoryId, categoryId)
                                .eq(DeliveryClassDO::getStatus, "SERVING")))
                .orderByAsc(DeliveryClassDO::getSystemClass).orderByAsc(DeliveryClassDO::getClassName));
        return rows.stream().filter(row -> includePending || !Boolean.TRUE.equals(row.getSystemClass()))
                .map(this::toOption).toList();
    }

    @Override
    public List<HomeroomCandidateRespVO> homeroomCandidates(Long userId) {
        AdminUserRespDTO manager = adminUserApi.getUser(userId);
        if (manager == null || manager.getDeptId() == null) return List.of();
        RoleRespDTO plannerRole = roleApi.getRoleByCode("study_planner");
        if (plannerRole == null || !Objects.equals(plannerRole.getStatus(), CommonStatusEnum.ENABLE.getStatus())) return List.of();
        Set<Long> plannerUserIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(plannerRole.getId()));
        Collection<AdminUserRespDTO> users = adminUserApi.getUserListByDeptIds(Set.of(manager.getDeptId()));
        Map<Long, DeptRespDTO> depts = deptApi.getDeptMap(users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return users.stream().filter(Objects::nonNull)
                .filter(user -> Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .filter(user -> plannerUserIds.contains(user.getId()))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> {
                    HomeroomCandidateRespVO row = new HomeroomCandidateRespVO();
                    row.setId(user.getId()); row.setName(user.getNickname()); row.setDeptId(user.getDeptId());
                    DeptRespDTO dept = depts.get(user.getDeptId()); row.setDeptName(dept == null ? null : dept.getName());
                    return row;
                }).toList();
    }

    @Override
    public List<ExamProductScopeRespVO> productOptions() {
        return productSkuService.getExamProductOptions();
    }

    @Override
    public List<DeliveryClassCategoryOptionRespVO> categoryOptions() {
        return categoryMapper.selectList(new LambdaQueryWrapperX<ZsjosProductCategoryDO>()
                        .eq(ZsjosProductCategoryDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                        .orderByAsc(ZsjosProductCategoryDO::getLevel).orderByAsc(ZsjosProductCategoryDO::getSort))
                .stream().map(row -> new DeliveryClassCategoryOptionRespVO(row.getId(), row.getParentId(), row.getName()))
                .toList();
    }

    @Override
    public List<DeliveryClassExamOptionRespVO> examOptions(Long categoryId, Long productId, String selectedAttrsJson) {
        Map<String, String> selectedAttrs = selectedAttrsJson == null || selectedAttrsJson.isBlank()
                ? Map.of() : JsonUtils.parseObject(selectedAttrsJson, Map.class);
        ExamProductScopeRespVO requested = productId == null ? null : productSkuService.resolveExamScope(productId, selectedAttrs);
        if (requested != null && !Objects.equals(categoryId, requested.categoryId())) {
            throw exception(DELIVERY_CLASS_CATEGORY_INVALID);
        }
        return scheduleMapper.selectList(new LambdaQueryWrapperX<ExamScheduleDO>()
                .eq(ExamScheduleDO::getCategoryId, categoryId)
                .eq(ExamScheduleDO::getRecordStatus, "PUBLISHED")
                .orderByDesc(ExamScheduleDO::getPublishedAt).orderByDesc(ExamScheduleDO::getId))
                .stream().filter(this::isUnended).filter(row -> scheduleCovers(row, requested))
                .map(row -> new DeliveryClassExamOptionRespVO(row.getId(), row.getScheduleType(),
                        "EXACT".equals(row.getScheduleType()) ? String.valueOf(row.getExactDate())
                                : row.getRoughStartDate() + "~" + row.getRoughEndDate(), row.getProductId(),
                        row.getCategoryId(), parseSkus(row.getFrozenSkusJson()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeliveryClassSaveReqVO req, Long userId) {
        ScopeSnapshot snapshot = validate(req);
        AdminUserRespDTO homeroom = requireHomeroom(req.getHomeroomUserId());
        requireSameDepartment(userId, homeroom);
        DeptRespDTO dept = homeroom.getDeptId() == null ? null : deptApi.getDept(homeroom.getDeptId());
        for (int retry = 0; retry < 20; retry++) {
            String classNo = numberService.next();
            DeliveryClassDO row = new DeliveryClassDO().setClassNo(classNo)
                    .setClassName(blank(req.getClassName()) ? snapshot.categoryName() + snapshot.scheduleSnapshot()
                            + classNo.substring(classNo.length() - 4) + "班" : req.getClassName().trim())
                    .setSystemClass(false).setProductId(snapshot.scope().productId()).setProductNameSnapshot(snapshot.scope().productName())
                    .setSelectedAttrsJson(JsonUtils.toJsonString(req.getSelectedAttrs() == null ? Map.of() : req.getSelectedAttrs()))
                    .setSelectedSpecsJson(JsonUtils.toJsonString(snapshot.scope().selectedSpecs()))
                    .setSelectedSkusJson(JsonUtils.toJsonString(snapshot.scope().skus())).setCategoryId(snapshot.scope().categoryId())
                    .setCategoryNameSnapshot(snapshot.categoryName()).setCategoryPathSnapshot(snapshot.categoryPath())
                    .setExamScheduleId(req.getExamScheduleId()).setExamScheduleSnapshot(snapshot.scheduleSnapshot())
                    .setHomeroomUserId(homeroom.getId()).setHomeroomUserNameSnapshot(homeroom.getNickname())
                    .setDeptId(homeroom.getDeptId()).setDeptNameSnapshot(dept == null ? null : dept.getName())
                    .setStatus("SERVING").setVersion(0);
            try { mapper.insert(row); return row.getId(); }
            catch (DuplicateKeyException conflict) { if (retry == 19) throw exception(DELIVERY_CLASS_VERSION_CONFLICT); }
        }
        throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
    }

    @Override
    @ZsjosAudit(action = "delivery-class.update", targetType = BIZ_TYPE)
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "update")
    public void update(Long id, DeliveryClassSaveReqVO req, Long userId) {
        DeliveryClassDO current = mapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (current == null) throw exception(DELIVERY_CLASS_NOT_EXISTS);
        if (Boolean.TRUE.equals(current.getSystemClass()) || "COMPLETED".equals(current.getStatus())) {
            throw exception(DELIVERY_CLASS_STATE_INVALID);
        }
        if (!Objects.equals(req.getVersion(), current.getVersion())) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
        ScopeSnapshot snapshot = validate(req);
        if (!Objects.equals(snapshot.scope().categoryId(), current.getCategoryId())
                && mapper.countAllRelations(current.getTenantId(), id) > 0) throw exception(DELIVERY_CLASS_CATEGORY_LOCKED);
        AdminUserRespDTO homeroom = requireHomeroom(req.getHomeroomUserId());
        requireSameDepartment(userId, homeroom);
        if (!Objects.equals(homeroom.getDeptId(), current.getDeptId())) throw exception(DELIVERY_CLASS_USER_INVALID);
        Long oldOwner = current.getHomeroomUserId();
        current.setClassName(blank(req.getClassName()) ? current.getClassName() : req.getClassName().trim())
                .setProductId(snapshot.scope().productId()).setProductNameSnapshot(snapshot.scope().productName())
                .setSelectedAttrsJson(JsonUtils.toJsonString(req.getSelectedAttrs() == null ? Map.of() : req.getSelectedAttrs()))
                .setSelectedSpecsJson(JsonUtils.toJsonString(snapshot.scope().selectedSpecs()))
                .setSelectedSkusJson(JsonUtils.toJsonString(snapshot.scope().skus())).setCategoryId(snapshot.scope().categoryId())
                .setCategoryNameSnapshot(snapshot.categoryName()).setCategoryPathSnapshot(snapshot.categoryPath()).setExamScheduleId(req.getExamScheduleId())
                .setExamScheduleSnapshot(snapshot.scheduleSnapshot()).setHomeroomUserId(homeroom.getId())
                .setHomeroomUserNameSnapshot(homeroom.getNickname()).setVersion(current.getVersion() + 1);
        if (mapper.updateById(current) != 1) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
        if (!Objects.equals(oldOwner, homeroom.getId())) {
            for (ServiceRelationDO relation : relationMapper.selectClassRelationsForUpdate(id, current.getTenantId())) {
                transferLocked(relation, current, homeroom.getId(), "班主任变更",
                        "homeroom-change:" + id + ":relation:" + relation.getId() + ":v" + relation.getVersion());
            }
        }
    }

    @Override
    @ZsjosAudit(action = "delivery-class.complete", targetType = BIZ_TYPE)
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE, bizId = "#id", action = "complete")
    public void complete(Long id, Long userId) {
        DeliveryClassDO row = mapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (row == null) throw exception(DELIVERY_CLASS_NOT_EXISTS);
        if (Boolean.TRUE.equals(row.getSystemClass()) || !"SERVING".equals(row.getStatus())) {
            throw exception(DELIVERY_CLASS_STATE_INVALID);
        }
        row.setStatus("COMPLETED").setVersion(row.getVersion() + 1);
        if (mapper.updateById(row) != 1) throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
    }

    @Override
    @ZsjosAudit(action = "delivery-class.direct-transfer", targetType = "service-relation")
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "direct-transfer")
    public void directTransfer(Long relationId, DeliveryClassDirectTransferReqVO req, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId, TenantContextHolder.getRequiredTenantId());
        if (relation == null || !List.of("active", "paused", "completed").contains(relation.getStatus())) {
            throw exception(STUDENT_SERVICE_NOT_EXISTS);
        }
        if (!Objects.equals(relation.getVersion(), req.getVersion())) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        DeliveryClassDO target = mapper.selectByIdForUpdate(req.getTargetClassId(), relation.getTenantId());
        validateTransferTarget(relation, target);
        DeliveryClassDO source = require(relation.getClassId());
        if ((!Boolean.TRUE.equals(source.getSystemClass()) && !scopeService.contains(userId, source.getDeptId()))
                || !scopeService.contains(userId, target.getDeptId())) throw exception(DELIVERY_CLASS_PERMISSION_DENIED);
        transferLocked(relation, target, target.getHomeroomUserId(), req.getReason().trim(),
                "direct-transfer:" + relationId + ":v" + relation.getVersion());
    }

    public void transferApproved(ServiceRelationDO relation, DeliveryClassDO target, String eventKey, String reason) {
        validateTransferTarget(relation, target);
        transferLocked(relation, target, target.getHomeroomUserId(), reason, eventKey);
    }

    private void validateTransferTarget(ServiceRelationDO relation, DeliveryClassDO target) {
        if (target == null || Boolean.TRUE.equals(target.getSystemClass()) || !"SERVING".equals(target.getStatus())
                || Objects.equals(relation.getClassId(), target.getId())) throw exception(DELIVERY_CLASS_TRANSFER_INVALID);
        DeliveryClassDO source = require(relation.getClassId());
        Long sourceCategoryId = source.getCategoryId();
        if (Boolean.TRUE.equals(source.getSystemClass())) {
            SalesOrderItemDO orderItem = relation.getOrderItemId() == null ? null
                    : orderItemMapper.selectById(relation.getOrderItemId());
            ZsjosProductDO product = orderItem == null || orderItem.getProductId() == null ? null
                    : productMapper.selectById(orderItem.getProductId());
            sourceCategoryId = product == null ? null : product.getCategoryId();
        }
        if (sourceCategoryId == null || !Objects.equals(sourceCategoryId, target.getCategoryId())) {
            throw exception(DELIVERY_CLASS_TRANSFER_INVALID);
        }
        validateHomeroom(target.getHomeroomUserId());
    }

    private void transferLocked(ServiceRelationDO relation, DeliveryClassDO target, Long ownerId,
                                String reason, String eventKey) {
        Long oldOwner = relation.getOwnerUserId();
        if (relationMapper.transferClass(relation.getId(), target.getId(), ownerId, relation.getVersion()) != 1) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        taskCommandService.reassignPending(STUDENT_TASK_TYPES, relation.getId(), ownerId);
        notifyPublisher.publishOwnerChanged(eventKey, relation.getId(), target.getId(), oldOwner, ownerId, reason);
    }

    private DeliveryClassStudentRespVO student(ServiceRelationDO relation) {
        PersonDO person = personMapper.selectById(relation.getPersonId());
        DeliveryClassStudentRespVO row = new DeliveryClassStudentRespVO();
        row.setServiceRelationId(relation.getId()); row.setPersonId(relation.getPersonId());
        if (person != null) { row.setPersonNo(person.getPersonNo()); row.setStudentName(person.getName()); }
        SalesOrderItemDO orderItem = relation.getOrderItemId() == null ? null
                : orderItemMapper.selectById(relation.getOrderItemId());
        ZsjosProductDO product = orderItem == null || orderItem.getProductId() == null ? null
                : productMapper.selectById(orderItem.getProductId());
        if (product != null && product.getCategoryId() != null) {
            row.setCategoryId(product.getCategoryId());
            ZsjosProductCategoryDO category = categoryMapper.selectById(product.getCategoryId());
            row.setCategoryName(category == null ? null : category.getName());
        }
        row.setServiceStatus(relation.getStatus()); row.setAcceptanceStatus(relation.getAcceptanceStatus());
        row.setOwnerUserId(relation.getOwnerUserId()); row.setActivatedAt(relation.getActivatedAt());
        row.setVersion(relation.getVersion());
        if (relation.getOwnerUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(relation.getOwnerUserId());
            row.setOwnerUserName(user == null ? null : user.getNickname());
        }
        return row;
    }

    @Override
    public AdminUserRespDTO validateHomeroom(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        RoleRespDTO plannerRole = roleApi.getRoleByCode("study_planner");
        if (user == null || !Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                || plannerRole == null || !Objects.equals(plannerRole.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                || !permissionApi.getEnabledRoleIdsByUserId(userId).contains(plannerRole.getId())) {
            throw exception(DELIVERY_CLASS_USER_INVALID);
        }
        return user;
    }

    private AdminUserRespDTO requireHomeroom(Long userId) {
        return validateHomeroom(userId);
    }

    private void requireSameDepartment(Long managerId, AdminUserRespDTO homeroom) {
        AdminUserRespDTO manager = adminUserApi.getUser(managerId);
        if (manager == null || manager.getDeptId() == null || !Objects.equals(manager.getDeptId(), homeroom.getDeptId())) {
            throw exception(DELIVERY_CLASS_PERMISSION_DENIED);
        }
    }

    private boolean isUnended(ExamScheduleDO schedule) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        return "EXACT".equals(schedule.getScheduleType()) ? !schedule.getExactDate().isBefore(today)
                : !schedule.getRoughEndDate().isBefore(today);
    }

    private boolean scheduleCovers(ExamScheduleDO schedule, ExamProductScopeRespVO requested) {
        if (requested == null) return true;
        if (schedule.getProductId() == null) return Objects.equals(schedule.getCategoryId(), requested.categoryId());
        if (!Objects.equals(schedule.getProductId(), requested.productId())) return false;
        Set<Long> frozen = parseSkus(schedule.getFrozenSkusJson()).stream().map(ExamProductScopeRespVO.Sku::id).collect(java.util.stream.Collectors.toSet());
        return frozen.containsAll(requested.skus().stream().map(ExamProductScopeRespVO.Sku::id).toList());
    }

    private List<ExamProductScopeRespVO.Sku> parseSkus(String json) {
        return json == null ? List.of() : JsonUtils.parseArray(json, ExamProductScopeRespVO.Sku.class);
    }

    private DeliveryClassDO require(Long id) {
        DeliveryClassDO row = id == null ? null : mapper.selectById(id);
        if (row == null) throw exception(DELIVERY_CLASS_NOT_EXISTS);
        return row;
    }

    private ScopeSnapshot validate(DeliveryClassSaveReqVO req) {
        ExamProductScopeRespVO resolved = productSkuService.resolveExamScope(req.getProductId(), req.getSelectedAttrs());
        if (!Objects.equals(req.getCategoryId(), resolved.categoryId())) {
            throw exception(DELIVERY_CLASS_CATEGORY_INVALID);
        }
        Set<Long> selectedIds = req.getSelectedSkuIds() == null || req.getSelectedSkuIds().isEmpty()
                ? resolved.skus().stream().map(ExamProductScopeRespVO.Sku::id).collect(java.util.stream.Collectors.toSet())
                : req.getSelectedSkuIds();
        Set<Long> validIds = resolved.skus().stream().map(ExamProductScopeRespVO.Sku::id).collect(java.util.stream.Collectors.toSet());
        if (!validIds.containsAll(selectedIds) || selectedIds.isEmpty()) throw exception(DELIVERY_CLASS_SCHEDULE_INVALID);
        ExamProductScopeRespVO scope = new ExamProductScopeRespVO(resolved.productId(), resolved.productRef(), resolved.productName(),
                resolved.categoryId(), resolved.categoryPath(), resolved.attrs(), resolved.selectedSpecs(),
                resolved.skus().stream().filter(sku -> selectedIds.contains(sku.id())).toList());
        ZsjosProductCategoryDO category = categoryMapper.selectById(scope.categoryId());
        if (category == null || !Objects.equals(category.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            throw exception(DELIVERY_CLASS_CATEGORY_INVALID);
        }
        ExamScheduleDO schedule = scheduleMapper.selectById(req.getExamScheduleId());
        if (schedule == null || !"PUBLISHED".equals(schedule.getRecordStatus()) || !isUnended(schedule)
                || !Objects.equals(schedule.getCategoryId(), scope.categoryId()) || !scheduleCovers(schedule, scope)) throw exception(DELIVERY_CLASS_SCHEDULE_INVALID);
        List<String> path = new ArrayList<>();
        ZsjosProductCategoryDO cursor = category;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor.getId())) {
            path.add(cursor.getName());
            if (cursor.getParentId() == null || cursor.getParentId() == 0) break;
            cursor = categoryMapper.selectById(cursor.getParentId());
        }
        Collections.reverse(path);
        String date = "EXACT".equals(schedule.getScheduleType()) ? String.valueOf(schedule.getExactDate())
                : schedule.getRoughStartDate() + "~" + schedule.getRoughEndDate();
        return new ScopeSnapshot(scope, category.getName(), JsonUtils.toJsonString(path), date);
    }

    private DeliveryClassRespVO toVO(DeliveryClassDO row) {
        ExamScheduleDO schedule = row.getExamScheduleId() == null ? null : scheduleMapper.selectById(row.getExamScheduleId());
        return toVO(row, schedule, mapper.countStudents(row.getTenantId(), row.getId()));
    }

    private DeliveryClassRespVO toVO(DeliveryClassDO row, ExamScheduleDO schedule, int studentCount) {
        DeliveryClassRespVO vo = BeanUtils.toBean(row, DeliveryClassRespVO.class);
        if (schedule != null) {
            vo.setScheduleType(schedule.getScheduleType());
            vo.setExactDate(schedule.getExactDate());
        }
        vo.setSelectedAttrs(row.getSelectedAttrsJson() == null ? Map.of() : JsonUtils.parseObject(row.getSelectedAttrsJson(), Map.class));
        vo.setSelectedSpecs(row.getSelectedSpecsJson() == null ? List.of() : JsonUtils.parseArray(row.getSelectedSpecsJson(), cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO.class));
        vo.setSelectedSkus(parseSkus(row.getSelectedSkusJson()));
        vo.setStudentCount(studentCount);
        return vo;
    }

    private DeliveryClassOptionRespVO toOption(DeliveryClassDO row) {
        DeliveryClassOptionRespVO vo = new DeliveryClassOptionRespVO();
        vo.setId(row.getId()); vo.setClassNo(row.getClassNo()); vo.setClassName(row.getClassName());
        vo.setSystemClass(row.getSystemClass()); vo.setCategoryId(row.getCategoryId());
        vo.setCategoryName(row.getCategoryNameSnapshot()); vo.setExamScheduleId(row.getExamScheduleId());
        vo.setExamScheduleName(row.getExamScheduleSnapshot()); vo.setHomeroomUserId(row.getHomeroomUserId());
        vo.setHomeroomUserName(row.getHomeroomUserNameSnapshot());
        return vo;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record ScopeSnapshot(ExamProductScopeRespVO scope, String categoryName, String categoryPath, String scheduleSnapshot) {}
}
