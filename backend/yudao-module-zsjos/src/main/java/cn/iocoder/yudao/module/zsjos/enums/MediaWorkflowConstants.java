package cn.iocoder.yudao.module.zsjos.enums;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MediaWorkflowConstants {
    private MediaWorkflowConstants() {}

    public static final String BIZ_TYPE_MEDIA_ACCOUNT = "media-account";
    public static final String BIZ_TYPE_CONTENT = "content";
    public static final String BIZ_TYPE_PRODUCTION_TICKET = "production-ticket";
    public static final String BIZ_TYPE_POSITIONING_CARD = "positioning-card";
    public static final String BIZ_TYPE_POSITIONING_EXEC_CARD = "positioning-exec-card";

    public static final String OWNERSHIP_STUDENT = "student";
    public static final String OWNERSHIP_COMPANY = "company";
    public static final String RUN_STATUS_ACTIVE = "active";

    public static final List<String> ACCOUNT_STAGES = List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6");

    public static final String CONTENT_TOPIC = "topic";
    public static final String CONTENT_SCRIPT = "script";
    public static final String CONTENT_IN_PRODUCTION = "in_production";
    public static final String CONTENT_ACCEPTANCE = "acceptance";
    public static final String CONTENT_PUBLISHED = "published";
    public static final String CONTENT_REJECTED = "rejected";
    public static final String CONTENT_REVISING = "revising";
    public static final Map<String, Set<String>> CONTENT_TRANSITIONS = Map.of(
            CONTENT_TOPIC, Set.of(CONTENT_SCRIPT),
            CONTENT_SCRIPT, Set.of(CONTENT_IN_PRODUCTION),
            CONTENT_IN_PRODUCTION, Set.of(CONTENT_ACCEPTANCE),
            CONTENT_ACCEPTANCE, Set.of(CONTENT_PUBLISHED, CONTENT_REJECTED),
            CONTENT_REJECTED, Set.of(CONTENT_REVISING),
            CONTENT_REVISING, Set.of(CONTENT_IN_PRODUCTION));

    public static final String TICKET_PENDING_ACCEPT = "pending_accept";
    public static final String TICKET_ACCEPTED = "accepted";
    public static final String TICKET_IN_PRODUCTION = "in_production";
    public static final String TICKET_SUBMITTED = "submitted";
    public static final String TICKET_CHECKING = "checking";
    public static final String TICKET_COMPLETED = "completed";
    public static final String TICKET_REJECTED = "rejected";
    public static final Map<String, Set<String>> TICKET_TRANSITIONS = Map.of(
            TICKET_PENDING_ACCEPT, Set.of(TICKET_ACCEPTED),
            TICKET_ACCEPTED, Set.of(TICKET_IN_PRODUCTION),
            TICKET_IN_PRODUCTION, Set.of(TICKET_SUBMITTED),
            TICKET_SUBMITTED, Set.of(TICKET_CHECKING),
            TICKET_CHECKING, Set.of(TICKET_COMPLETED, TICKET_REJECTED),
            TICKET_REJECTED, Set.of(TICKET_ACCEPTED));

    public static final String POSITIONING_DRAFT = "draft";
    public static final String POSITIONING_CO_CREATING = "co_creating";
    public static final String POSITIONING_IP_REVIEW = "ip_review";
    public static final String POSITIONING_OPERATOR_FEASIBILITY = "operator_feasibility";
    public static final String POSITIONING_STUDENT_CONFIRM = "student_confirm";
    public static final String POSITIONING_TRIAL_14D = "trial_14d";
    public static final String POSITIONING_CONFIRMED = "confirmed";
    public static final String POSITIONING_ARCHIVED = "archived";

    public static final String PROCESS_KEY_POSITIONING_IP = "zsjos_media_positioning_ip";
    public static final String POST_CODE_IP_TEACHER = "ip_teacher";
    public static final String PROCESS_KEY_REPOSITION = "zsjos_media_reposition";
    public static final String PROCESS_KEY_REBIND = "zsjos_media_rebind";
    public static final String PROCESS_KEY_OVER_ENTITLEMENT = "zsjos_media_over_entitlement";
    public static final String PROCESS_KEY_GRADUATION = "zsjos_media_graduation";

    public static final String ACTION_ACCEPT_TICKET = "ACCEPT_TICKET";
    public static final String ACTION_CHECK_TICKET = "CHECK_TICKET";
    public static final String ACTION_ACCEPT_CONTENT = "ACCEPT_CONTENT";
    public static final String ACTION_ADVANCE_STAGE = "ADVANCE_STAGE";
    public static final String ACTION_ROLLBACK_STAGE = "ROLLBACK_STAGE";

    public static final String ACTION_BIND_STUDENT = "BIND_STUDENT";
    public static final String ACTION_UNBIND_STUDENT = "UNBIND_STUDENT";
    public static final String ACTION_EDIT_ACCOUNT = "EDIT_ACCOUNT";
    public static final String ACTION_DIAGNOSE_ACCOUNT = "DIAGNOSE_ACCOUNT";
    public static final String ACTION_RESCUE_ACCOUNT = "RESCUE_ACCOUNT";
    public static final String ACTION_REQUEST_ACCOUNT_REBIND = "REQUEST_ACCOUNT_REBIND";
    public static final String ACTION_COMPLETE_TOPIC = "COMPLETE_TOPIC";
    public static final String ACTION_SUBMIT_PRODUCTION = "SUBMIT_PRODUCTION";
    public static final String ACTION_SUBMIT_ACCEPTANCE = "SUBMIT_ACCEPTANCE";
    public static final String ACTION_APPROVE_CONTENT = "APPROVE_CONTENT";
    public static final String ACTION_REJECT_CONTENT = "REJECT_CONTENT";
    public static final String ACTION_START_CONTENT_REVISION = "START_CONTENT_REVISION";
    public static final String ACTION_RESUBMIT_PRODUCTION = "RESUBMIT_PRODUCTION";
    public static final String ACTION_START_TICKET = "START_TICKET";
    public static final String ACTION_SUBMIT_TICKET = "SUBMIT_TICKET";
    public static final String ACTION_START_TICKET_CHECK = "START_TICKET_CHECK";
    public static final String ACTION_APPROVE_TICKET = "APPROVE_TICKET";
    public static final String ACTION_REJECT_TICKET = "REJECT_TICKET";
    public static final String ACTION_REACCEPT_TICKET = "REACCEPT_TICKET";
    public static final String ACTION_SUBMIT_POSITIONING_REVIEW = "SUBMIT_POSITIONING_REVIEW";
    public static final String ACTION_APPROVE_POSITIONING_FEASIBILITY = "APPROVE_POSITIONING_FEASIBILITY";
    public static final String ACTION_REJECT_POSITIONING_FEASIBILITY = "REJECT_POSITIONING_FEASIBILITY";
    public static final String ACTION_CONFIRM_POSITIONING_TRIAL = "CONFIRM_POSITIONING_TRIAL";
    public static final String ACTION_ARCHIVE_POSITIONING = "ARCHIVE_POSITIONING";
}
