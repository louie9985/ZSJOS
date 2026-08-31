package cn.iocoder.yudao.module.eam.enums.procurement;

public final class EamProcurementConstants {

    public static final String DEMAND_PROCESS_KEY = "eam_asset_demand";
    public static final String PURCHASE_PROCESS_KEY = "eam_office_purchase";
    public static final String EXPENSE_PROCESS_KEY = "eam_purchase_expense";
    public static final String EMPLOYEE_REVIEW_PROCESS_KEY = "eam_employee_asset_review";
    public static final String EXPIRY_FIELD_KEY = "package_expiry";

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_APPROVING = 1;
    public static final int STATUS_APPROVED = 2;
    public static final int STATUS_REJECTED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_FULFILLING = 5;
    public static final int STATUS_COMPLETED = 6;

    public static final int EXPENSE_NOT_SUBMITTED = 0;

    public static final int TASK_ACTION_FOLLOW = 1;
    public static final int TASK_ACTION_RETURN = 2;
    public static final int TASK_ACTION_TRANSFER = 3;
    public static final int TASK_ACTION_NO_CHANGE = 4;

    public static final int RECEIPT_INBOUND = 1;
    public static final int RECEIPT_RETURN = 2;

    public static final int RESERVATION_ACTIVE = 1;
    public static final int RESERVATION_FULFILLED = 2;
    public static final int RESERVATION_RELEASED = 3;

    public static final int HOLDING_PENDING_SIGN = 0;
    public static final int HOLDING_ACTIVE = 1;
    public static final int HOLDING_RETURN_PENDING = 2;
    public static final int HOLDING_RETURNED = 3;
    public static final int HOLDING_LOST = 4;

    public static final int TASK_PROVISIONING = 1;
    public static final int TASK_CHANGE_REVIEW = 2;
    public static final int TASK_OFFBOARDING = 3;
    public static final int TASK_CANCELLED = 4;

    private EamProcurementConstants() {
    }

}
