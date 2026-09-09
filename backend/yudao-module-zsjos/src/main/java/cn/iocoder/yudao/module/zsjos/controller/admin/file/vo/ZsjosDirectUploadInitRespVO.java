package cn.iocoder.yudao.module.zsjos.controller.admin.file.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ZsjosDirectUploadInitRespVO {

    private String uploadToken;
    private String uploadUrl;
    private Map<String, String> uploadHeaders;
    private LocalDateTime expiresAt;
}
