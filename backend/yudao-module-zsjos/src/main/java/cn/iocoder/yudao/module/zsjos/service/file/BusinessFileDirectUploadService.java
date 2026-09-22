package cn.iocoder.yudao.module.zsjos.service.file;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadCompleteReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitRespDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_FILE_INVALID;

@Service
public class BusinessFileDirectUploadService {

    public static final String CONTENT_SCENE = "ZSJOS_CONTENT";
    public static final String MATERIAL_SCENE = "ZSJOS_MATERIAL";
    private static final long MAX_FILE_BYTES = 1024L * 1024 * 1024;

    @Resource private FileApi fileApi;

    public FileDirectUploadInitRespDTO initContent(ZsjosDirectUploadInitReqVO request, Long userId) {
        validateCommon(request, true);
        return fileApi.initDirectUpload(buildInitRequest(request, userId, CONTENT_SCENE, contentDirectory(userId)));
    }

    public FileInfoRespDTO completeContent(String uploadToken, Long userId) {
        FileInfoRespDTO file = fileApi.completeDirectUpload(buildCompleteRequest(uploadToken, userId, CONTENT_SCENE));
        if (!validCompletedFile(file, userId, contentDirectory(userId))) {
            throw exception(CONTENT_FILE_UNAVAILABLE, "上传文件");
        }
        return file;
    }

    public FileDirectUploadInitRespDTO initMaterial(ZsjosDirectUploadInitReqVO request, Long userId) {
        validateCommon(request, false);
        return fileApi.initDirectUpload(buildInitRequest(request, userId, MATERIAL_SCENE, materialDirectory(userId)));
    }

    public FileInfoRespDTO completeMaterial(String uploadToken, Long userId) {
        FileInfoRespDTO file = fileApi.completeDirectUpload(buildCompleteRequest(uploadToken, userId, MATERIAL_SCENE));
        if (!validCompletedFile(file, userId, materialDirectory(userId))) {
            throw exception(MATERIAL_FILE_INVALID, "上传文件登记信息不一致");
        }
        return file;
    }

    private void validateCommon(ZsjosDirectUploadInitReqVO request, boolean contentScene) {
        String contentType = request.getContentType() == null ? "" : request.getContentType().trim().toLowerCase(Locale.ROOT);
        boolean invalidType = contentType.isEmpty() || contentType.contains("\r") || contentType.contains("\n")
                || contentScene && !ContentAttachmentTypes.accepts(contentType);
        if (request.getSize() == null || request.getSize() <= 0 || request.getSize() > MAX_FILE_BYTES
                || request.getName() == null || request.getName().isBlank() || request.getName().length() > 255
                || invalidType) {
            if (contentScene) {
                if (request.getSize() == null || request.getSize() <= 0 || request.getSize() > MAX_FILE_BYTES)
                    throw exception(CONTENT_FILE_SIZE_INVALID, "上传文件");
                if (request.getName() == null || request.getName().isBlank() || request.getName().length() > 255)
                    throw exception(CONTENT_UPLOAD_NAME_INVALID);
                throw exception(CONTENT_FILE_TYPE_INVALID, "上传文件");
            }
            throw exception(MATERIAL_FILE_INVALID, "文件为空、类型无效或超过 1GB");
        }
    }

    private FileDirectUploadInitReqDTO buildInitRequest(ZsjosDirectUploadInitReqVO request, Long userId,
                                                        String scene, String directory) {
        FileDirectUploadInitReqDTO result = new FileDirectUploadInitReqDTO();
        result.setTenantId(TenantContextHolder.getRequiredTenantId());
        result.setUserType(UserTypeEnum.ADMIN.getValue());
        result.setUserId(userId);
        result.setScene(scene);
        result.setDirectory(directory);
        result.setName(request.getName().trim());
        result.setContentType(request.getContentType().trim().toLowerCase(Locale.ROOT));
        result.setSize(request.getSize());
        return result;
    }

    private FileDirectUploadCompleteReqDTO buildCompleteRequest(String uploadToken, Long userId, String scene) {
        FileDirectUploadCompleteReqDTO result = new FileDirectUploadCompleteReqDTO();
        result.setUploadToken(uploadToken);
        result.setTenantId(TenantContextHolder.getRequiredTenantId());
        result.setUserType(UserTypeEnum.ADMIN.getValue());
        result.setUserId(userId);
        result.setScene(scene);
        return result;
    }

    private boolean validCompletedFile(FileInfoRespDTO file, Long userId, String directory) {
        return file != null && file.getId() != null && file.getPath() != null
                && file.getPath().startsWith(directory + "/")
                && Objects.equals(String.valueOf(userId), file.getCreator())
                && file.getSize() != null && file.getSize() > 0 && file.getSize() <= MAX_FILE_BYTES;
    }

    private String contentDirectory(Long userId) {
        return "zsjos/content/" + userId;
    }

    private String materialDirectory(Long userId) {
        return "zsjos/material/" + userId;
    }
}
