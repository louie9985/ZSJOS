package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import java.util.Locale;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

public final class CalendarColors {
    private CalendarColors() {}
    public static final ErrorCode INVALID = new ErrorCode(1_900_096_001, "颜色必须是 #RRGGBB 格式");
    public static final ErrorCode DENIED = new ErrorCode(1_900_096_002, "无权访问此日历快捷色");
    public static final ErrorCode NAME = new ErrorCode(1_900_096_003, "快捷色名称须为1至20字且不能重名");
    public static final ErrorCode LIMIT = new ErrorCode(1_900_096_004, "每类日历最多保存20个快捷色");
    public static final ErrorCode MISSING = new ErrorCode(1_900_096_005, "快捷色已不存在，请刷新后重试");
    public static final ErrorCode NOT_INITIALIZED = new ErrorCode(1_900_096_006, "课程共享色板尚未初始化，请联系管理员完成升级");

    // Null is an old-client omission; empty is an explicit clear.
    public static String resolve(String value, String previous) {
        if (value == null) return previous;
        if (value.isEmpty()) return null;
        if (!value.matches("#[0-9a-fA-F]{6}")) throw exception(INVALID);
        return value.toUpperCase(Locale.ROOT);
    }
}
