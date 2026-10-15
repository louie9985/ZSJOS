package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface FileStorageErrorCodes {
    ErrorCode MASTER_NOT_CONFIGURED = new ErrorCode(1_001_006_002, "尚未配置默认文件存储，请联系管理员设置");
    ErrorCode MASTER_CLIENT_UNAVAILABLE = new ErrorCode(1_001_006_003, "默认文件存储不可用，请联系管理员检查配置");
}
