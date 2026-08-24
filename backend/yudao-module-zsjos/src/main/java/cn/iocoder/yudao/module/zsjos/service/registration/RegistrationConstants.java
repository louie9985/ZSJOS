package cn.iocoder.yudao.module.zsjos.service.registration;

public interface RegistrationConstants {
    String STATUS_PENDING = "pending";
    String STATUS_PROCESSING = "processing";
    String STATUS_COMPLETED = "completed";
    String STATUS_CANCELLED = "cancelled";
    String ITEM_TYPE_CHECKBOX = "checkbox";
    String ITEM_TYPE_STUDY_PLANNER = "study_planner";
    String ITEM_TYPE_ATTACHMENT = "attachment";
    String ITEM_KEY_STUDY_PLANNER = "study_planner";
    String ROUTE_STUDENT_DELIVERY = "student_delivery";
    String ROUTE_NEW_MEDIA = "new_media";
    String ASSIGNEE_STUDY_PLANNER = "study_planner";
    String ASSIGNEE_CONTENT_DIRECTOR = "content_director";
    String STUDY_PLANNER_ROLE_CODE = "study_planner";
    String CONTENT_DIRECTOR_POST_CODE = "content_director";
    String RELATION_REGISTRATION_MANAGER_PLANNER = "registration_manager_study_planner";
    String RELATION_REGISTRATION_SPECIALIST_PLANNER = "registration_specialist_study_planner";
    String PERMISSION_QUERY_POOL = "zsjos:registration:query-pool";
    String NOTIFY_SCENE_TASK_CREATED = "zsjos.registration.task_created";
    String NOTIFY_SCENE_PLANNER_ASSIGNED = "zsjos.registration.planner_assigned";
    String NOTIFY_SCENE_DIRECTOR_ASSIGNED = "zsjos.registration.director_assigned";
    String NOTIFY_ROLE_POOL_HANDLERS = "pool_handlers";
    String NOTIFY_ROLE_STUDY_PLANNER = "study_planner";
    String NOTIFY_ROLE_CONTENT_DIRECTOR = "content_director";
    String COMPLETION_BLOCK_FINANCE_PENDING = "finance_pending";
    String COMPLETION_BLOCK_FINANCE_REVISION_REQUIRED = "finance_revision_required";
    String COMPLETION_BLOCK_ORDER_NOT_EFFECTIVE = "order_not_effective";
    String COMPLETION_BLOCK_CHECKLIST_INCOMPLETE = "checklist_incomplete";
    String COMPLETION_BLOCK_PLANNER_REQUIRED = "planner_required";
    String COMPLETION_BLOCK_PLANNER_INVALID = "planner_invalid";
    String COMPLETION_BLOCK_ROUTE_REQUIRED = "route_required";
    String COMPLETION_BLOCK_ROUTE_ASSIGNEE_INVALID = "route_assignee_invalid";
    String COMPLETION_BLOCK_ATTACHMENT_REQUIRED = "attachment_required";
    long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;
    int MAX_ATTACHMENTS_PER_ITEM = 9;
}
