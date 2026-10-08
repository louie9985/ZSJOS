package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ExamNoteErrorCodes.*;

@Service
public class ExamCalendarNoteService {
    @Resource private ExamCalendarNoteMapper notes;
    @Resource private ExamCalendarNoteImageMapper images;
    @Resource private ExamCalendarNotePermissionProvider access;
    @Resource private FileApi files;

    @ZsjosPermission(bizType = "exam-calendar-note", bizId = "#tenantId", action = "read")
    public ExamCalendarNoteRespVO get(Long tenantId, Long userId) {
        access.check(tenantId, "read", userId);
        var row = notes.selectOne(new LambdaQueryWrapper<ExamCalendarNoteDO>().eq(ExamCalendarNoteDO::getTenantId, tenantId));
        return response(row, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "exam-calendar-note", bizId = "#tenantId", action = "write")
    public ExamCalendarNoteRespVO save(Long tenantId, Long userId, ExamCalendarNoteSaveReqVO request) {
        access.check(tenantId, "write", userId);
        var clean = ExamCalendarNoteContent.clean(request.getContent());
        notes.ensureRow(tenantId, userId);
        var row = notes.lock(tenantId);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId) || Boolean.TRUE.equals(row.getDeleted())) throw exception(DENIED);
        if (!Objects.equals(row.getVersion(), request.getVersion())) throw exception(CONFLICT);
        if (!clean.fileIds().isEmpty()) {
            var referenced = images.selectList(new LambdaQueryWrapper<ExamCalendarNoteImageDO>()
                .eq(ExamCalendarNoteImageDO::getTenantId, tenantId).in(ExamCalendarNoteImageDO::getFileId, clean.fileIds()));
            if (referenced.size() != clean.fileIds().size() || referenced.stream().anyMatch(image ->
                !Objects.equals(image.getTenantId(), tenantId) || Boolean.TRUE.equals(image.getDeleted())
                || (!Boolean.TRUE.equals(image.getBound()) && !Objects.equals(image.getUploadedBy(), userId)))) throw exception(IMAGE_INVALID);
        }
        images.update(null, new LambdaUpdateWrapper<ExamCalendarNoteImageDO>().eq(ExamCalendarNoteImageDO::getTenantId, tenantId)
            .eq(ExamCalendarNoteImageDO::getBound, true).set(ExamCalendarNoteImageDO::getBound, false));
        if (!clean.fileIds().isEmpty()) images.update(null, new LambdaUpdateWrapper<ExamCalendarNoteImageDO>()
            .eq(ExamCalendarNoteImageDO::getTenantId, tenantId).in(ExamCalendarNoteImageDO::getFileId, clean.fileIds()).set(ExamCalendarNoteImageDO::getBound, true));
        row.setContent(clean.html()).setVersion(row.getVersion() + 1);
        notes.updateById(row);
        return response(row, tenantId);
    }

    @ZsjosPermission(bizType = "exam-calendar-note", bizId = "#tenantId", action = "write")
    public ExamCalendarNoteImageRespVO upload(Long tenantId, Long userId, MultipartFile file) throws IOException {
        access.check(tenantId, "write", userId);
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw exception(IMAGE_TYPE);
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(10 * 1024 * 1024 + 1); }
        if (bytes.length > 10 * 1024 * 1024) throw exception(IMAGE_TYPE);
        String type = imageType(bytes);
        String extension = switch (type) { case "image/png" -> "png"; case "image/jpeg" -> "jpg"; case "image/gif" -> "gif"; default -> "webp"; };
        var info = files.createFileInfo(bytes, UUID.randomUUID() + "." + extension,
            "zsjos/exam-note/" + tenantId + "/" + userId, type);
        var binding = new ExamCalendarNoteImageDO().setFileId(info.getId()).setUploadedBy(userId).setBound(false);
        binding.setTenantId(tenantId);
        images.insert(binding);
        return imageResponse(info.getId());
    }

    static String imageType(byte[] b) {
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (b.length >= 3 && b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255) return "image/jpeg";
        if (b.length >= 6 && Set.of("GIF87a", "GIF89a").contains(new String(b, 0, 6, StandardCharsets.US_ASCII))) return "image/gif";
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw exception(IMAGE_TYPE);
    }

    private ExamCalendarNoteRespVO response(ExamCalendarNoteDO row, Long tenantId) {
        if (row == null) return new ExamCalendarNoteRespVO().setContent("").setVersion(0L).setImages(List.of());
        if (!Objects.equals(row.getTenantId(), tenantId) || Boolean.TRUE.equals(row.getDeleted())) throw exception(DENIED);
        var ids = ExamCalendarNoteContent.clean(row.getContent()).fileIds();
        return new ExamCalendarNoteRespVO().setContent(row.getContent()).setVersion(row.getVersion())
            .setImages(ids.stream().map(this::imageResponse).toList());
    }
    private ExamCalendarNoteImageRespVO imageResponse(Long id) {
        return new ExamCalendarNoteImageRespVO().setFileId(id).setUrl(files.presignGetUrl(id, 3600));
    }
}
