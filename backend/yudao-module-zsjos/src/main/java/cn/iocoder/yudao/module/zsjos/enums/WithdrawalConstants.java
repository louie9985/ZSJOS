package cn.iocoder.yudao.module.zsjos.enums;

public interface WithdrawalConstants {
    String PROCESS_DEFINITION_KEY = "zsjos_partner_withdrawal";
    String TASK_DEFINITION_KEY = "financeReview";
    String STATUS_PENDING = "pending_review";
    String STATUS_APPROVED = "approved";
    String STATUS_REJECTED = "rejected";
    String STATUS_PAID = "paid";
    String STATUS_CANCELLED = "cancelled";
    String VERIFY_NORMAL = "normal";
    String VERIFY_AMOUNT = "amount_abnormal";
    String VERIFY_DUPLICATE = "duplicate_application";
    String VERIFY_BALANCE = "balance_abnormal";
    String MIN_AMOUNT_KEY = "zsjos.withdrawal.minimum-amount";
    String REMINDER_OVERDUE_DAYS_KEY = "zsjos.withdrawal.reminder-overdue-days";
    String SCENE_SUBMITTED = "zsjos.withdrawal.submitted";
    String SCENE_APPROVED = "zsjos.withdrawal.approved";
    String SCENE_REJECTED = "zsjos.withdrawal.rejected";
    String SCENE_PAID = "zsjos.withdrawal.paid";
    String SCENE_FINANCE_REMINDER = "zsjos.withdrawal.finance_reminder";
    String NOTIFICATION_REJECTION_REASON = "withdrawal.rejectionReason";
    String ROLE_APPLICANT = "applicant";
    String ROLE_FINANCE = "finance";

    /**
     * 提现状态的中文标签，唯一口径。
     *
     * <p>{@code approved} 是「财务已通过、尚未打款」，对财务而言真正要办的动作是登记打款，
     * 所以叫「待打款」。此前审批中心的业务卡片单独维护了一份映射并写成「已通过」，
     * 与财务筛选目录不一致，看着像流程已经走完。状态标签走这里，不要再各写各的 switch。
     *
     * @param status 业务状态值，允许为 null
     * @return 中文标签；null 原样返回，未知状态回落到原始值
     */
    static String statusLabel(String status) {
        if (status == null) return null;
        return switch (status) {
            case STATUS_PENDING -> "待审核";
            case STATUS_APPROVED -> "待打款";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_PAID -> "已打款";
            case STATUS_CANCELLED -> "已取消";
            default -> status;
        };
    }
}
