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
    String ROLE_APPLICANT = "applicant";
    String ROLE_FINANCE = "finance";
}
