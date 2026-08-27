package cn.iocoder.yudao.module.zsjos.service.feedback;

import java.util.Map;
import java.util.Set;

public interface FeedbackConstants {

    String BUSINESS_TYPE_FEEDBACK = "FEEDBACK";

    String TYPE_REQUIREMENT = "REQUIREMENT";
    String TYPE_BUG = "BUG";
    String TYPE_SUPPORT = "SUPPORT";
    String TYPE_SURVEY = "SURVEY";
    Set<String> SUBMISSION_TYPES = Set.of(TYPE_REQUIREMENT, TYPE_BUG, TYPE_SUPPORT);

    String STATUS_APPROVING = "APPROVING";
    String STATUS_APPROVAL_REJECTED = "APPROVAL_REJECTED";
    String STATUS_WAITING = "WAITING";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";

    String AUTHOR_EMPLOYEE = "EMPLOYEE";
    String AUTHOR_ADMIN = "ADMIN";
    String SURVEY_PENDING = "PENDING";
    String SURVEY_SUBMITTED = "SUBMITTED";

    String PROCESS_DEFINITION_KEY = "zsjos_feedback_requirement_approval";
    String TASK_DEPARTMENT_LEADER = "departmentLeaderReview";
    String TASK_CHAIRMAN = "chairmanReview";
    String SUPPORT_DICT_TYPE = "zsjos_feedback_support_type";
    String ROLE_CHAIRMAN = "boss";

    String PERMISSION_REQUIREMENT_MANAGE = "zsjos:feedback:requirement:manage";
    String PERMISSION_BUG_MANAGE = "zsjos:feedback:bug:manage";
    String PERMISSION_SUPPORT_MANAGE = "zsjos:feedback:support:manage";

    Map<String, String> TYPE_PREFIX = Map.of(
            TYPE_REQUIREMENT, "REQ",
            TYPE_BUG, "BUG",
            TYPE_SUPPORT, "SUP");
    Map<String, String> TYPE_LABEL = Map.of(
            TYPE_REQUIREMENT, "需求反馈",
            TYPE_BUG, "BUG 反馈",
            TYPE_SUPPORT, "技术支持");
    Map<String, String> TYPE_PERMISSION = Map.of(TYPE_REQUIREMENT, PERMISSION_REQUIREMENT_MANAGE,
            TYPE_BUG, PERMISSION_BUG_MANAGE, TYPE_SUPPORT, PERMISSION_SUPPORT_MANAGE);
}
