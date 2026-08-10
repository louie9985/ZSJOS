package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface LeadAttachmentService {
    LeadAttachmentUploadRespVO upload(MultipartFile file) throws IOException;

    Map<Long, FileInfoRespDTO> validateReferences(List<LeadAttachmentReqVO> attachments, Long submitterUserId);
}
