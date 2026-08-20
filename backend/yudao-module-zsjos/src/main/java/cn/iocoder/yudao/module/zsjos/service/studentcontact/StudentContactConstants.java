package cn.iocoder.yudao.module.zsjos.service.studentcontact;

public interface StudentContactConstants {
    String BIZ_TYPE = "student_service";
    String TYPE_FIRST_CONTACT = "student_first_contact";
    String TYPE_STUDY_PLAN = "student_study_plan";
    String TYPE_CONTACT = "student_contact";
    String TYPE_ASSISTANCE = "student_first_contact_assistance";
    String ACTION_FIRST_CONTACT = "OPEN_STUDENT_FIRST_CONTACT";
    String ACTION_STUDY_PLAN = "OPEN_STUDENT_STUDY_PLAN";
    String ACTION_CONTACT = "OPEN_STUDENT_CONTACT";
    String ACTION_ASSISTANCE = "OPEN_STUDENT_CONTACT_ASSISTANCE";
    String COLLABORATOR_DIRECTOR = "content_director";
    String COLLABORATOR_CAREER = "career_planner";
    String RELATION_REGISTRATION_MANAGER_PLANNER = "registration_manager_study_planner";
    String RELATION_REGISTRATION_SPECIALIST_PLANNER = "registration_specialist_study_planner";
    String RELATION_PLANNER_DIRECTOR = "study_planner_content_director";
    String RELATION_PLANNER_CAREER = "study_planner_career_planner";
    String DICT_UNSUCCESSFUL_REASON = "zsjos_student_contact_unsuccessful_reason";
    String DICT_EXTENSION_REASON = "zsjos_student_contact_extension_reason";
    String PROCESS_EXTENSION = "zsjos_student_contact_extension";
    String TASK_EXTENSION_REVIEW = "deliverySupervisorReview";
    String PERMISSION_QUERY = "zsjos:student-contact:query";
    String PERMISSION_UPDATE = "zsjos:student-contact:update";
    String PERMISSION_CONFIG = "zsjos:student-contact-config:update";
    String PERMISSION_EXTENSION_REVIEW = "zsjos:student-contact-extension:review";
    String PERMISSION_COLLABORATOR_CORRECT = "zsjos:student-collaborator:correct";
    String NOTIFY_FIRST_CONTACT = "zsjos.student.first_contact_reminder";
    String NOTIFY_STUDY_PLAN = "zsjos.student.study_plan_reminder";
    String NOTIFY_CONTACT = "zsjos.student.contact_reminder";
    String NOTIFY_ROLE_PLANNER = "study_planner";
    String NOTIFY_ROLE_SUPERVISOR = "delivery_supervisor";
}
