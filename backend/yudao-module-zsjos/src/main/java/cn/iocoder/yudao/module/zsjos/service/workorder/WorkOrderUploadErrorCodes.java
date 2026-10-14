package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface WorkOrderUploadErrorCodes {
    ErrorCode FILE_SIZE_INVALID = new ErrorCode(1_900_003_112, "工单附件不能为空且不能超过100MB");
    ErrorCode UPLOAD_BUSY = new ErrorCode(1_900_003_113, "工单附件上传繁忙，请稍后重试");
}
