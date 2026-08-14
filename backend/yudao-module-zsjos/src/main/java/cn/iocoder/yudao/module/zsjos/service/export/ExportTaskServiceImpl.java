package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.export.vo.ExportTaskRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.export.ExportTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.export.ExportTaskStatus.*;

@Service
public class ExportTaskServiceImpl implements ExportTaskService {
    static final int MAX_ROWS = 100_000;
    static final int MAX_ATTEMPTS = 3;
    static final int LEASE_SECONDS = 1_800;
    static final int DOWNLOAD_URL_SECONDS = 300;
    static final int TASK_RETENTION_DAYS = 90;
    @Resource private ExportTaskMapper mapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private SecurityFrameworkService securityService;
    @Resource private FileApi fileApi;
    @Resource private List<ExportTypeProvider> providers;
    @Resource private BusinessAuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, String exportType, String filterJson) {
        ExportTypeProvider provider = requireProvider(exportType);
        if (!securityService.hasPermission(provider.getCreatePermission())) throw exception(EXPORT_PERMISSION_DENIED);
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(EXPORT_PERMISSION_DENIED);
        }
        String normalizedFilter = normalizeFilter(filterJson);
        try {
            provider.validateFilter(normalizedFilter);
        } catch (RuntimeException error) {
            throw exception(EXPORT_FILTER_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        ExportTaskDO task = new ExportTaskDO().setTaskNo(newTaskNo()).setExportType(exportType)
                .setStatus(QUEUED).setCreatorUserId(userId).setCreatorNameSnapshot(user.getNickname())
                .setCreatorRoleSnapshot(provider.getCreatePermission()).setFilterJson(normalizedFilter)
                .setPermissionSnapshotJson(JsonUtils.toJsonString(Map.of("permission", provider.getCreatePermission())))
                .setAttemptCount(0).setNextAttemptAt(now).setLastActiveAt(now).setVersion(0);
        mapper.insert(task);
        auditService.record("export", "export.create", "export_task", task.getId().toString(),
                provider.getCreatePermission(), Map.of("exportType", exportType, "taskNo", task.getTaskNo()));
        return task.getId();
    }

    @Override
    public PageResult<ExportTaskRespVO> getMyPage(Long userId, PageParam page, String exportType) {
        return BeanUtils.toBean(mapper.selectCreatorPage(page, userId, exportType), ExportTaskRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long taskId) {
        ExportTaskDO task = requireOwned(taskId, userId);
        if (!List.of(QUEUED, PRECHECKING, GENERATING).contains(task.getStatus())) throw exception(EXPORT_STATE_INVALID);
        ExportTaskDO values = new ExportTaskDO().setStatus(CANCELLED).setCancelledAt(LocalDateTime.now())
                .setLeaseExpiresAt(null).setLastActiveAt(LocalDateTime.now());
        if (mapper.transition(taskId, task.getVersion(), List.of(QUEUED, PRECHECKING, GENERATING), values) != 1) {
            throw exception(EXPORT_STATE_INVALID);
        }
        auditService.record("export", "export.cancel", "export_task", taskId.toString(),
                task.getCreatorRoleSnapshot(), Map.of("exportType", task.getExportType(), "taskNo", task.getTaskNo()));
    }

    @Override
    public String getDownloadUrl(Long userId, Long taskId) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser != null && loginUser.getContext("zsjos.impersonation.sessionId", Long.class) != null) {
            throw exception(EXPORT_PERMISSION_DENIED);
        }
        ExportTaskDO task = requireOwned(taskId, userId);
        ExportTypeProvider provider = requireProvider(task.getExportType());
        if (!securityService.hasPermission(provider.getCreatePermission())) throw exception(EXPORT_PERMISSION_DENIED);
        if (!READY.equals(task.getStatus()) || task.getResultFileId() == null
                || task.getExpiresAt() == null || !task.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw exception(EXPORT_STATE_INVALID);
        }
        auditService.record("export", "export.download", "export_task", taskId.toString(),
                task.getCreatorRoleSnapshot(), Map.of("exportType", task.getExportType(), "taskNo", task.getTaskNo()));
        return fileApi.presignGetUrl(task.getResultFileId(), DOWNLOAD_URL_SECONDS);
    }

    @Override
    public int processAvailable() {
        // Tenant-scoped invocation is owned by the scheduler.
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        for (ExportTaskDO candidate : mapper.selectClaimCandidates(
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(), now)) {
            if (mapper.claim(candidate.getId(), candidate.getVersion(), now.plusSeconds(LEASE_SECONDS)) != 1) continue;
            processOne(mapper.selectById(candidate.getId()));
            processed++;
        }
        return processed;
    }

    @Override
    public int expireFiles() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ExportTaskDO task : mapper.selectReadyExpired(now)) {
            count += mapper.transition(task.getId(), task.getVersion(), List.of(READY),
                    new ExportTaskDO().setStatus(EXPIRED).setLastActiveAt(now));
        }
        return count;
    }

    @Override
    public int cleanInactiveTasks() {
        return mapper.deleteInactiveTerminal(LocalDateTime.now().minusDays(TASK_RETENTION_DAYS));
    }

    void processOne(ExportTaskDO task) {
        ExportTypeProvider provider = requireProvider(task.getExportType());
        try {
            int version = task.getVersion();
            if (mapper.transition(task.getId(), version, List.of(PRECHECKING),
                    new ExportTaskDO().setStatus(GENERATING)) != 1) return;
            task.setStatus(GENERATING).setVersion(version + 1);
            ExportTypeProvider.ExportResult result = generateAsCreator(task, provider);
            if (result.rowCount() > MAX_ROWS) {
                failTerminal(task, "ROW_LIMIT_EXCEEDED", "导出数据超过 100000 行");
                return;
            }
            var file = fileApi.createFileInfo(result.content(), result.fileName(),
                    "zsjos/export/" + task.getCreatorUserId(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            LocalDateTime now = LocalDateTime.now();
            ExportTaskDO ready = new ExportTaskDO().setStatus(READY).setResultFileId(file.getId()).setResultFileName(result.fileName())
                    .setResultFileSize((long) result.content().length).setReadyAt(now).setExpiresAt(now.plusDays(7))
                    .setLeaseExpiresAt(null).setLastActiveAt(now).setFailureCode(null).setFailureMessage(null)
                    .setNextAttemptAt(null);
            if (mapper.transition(task.getId(), task.getVersion(), List.of(GENERATING), ready) != 1) {
                if (mapper.attachTerminalFile(task.getId(), file.getId(), file.getName(), file.getSize()) == 0) {
                    try {
                        fileApi.deleteFileIfExists(file.getId());
                    } catch (Exception cleanupError) {
                        // The task may be reclaimed by another worker; keep the failure visible for the scheduler log.
                        throw new IllegalStateException("导出结果文件无法挂载或删除: " + task.getId(), cleanupError);
                    }
                }
                return;
            }
            auditService.record("export", "export.generate", "export_task", task.getId().toString(),
                    "系统", Map.of("exportType", task.getExportType(), "taskNo", task.getTaskNo(),
                            "rowCount", result.rowCount()));
        } catch (ExportPermissionRevokedException error) {
            failTerminal(task, "PERMISSION_REVOKED", error.getMessage());
        } catch (Exception error) {
            retryOrFail(task, error);
        }
    }

    @Override
    public void cleanupTerminalFiles() {
        for (ExportTaskDO task : mapper.selectTerminalWithFiles()) {
            try {
                fileApi.deleteFileIfExists(task.getResultFileId());
                mapper.clearResultFile(task.getId(), task.getVersion(), task.getResultFileId());
            } catch (Exception error) {
                // Keep processing other files; the failed row remains referenced for the next retry.
                mapper.touchCleanupAttempt(task.getId(), task.getVersion());
                continue;
            }
        }
    }

    private void retryOrFail(ExportTaskDO task, Exception error) {
        if (task.getAttemptCount() >= MAX_ATTEMPTS) {
            failTerminal(task, "GENERATION_FAILED", safeMessage(error));
            return;
        }
        int delay = task.getAttemptCount() <= 1 ? 30 : 60;
        mapper.transition(task.getId(), task.getVersion(), List.of(GENERATING),
                new ExportTaskDO().setStatus(QUEUED).setNextAttemptAt(LocalDateTime.now().plusSeconds(delay))
                        .setLeaseExpiresAt(null).setFailureCode("RETRYABLE").setFailureMessage(safeMessage(error))
                        .setLastActiveAt(LocalDateTime.now()));
    }

    private void failTerminal(ExportTaskDO task, String code, String message) {
        mapper.transition(task.getId(), task.getVersion(), List.of(GENERATING),
                new ExportTaskDO().setStatus(FAILED).setFailureCode(code).setFailureMessage(message)
                        .setLeaseExpiresAt(null).setLastActiveAt(LocalDateTime.now()));
    }

    private ExportTaskDO requireOwned(Long taskId, Long userId) {
        ExportTaskDO task = mapper.selectById(taskId);
        if (task == null || !userId.equals(task.getCreatorUserId())) throw exception(EXPORT_TASK_NOT_EXISTS);
        return task;
    }

    private ExportTypeProvider requireProvider(String type) {
        return providers.stream().filter(item -> item.getType().equals(type)).findFirst()
                .orElseThrow(() -> exception(EXPORT_TYPE_INVALID));
    }

    private ExportTypeProvider.ExportResult generateAsCreator(ExportTaskDO task, ExportTypeProvider provider)
            throws Exception {
        AdminUserRespDTO creator = adminUserApi.getUser(task.getCreatorUserId());
        if (creator == null || !CommonStatusEnum.ENABLE.getStatus().equals(creator.getStatus())) {
            throw new ExportPermissionRevokedException("导出任务创建人已停用或不存在");
        }
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext creatorContext = SecurityContextHolder.createEmptyContext();
        LoginUser loginUser = new LoginUser().setId(task.getCreatorUserId())
                .setUserType(UserTypeEnum.ADMIN.getValue()).setTenantId(task.getTenantId());
        Map<String, String> info = new HashMap<>();
        info.put(LoginUser.INFO_KEY_NICKNAME, creator.getNickname());
        if (creator.getDeptId() != null) {
            info.put(LoginUser.INFO_KEY_DEPT_ID, creator.getDeptId().toString());
        }
        loginUser.setInfo(info);
        creatorContext.setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        SecurityContextHolder.setContext(creatorContext);
        try {
            if (!securityService.hasPermission(provider.getCreatePermission())) {
                throw new ExportPermissionRevokedException("导出权限已被撤销");
            }
            return provider.generate(task);
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private static final class ExportPermissionRevokedException extends RuntimeException {
        private ExportPermissionRevokedException(String message) {
            super(message);
        }
    }

    private static String normalizeFilter(String filterJson) {
        if (filterJson == null || filterJson.isBlank()) return "{}";
        try {
            Object parsed = JsonUtils.parseObject(filterJson, Object.class);
            return JsonUtils.toJsonString(parsed);
        } catch (RuntimeException error) {
            throw exception(EXPORT_FILTER_INVALID);
        }
    }

    private static String newTaskNo() {
        return "EXP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 500));
    }
}
