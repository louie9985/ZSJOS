package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ExamAttachmentErrorCodes.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXAM_SCHEDULE_PERMISSION_DENIED;

@Service
public class ExamScheduleAttachmentService {
    public static final long MAX_SIZE = 100L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf", "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    @Resource private FileApi fileApi;
    @Resource private PermissionApi permissionApi;
    @Resource private ExamScheduleMapper mapper;
    @Resource private ExamScheduleObjectPermissionProvider objectPermissions;

    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(
            bizType = ExamScheduleObjectPermissionProvider.BIZ_TYPE, bizId = "#scheduleId", action = "read-attachment")
    public ExamScheduleAttachmentRespVO read(Long scheduleId, Long fileId, Long userId) {
        objectPermissions.check(scheduleId, "read-attachment", userId);
        var schedule = mapper.selectById(scheduleId);
        if (schedule == null || !ids(schedule.getAttachmentIdsJson()).contains(fileId)) throw exception(INVALID_REFERENCE);
        return response(requireFile(fileId));
    }

    public ExamScheduleAttachmentRespVO upload(MultipartFile file, Long userId) throws IOException {
        requireManage(userId);
        if (file.isEmpty() || file.getSize() > MAX_SIZE) throw exception(INVALID_FILE);
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.matches(".*[\\\\/\\p{Cntrl}].*"))
            throw exception(INVALID_FILE);
        byte[] bytes = file.getBytes();
        String type = FileTypeUtils.getMineType(bytes, name);
        if (!TYPES.contains(type)) throw exception(INVALID_FILE);
        var info = fileApi.createFileInfo(bytes, name, directory() + userId, type);
        return response(info);
    }

    public static List<Long> ids(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, Long.class);
    }

    public String validate(List<Long> requested, String existingJson, Long userId) {
        requireManage(userId);
        // An older client omitting the new field must not erase previously saved attachments.
        if (requested == null) return existingJson;
        if (requested.size() > 10 || new HashSet<>(requested).size() != requested.size()) throw exception(TOO_MANY_FILES);
        Set<Long> existing = new HashSet<>(ids(existingJson));
        for (Long id : requested) {
            if (id == null || id <= 0) throw exception(INVALID_REFERENCE);
            var info = requireFile(id);
            boolean ownUpload = info.getPath().startsWith(directory() + userId + "/")
                    && Objects.equals(info.getCreator(), String.valueOf(userId));
            // A successful reedit claim transfers the right to copy its files into the replacement draft.
            if (!ownUpload && !existing.contains(id)
                    && mapper.countClaimedAttachment(id, TenantContextHolder.getRequiredTenantId(), userId) == 0)
                throw exception(INVALID_REFERENCE);
        }
        return JsonUtils.toJsonString(requested);
    }

    /** Caller has already applied schedule visibility or the reedit object authorization. */
    public List<ExamScheduleAttachmentRespVO> describe(String json) {
        return ids(json).stream().map(id -> {
            var info = fileApi.getFileInfo(id);
            if (info == null || info.getPath() == null || !info.getPath().startsWith(directory()))
                return new ExamScheduleAttachmentRespVO().setFileId(id).setName("附件已不可用").setType("");
            return response(info);
        }).toList();
    }

    private FileInfoRespDTO requireFile(Long id) {
        var info = fileApi.getFileInfo(id);
        if (info == null || info.getPath() == null || !info.getPath().startsWith(directory())
                || !TYPES.contains(info.getType()) || info.getSize() == null || info.getSize() <= 0 || info.getSize() > MAX_SIZE)
            throw exception(INVALID_REFERENCE);
        return info;
    }

    private ExamScheduleAttachmentRespVO response(FileInfoRespDTO info) {
        return new ExamScheduleAttachmentRespVO().setFileId(info.getId()).setName(info.getName())
                .setType(info.getType()).setSize(info.getSize()).setUrl(fileApi.presignGetUrl(info.getId(), 3600));
    }

    private String directory() { return "zsjos/exam-attachment/" + TenantContextHolder.getRequiredTenantId() + "/"; }

    private void requireManage(Long userId) {
        if (userId == null || !permissionApi.hasAnyPermissions(userId, ExamScheduleService.PERMISSION_MANAGE))
            throw exception(EXAM_SCHEDULE_PERMISSION_DENIED);
    }
}
