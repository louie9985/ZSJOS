package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审批内容展示的格式化工具。
 *
 * <p>刻意放在审批内容包里而不是做成通用工具：这里的选择（金额固定两位小数、
 * 时间精确到分钟、空值交给前端显示 "-"）是<b>审批场景的展示约定</b>，
 * 不代表整个系统的约定。
 */
public final class BpmApprovalFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BpmApprovalFormat() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    /**
     * 金额统一两位小数。审批人核对金额时位数对齐比省略尾零更重要。
     */
    public static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 截断长文本，避免摘要把列表撑开。保留可读性优先，故在结尾加省略号。
     */
    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    /**
     * 银行卡号脱敏，只留首尾各四位。
     *
     * <p>与 {@code WithdrawalServiceImpl.mask} 保持逐字一致：不足 8 位的串一律整体遮蔽，
     * 不做部分暴露——短串的头尾四位会重合，遮了等于没遮。同一张卡在提现页和审批卡上
     * 必须显示成同一个样子，否则审批人会以为看到的是两张不同的卡。
     */
    public static String maskCard(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
    }
}
