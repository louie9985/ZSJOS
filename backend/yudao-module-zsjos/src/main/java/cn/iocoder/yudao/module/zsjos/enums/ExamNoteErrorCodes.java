package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ExamNoteErrorCodes {
    ErrorCode DENIED = new ErrorCode(1_900_018_020, "无权访问考期说明");
    ErrorCode CONFLICT = new ErrorCode(1_900_018_021, "说明已被其他人更新，请保留当前内容并重新加载");
    ErrorCode CONTENT_INVALID = new ErrorCode(1_900_018_022, "说明最多5000字、20张图片，HTML不能超过200KB");
    ErrorCode IMAGE_INVALID = new ErrorCode(1_900_018_023, "图片无效或无权引用，请重新上传");
    ErrorCode IMAGE_TYPE = new ErrorCode(1_900_018_024, "仅支持10MB以内的PNG、JPEG、GIF、WebP图片");
}
