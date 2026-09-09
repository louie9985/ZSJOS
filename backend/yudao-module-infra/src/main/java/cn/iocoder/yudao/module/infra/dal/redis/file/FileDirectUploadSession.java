package cn.iocoder.yudao.module.infra.dal.redis.file;

import lombok.Data;

@Data
public class FileDirectUploadSession {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private Long tenantId;
    private Integer userType;
    private Long userId;
    private String scene;
    private Long configId;
    private String stagingPath;
    private String finalPath;
    private String name;
    private String contentType;
    private Long size;
    private Long expiresAtMillis;
    private String status;
    private Long fileId;
}
