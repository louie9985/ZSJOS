package cn.iocoder.yudao.module.infra.api.file.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class FileDirectUploadInitRespDTO {

    private String uploadToken;
    private String uploadUrl;
    private Map<String, String> uploadHeaders;
    private LocalDateTime expiresAt;
}
