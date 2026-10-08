package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ExamAttachmentErrorCodes {
    ErrorCode INVALID_FILE = new ErrorCode(1_900_018_030, "请上传100MB以内的图片、PDF、Word、Excel或PPT文件");
    ErrorCode INVALID_REFERENCE = new ErrorCode(1_900_018_031, "考期附件不存在或无权引用，请移除后重新上传");
    ErrorCode TOO_MANY_FILES = new ErrorCode(1_900_018_032, "考期最多支持10个附件，且不能重复");
}
