package cn.iocoder.yudao.module.zsjos.enums;

import java.util.Set;

public interface SalesOrderConstants {

    String PROCESS_DEFINITION_KEY = "zsjos_sales_order_dual_approval";
    String BUSINESS_KEY_PREFIX = "sales-order:";
    String TASK_REGISTRATION = "registrationReview";
    String TASK_FINANCE = "financeReview";
    String CENTER_REGISTRATION = "registration";
    String CENTER_FINANCE = "finance";

    String BIZ_TYPE_SALES_ORDER = "sales-order";
    String TASK_TYPE_REVISION = "sales_order_revision";
    String TASK_ACTION_REVISION = "OPEN_SALES_ORDER_REVISION";
    String TASK_REVISION_KEY_PREFIX = "sales-order-revision:";

    String STATUS_PENDING_APPROVAL = "pending_approval";
    String STATUS_REVISION_REQUIRED = "revision_required";
    String STATUS_EFFECTIVE = "effective";
    String STATUS_SUPERSEDED = "superseded";
    String STATUS_TERMINATED = "terminated";
    Set<String> ACTIVE_ORDER_STATUSES = Set.of(STATUS_PENDING_APPROVAL, STATUS_REVISION_REQUIRED);

    String ROUND_PENDING = "pending";
    String ROUND_APPROVED = "approved";
    String ROUND_REJECTED = "rejected";
    String ROUND_TERMINATED = "terminated";

    String ORDER_TYPE_FIRST_PURCHASE = "first_purchase";
    String ORDER_TYPE_REPURCHASE = "repurchase";
    String ORDER_TYPE_DIRECT_SALE = ORDER_TYPE_FIRST_PURCHASE;
    String SUBMITTER_CENTER_SALES = "sales_conversion";

    String PERMISSION_CREATE = "zsjos:sales-order:create";
    String PERMISSION_QUERY = "zsjos:sales-order:query";
    String PERMISSION_REVIEW = "zsjos:sales-order:review";
    String PERMISSION_SUPERVISOR_CONFIRM = "zsjos:sales-order:supervisor-confirm";

    String SUPERVISOR_PENDING = "pending";
    String SUPERVISOR_CONFIRMED = "confirmed";
    String SUPERVISOR_REJECTED = "rejected";
    String SUPERVISOR_CANCELLED = "cancelled";

    String DICT_STUDENT_NATURE = "zsjos_order_student_nature";
    String DICT_SERVICE_PERIOD = "zsjos_order_service_period";
    String DICT_STUDENT_SOURCE = "zsjos_order_student_source";
    String DICT_FEE_MODE = "zsjos_order_fee_mode";
    String DICT_PAYMENT_METHOD = "zsjos_order_payment_method";
}
