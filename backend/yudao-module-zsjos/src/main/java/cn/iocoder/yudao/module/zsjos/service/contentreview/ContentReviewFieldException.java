package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional field context; the message remains useful to clients that only read code/msg. */
public final class ContentReviewFieldException extends RuntimeException {
    private final int code;
    private final String fieldPath;
    private final Integer workIndex;

    public ContentReviewFieldException(int code, String message, String fieldPath, Integer workIndex) {
        super(message);
        this.code = code;
        this.fieldPath = fieldPath;
        this.workIndex = workIndex;
    }

    public static ContentReviewFieldException field(ErrorCode code, String path, Object... args) {
        return new ContentReviewFieldException(code.getCode(),
                ServiceExceptionUtil.doFormat(code.getCode(), code.getMsg(), args), path, null);
    }

    public static RuntimeException atWork(RuntimeException error, int index) {
        if (error instanceof ContentReviewFieldException field) {
            if (field.workIndex != null) return field;
            return new ContentReviewFieldException(field.code, "第 " + (index + 1) + " 件作品：" + field.getMessage(),
                    "works[" + index + "]" + (field.fieldPath == null ? "" : "." + field.fieldPath), index);
        }
        // Preserve authorization failures and unknown infrastructure failures without inventing field context.
        if (error instanceof ServiceException business && java.util.Set.of(CONTENT_REVISION_SOURCE_INVALID.getCode(), CONTENT_VERSION_CONFLICT.getCode(),
                CONTENT_REVIEW_VERSION_CONFLICT.getCode(), CONTENT_VERSION_UNAVAILABLE.getCode(),
                CONTENT_VERSION_IN_REVIEW.getCode(), CONTENT_CLASS_INVALID.getCode(),
                CONTENT_REVIEW_COLLECTION_INVALID.getCode()).contains(business.getCode())) {
            return new ContentReviewFieldException(business.getCode(),
                    "第 " + (index + 1) + " 件作品：" + business.getMessage(), "works[" + index + "]", index);
        }
        return error;
    }

    public int getCode() { return code; }
    public Map<String, Object> details() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (fieldPath != null) data.put("fieldPath", fieldPath);
        if (workIndex != null) data.put("workIndex", workIndex);
        return data;
    }
}
