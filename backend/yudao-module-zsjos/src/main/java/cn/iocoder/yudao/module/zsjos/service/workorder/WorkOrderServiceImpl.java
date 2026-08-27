package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.*;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class WorkOrderServiceImpl implements WorkOrderService {
    private static final Set<String> FIELD_TYPES = Set.of(
            "text", "textarea", "number", "date", "datetime", "user", "department", "dictionary");

    @Resource private WorkOrderSceneMapper sceneMapper;
    @Resource private WorkOrderMapper orderMapper;
    @Resource private WorkOrderHistoryMapper historyMapper;
    @Resource private PostApi postApi;
    @Resource private DeptApi deptApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;

    @Override
    public Long createScene(WorkOrderSceneCreateReqVO req, Long userId) {
        if (sceneMapper.selectByCode(req.getCode()) != null) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_CODE_DUPLICATE);
        }
        validateScene(req);
        WorkOrderSceneDO row = toSceneDO(req);
        row.setVersion(0);
        try {
            sceneMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_CODE_DUPLICATE);
        }
        return row.getId();
    }

    @Override
    public void updateScene(WorkOrderSceneUpdateReqVO req, Long userId) {
        WorkOrderSceneDO old = requireScene(req.getId());
        if (!Objects.equals(old.getCode(), req.getCode()) || !Objects.equals(old.getVersion(), req.getVersion())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        validateScene(req);
        WorkOrderSceneDO row = toSceneDO(req);
        row.setId(req.getId());
        row.setVersion(req.getVersion());
        if (sceneMapper.updateById(row) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
    }

    @Override
    public PageResult<WorkOrderSceneRespVO> scenePage(int pageNo, int pageSize) {
        PageResult<WorkOrderSceneDO> page = sceneMapper.selectPage(page(pageNo, pageSize), null);
        return new PageResult<>(page.getList().stream().map(this::toSceneVO).toList(), page.getTotal());
    }

    @Override
    public WorkOrderSceneRespVO getScene(String code) {
        return toSceneVO(requireScene(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkOrderCreateReqVO req, Long userId) {
        List<Long> attachmentIds = normalizeAttachmentIds(req.getAttachmentIds());
        String fingerprint = fingerprint("create", userId, req.getSceneCode(), req.getTargetUserId(),
                canonicalize(req.getValues()), attachmentIds);
        WorkOrderDO replay = orderMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (replay != null) return requireCreateReplay(replay, userId, fingerprint);
        WorkOrderSceneDO scene = requireScene(req.getSceneCode());
        if (!Integer.valueOf(1).equals(scene.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        AdminUserRespDTO source = requireEligibleUser(userId, scene.getSourcePostCode());
        AdminUserRespDTO target = validateTarget(req, scene);
        List<WorkOrderFieldDefinition> definitions = parseDefinitions(scene.getFieldsJson());
        Map<String, Object> values = normalizeValues(definitions, req.getValues());
        List<Long> attachments = validateAttachments(attachmentIds, userId);

        WorkOrderDO row = new WorkOrderDO();
        row.setBusinessType("GENERIC");
        row.setOrderNo("WO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        row.setSceneCode(scene.getCode());
        row.setSceneNameSnapshot(scene.getName());
        row.setAssignmentMode(scene.getAssignmentMode());
        row.setSourceUserId(userId);
        row.setTargetUserId(req.getTargetUserId());
        row.setSourceNameSnapshot(source.getNickname());
        row.setTargetNameSnapshot(target == null ? null : target.getNickname());
        row.setStatus("PUBLIC_POOL".equals(scene.getAssignmentMode()) ? "POOL" : "IN_PROGRESS");
        row.setFieldSnapshotJson(JsonUtils.toJsonString(definitions));
        row.setValueJson(JsonUtils.toJsonString(values));
        row.setAttachmentIdsJson(JsonUtils.toJsonString(attachments));
        row.setIdempotencyKey(req.getIdempotencyKey());
        row.setCommandUserId(userId);
        row.setRequestFingerprint(fingerprint);
        row.setVersion(0);
        try {
            orderMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            WorkOrderDO concurrent = orderMapper.selectByIdempotencyKey(req.getIdempotencyKey());
            if (concurrent == null) throw duplicate;
            return requireCreateReplay(concurrent, userId, fingerprint);
        }
        history(row, null, row.getStatus(), userId, null, req.getIdempotencyKey(), "create", fingerprint);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id, WorkOrderActionReqVO req, Long userId) {
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("claim", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "claim", userId, fingerprint)) return;
        WorkOrderSceneDO scene = requireScene(row.getSceneCode());
        AdminUserRespDTO target = requireEligibleUser(userId, scene.getTargetPostCode());
        if (!"POOL".equals(row.getStatus())
                || orderMapper.claim(id, userId, target.getNickname(), req.getVersion()) != 1) {
            if (isExactReplay(id, req.getIdempotencyKey(), "claim", userId, fingerprint)) return;
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_CLAIM_ALREADY_TAKEN);
        }
        row.setTargetUserId(userId);
        row.setTargetNameSnapshot(target.getNickname());
        history(row, "POOL", "IN_PROGRESS", userId, null, req.getIdempotencyKey(), "claim", fingerprint);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, WorkOrderActionReqVO req, Long userId) {
        transition(id, req, userId, List.of("IN_PROGRESS", "RETURNED"), "COMPLETED_PENDING_ACCEPTANCE", false,
                "complete");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, WorkOrderActionReqVO req, Long userId) {
        transition(id, req, userId, List.of("COMPLETED_PENDING_ACCEPTANCE"), "ACCEPTED", true, "accept");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void returnForRework(Long id, WorkOrderActionReqVO req, Long userId) {
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        transition(id, req, userId, List.of("COMPLETED_PENDING_ACCEPTANCE"), "RETURNED", true, "return");
    }

    private void transition(Long id, WorkOrderActionReqVO req, Long userId, List<String> fromStates, String to,
                            boolean sourceAction, String operation) {
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint(operation, id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), operation, userId, fingerprint)) return;
        boolean authorized = sourceAction ? Objects.equals(row.getSourceUserId(), userId)
                : Objects.equals(row.getTargetUserId(), userId);
        if (!authorized) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        if (!fromStates.contains(row.getStatus()) || !Objects.equals(req.getVersion(), row.getVersion())
                || orderMapper.transition(id, req.getVersion(), row.getStatus(), to,
                sourceAction ? trimmed(req.getReason()) : null) != 1) {
            if (isExactReplay(id, req.getIdempotencyKey(), operation, userId, fingerprint)) return;
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        history(row, row.getStatus(), to, userId, trimmed(req.getReason()), req.getIdempotencyKey(), operation,
                fingerprint);
    }

    @Override
    public PageResult<WorkOrderRespVO> myPage(String status, int pageNo, int pageSize, Long userId) {
        return mapPage(orderMapper.selectMyPage(page(pageNo, pageSize), status, userId));
    }

    @Override
    public PageResult<WorkOrderRespVO> pool(String sceneCode, int pageNo, int pageSize, Long userId) {
        WorkOrderSceneDO scene = requireScene(sceneCode);
        requireEligibleUser(userId, scene.getTargetPostCode());
        return mapPage(orderMapper.selectPool(page(pageNo, pageSize), sceneCode));
    }

    @Override
    public WorkOrderRespVO get(Long id, Long userId) {
        WorkOrderDO row = require(id);
        if (!Objects.equals(row.getSourceUserId(), userId) && !Objects.equals(row.getTargetUserId(), userId)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return toVO(row);
    }

    private WorkOrderSceneDO toSceneDO(WorkOrderSceneCreateReqVO req) {
        WorkOrderSceneDO row = BeanUtils.toBean(req, WorkOrderSceneDO.class);
        row.setFieldsJson(JsonUtils.toJsonString(req.getFields()));
        return row;
    }

    private WorkOrderSceneRespVO toSceneVO(WorkOrderSceneDO row) {
        WorkOrderSceneRespVO result = BeanUtils.toBean(row, WorkOrderSceneRespVO.class);
        result.setFields(parseDefinitions(row.getFieldsJson()));
        return result;
    }

    private void validateScene(WorkOrderSceneCreateReqVO req) {
        var sourcePost = postApi.getPostByCode(req.getSourcePostCode());
        var targetPost = postApi.getPostByCode(req.getTargetPostCode());
        if (!List.of("DIRECT", "PUBLIC_POOL").contains(req.getAssignmentMode())
                || sourcePost == null || targetPost == null
                || !CommonStatusEnum.ENABLE.getStatus().equals(sourcePost.getStatus())
                || !CommonStatusEnum.ENABLE.getStatus().equals(targetPost.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        validateDefinitions(req.getFields());
    }

    private List<WorkOrderFieldDefinition> parseDefinitions(String json) {
        try {
            List<WorkOrderFieldDefinition> definitions = JsonUtils.parseArray(json, WorkOrderFieldDefinition.class);
            validateDefinitions(definitions);
            return definitions;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private void validateDefinitions(List<WorkOrderFieldDefinition> definitions) {
        if (definitions == null || definitions.isEmpty() || definitions.size() > 100) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        Set<String> keys = new HashSet<>();
        for (WorkOrderFieldDefinition field : definitions) {
            if (field == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            boolean dictionary = "dictionary".equals(field.type());
            if (field.key() == null || !field.key().matches("[a-z][a-z0-9_]{0,63}")
                    || field.label() == null || field.label().isBlank() || field.label().length() > 128
                    || !FIELD_TYPES.contains(field.type()) || !keys.add(field.key())
                    || dictionary != (field.dictionaryType() != null && !field.dictionaryType().isBlank())) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            }
        }
    }

    private Map<String, Object> normalizeValues(List<WorkOrderFieldDefinition> definitions,
                                                 Map<String, Object> submitted) {
        try {
            Map<String, WorkOrderFieldDefinition> byKey = new LinkedHashMap<>();
            definitions.forEach(field -> byKey.put(field.key(), field));
            if (submitted == null || !byKey.keySet().containsAll(submitted.keySet())) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            }
            Set<Long> userIds = new LinkedHashSet<>();
            Set<Long> departmentIds = new LinkedHashSet<>();
            Map<String, Set<String>> dictionaryValues = new LinkedHashMap<>();
            for (WorkOrderFieldDefinition field : definitions) {
                Object value = submitted.get(field.key());
                if (missing(value)) continue;
                if ("user".equals(field.type())) userIds.add(requireLong(value));
                if ("department".equals(field.type())) departmentIds.add(requireLong(value));
                if ("dictionary".equals(field.type())) dictionaryValues
                        .computeIfAbsent(field.dictionaryType(), ignored -> new LinkedHashSet<>())
                        .add(requireString(value));
            }
            Map<Long, AdminUserRespDTO> users = userIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(userIds);
            Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments = departmentIds.isEmpty()
                    ? Map.of() : deptApi.getDeptMap(departmentIds);
            Map<String, Map<String, DictDataRespDTO>> dictionaries = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : dictionaryValues.entrySet()) {
                dictDataApi.validateDictDataList(entry.getKey(), entry.getValue());
                Map<String, DictDataRespDTO> values = new LinkedHashMap<>();
                dictDataApi.getDictDataList(entry.getKey()).forEach(data -> values.put(data.getValue(), data));
                dictionaries.put(entry.getKey(), values);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (WorkOrderFieldDefinition field : definitions) {
                Object value = submitted.get(field.key());
                if (missing(value)) {
                    if (Boolean.TRUE.equals(field.required())) {
                        throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
                    }
                    continue;
                }
                normalized.put(field.key(), normalizeValue(field, value, users, departments, dictionaries));
            }
            return normalized;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private Object normalizeValue(WorkOrderFieldDefinition field, Object value,
                                  Map<Long, AdminUserRespDTO> users,
                                  Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments,
                                  Map<String, Map<String, DictDataRespDTO>> dictionaries) {
        return switch (field.type()) {
            case "text", "textarea" -> requireString(value);
            case "number" -> new BigDecimal(String.valueOf(value)).stripTrailingZeros();
            case "date" -> LocalDate.parse(requireString(value)).toString();
            case "datetime" -> LocalDateTime.parse(requireString(value)).toString();
            case "user" -> userSnapshot(requireLong(value), users);
            case "department" -> departmentSnapshot(requireLong(value), departments);
            case "dictionary" -> dictionarySnapshot(field.dictionaryType(), requireString(value), dictionaries);
            default -> throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        };
    }

    private Map<String, Object> userSnapshot(Long id, Map<Long, AdminUserRespDTO> users) {
        AdminUserRespDTO user = users.get(id);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return snapshot("id", id, user.getNickname());
    }

    private Map<String, Object> departmentSnapshot(Long id,
            Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments) {
        var dept = departments.get(id);
        if (dept == null || !CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return snapshot("id", id, dept.getName());
    }

    private Map<String, Object> dictionarySnapshot(String type, String value,
            Map<String, Map<String, DictDataRespDTO>> dictionaries) {
        DictDataRespDTO data = Optional.ofNullable(dictionaries.get(type)).map(values -> values.get(value))
                .orElseThrow(() -> exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID));
        String label = requireSnapshotLabel(data.getLabel());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    private List<Long> validateAttachments(List<Long> ids, Long userId) {
        List<Long> normalized = normalizeAttachmentIds(ids);
        try {
            for (Long id : normalized) {
                FileInfoRespDTO file = fileApi.getFileInfo(id);
                if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/work-order/")
                        || !String.valueOf(userId).equals(file.getCreator())) {
                    throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
                }
            }
            return normalized;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
        }
    }

    private AdminUserRespDTO validateTarget(WorkOrderCreateReqVO req, WorkOrderSceneDO scene) {
        if ("DIRECT".equals(scene.getAssignmentMode())) {
            if (req.getTargetUserId() == null) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
            }
            return requireEligibleUser(req.getTargetUserId(), scene.getTargetPostCode());
        }
        if (req.getTargetUserId() != null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        return null;
    }

    private Long requireCreateReplay(WorkOrderDO row, Long userId, String fingerprint) {
        if (!Objects.equals(row.getCommandUserId(), userId)
                || !Objects.equals(row.getRequestFingerprint(), fingerprint)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_IDEMPOTENCY_CONFLICT);
        }
        return row.getId();
    }

    private boolean isExactReplay(Long orderId, String key, String operation, Long userId, String fingerprint) {
        WorkOrderHistoryDO replay = historyMapper.selectByOrderAndKey(orderId, key);
        if (replay == null) return false;
        if (!Objects.equals(replay.getOperation(), operation)
                || !Objects.equals(replay.getOperatorUserId(), userId)
                || !Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_IDEMPOTENCY_CONFLICT);
        }
        return true;
    }

    private String actionFingerprint(String operation, Long id, WorkOrderActionReqVO req, Long userId) {
        return fingerprint(operation, id, userId, req.getVersion(), trimmed(req.getReason()));
    }

    private String fingerprint(Object... values) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(values)));
    }

    private void history(WorkOrderDO row, String from, String to, Long userId, String reason, String key,
                         String operation, String fingerprint) {
        WorkOrderHistoryDO history = new WorkOrderHistoryDO();
        history.setWorkOrderId(row.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorUserId(userId);
        history.setReason(reason);
        history.setIdempotencyKey(key);
        history.setOperation(operation);
        history.setRequestFingerprint(fingerprint);
        history.setOperatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private PageResult<WorkOrderRespVO> mapPage(PageResult<WorkOrderDO> page) {
        return new PageResult<>(page.getList().stream().map(this::toVO).toList(), page.getTotal());
    }

    private WorkOrderRespVO toVO(WorkOrderDO row) {
        WorkOrderRespVO result = BeanUtils.toBean(row, WorkOrderRespVO.class);
        result.setSceneName(row.getSceneNameSnapshot());
        result.setSourceName(row.getSourceNameSnapshot());
        result.setTargetName(row.getTargetNameSnapshot());
        result.setFields(parseDefinitions(row.getFieldSnapshotJson()));
        result.setValues(JsonUtils.parseObject(row.getValueJson(), Map.class));
        result.setAttachmentIds(JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class));
        return result;
    }

    private WorkOrderDO require(Long id) {
        WorkOrderDO row = orderMapper.selectById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return row;
    }

    private WorkOrderDO requireForUpdate(Long id) {
        WorkOrderDO row = orderMapper.selectByIdForUpdate(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneDO requireScene(String code) {
        WorkOrderSceneDO row = sceneMapper.selectByCode(code);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneDO requireScene(Long id) {
        WorkOrderSceneDO row = sceneMapper.selectById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_NOT_EXISTS);
        return row;
    }

    private AdminUserRespDTO requireEligibleUser(Long userId, String postCode) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        var post = postApi.getPostByCode(postCode);
        if (user == null || post == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())
                || user.getPostIds() == null || !user.getPostIds().contains(post.getId())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return user;
    }

    private PageParam page(int pageNo, int pageSize) {
        PageParam page = new PageParam();
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        return page;
    }

    private static boolean missing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private static String requireString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return text.trim();
    }

    private static Long requireLong(Object value) {
        try {
            BigDecimal decimal = value instanceof Number number ? new BigDecimal(number.toString())
                    : new BigDecimal(requireString(value));
            if (decimal.signum() < 0) throw new ArithmeticException();
            return decimal.longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static Map<String, Object> snapshot(String idKey, Long id, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(idKey, id);
        result.put("label", requireSnapshotLabel(label));
        return result;
    }

    private static String requireSnapshotLabel(String label) {
        if (label == null || label.isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return label;
    }

    private static List<Long> normalizeAttachmentIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> normalized = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (normalized.size() != ids.size() || normalized.size() > 20) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
        }
        return normalized;
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(WorkOrderServiceImpl::canonicalize).toList();
        return value;
    }
}
