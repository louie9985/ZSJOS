package cn.iocoder.yudao.module.zsjos.service.registration;

public interface RegistrationConstants {
    String STATUS_PENDING = "pending";
    String STATUS_PROCESSING = "processing";
    String STATUS_COMPLETED = "completed";
    String STATUS_CANCELLED = "cancelled";
    String ITEM_TYPE_CHECKBOX = "checkbox";
    String ITEM_TYPE_STUDY_PLANNER = "study_planner";
    String ITEM_KEY_STUDY_PLANNER = "study_planner";
    String STUDY_PLANNER_ROLE_CODE = "study_planner";
    String PERMISSION_QUERY_POOL = "zsjos:registration:query-pool";
    String NOTIFY_SCENE_TASK_CREATED = "zsjos.registration.task_created";
    String NOTIFY_ROLE_POOL_HANDLERS = "pool_handlers";
    String COMPLETION_BLOCK_FINANCE_PENDING = "finance_pending";
    String COMPLETION_BLOCK_FINANCE_REVISION_REQUIRED = "finance_revision_required";
    String COMPLETION_BLOCK_ORDER_NOT_EFFECTIVE = "order_not_effective";
    String COMPLETION_BLOCK_CHECKLIST_INCOMPLETE = "checklist_incomplete";
    String COMPLETION_BLOCK_PLANNER_REQUIRED = "planner_required";
    String COMPLETION_BLOCK_PLANNER_INVALID = "planner_invalid";
}
