package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.MAX_ATTACHMENT_SIZE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_ATTACHMENT_INVALID;

@Service
public class LeadAttachmentServiceImpl implements LeadAttachmentService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Resource
    private FileApi fileApi;

    @Override
    public LeadAttachmentUploadRespVO upload(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw exception(LEAD_ATTACHMENT_INVALID);
        }
        byte[] content = file.getBytes();
        String type = FileTypeUtils.getMineType(content, file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(type)) {
            throw exception(LEAD_ATTACHMENT_INVALID);
        }
        FileInfoRespDTO fileInfo = fileApi.createFileInfo(content, file.getOriginalFilename(), "zsjos/lead", type);
        return new LeadAttachmentUploadRespVO(fileInfo.getId(), fileInfo.getUrl(), fileInfo.getName(),
                fileInfo.getType(), fileInfo.getSize());
    }

    @Override
    public Map<Long, FileInfoRespDTO> validateReferences(List<LeadAttachmentReqVO> attachments,
                                                         Long submitterUserId) {
        Map<Long, FileInfoRespDTO> result = new LinkedHashMap<>();
        for (LeadAttachmentReqVO attachment : attachments) {
            if (result.containsKey(attachment.getInfraFileId())) {
                throw exception(LEAD_ATTACHMENT_INVALID);
            }
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(attachment.getInfraFileId());
            } catch (ServiceException ex) {
                throw exception(LEAD_ATTACHMENT_INVALID);
            }
            if (file == null || !ALLOWED_TYPES.contains(file.getType()) || file.getSize() == null
                    || file.getSize() > MAX_ATTACHMENT_SIZE || StrUtil.isBlank(file.getName())
                    || file.getName().length() > 255 || StrUtil.isBlank(file.getPath())
                    || !file.getPath().startsWith("zsjos/lead/")
                    || !String.valueOf(submitterUserId).equals(file.getCreator())) {
                throw exception(LEAD_ATTACHMENT_INVALID);
            }
            result.put(file.getId(), file);
        }
        return result;
    }
}
