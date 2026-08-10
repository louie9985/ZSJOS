package cn.iocoder.yudao.module.zsjos.enums;

public interface LeadNotifySceneConstants {

    String CREATED = "zsjos.lead.created";
    String ACTIVATED = "zsjos.lead.activated";
    String ASSIGNED = "zsjos.lead.assigned";
    String REASSIGNED = "zsjos.lead.reassigned";
    String ACCEPTED = "zsjos.lead.accepted";
    String REJECTED = "zsjos.lead.rejected";
    String EXPIRED = "zsjos.lead.expired";
    String PUBLIC_POOL = "zsjos.lead.public_pool";
    String CLAIMED = "zsjos.lead.claimed";
    String TRANSFERRED = "zsjos.lead.transferred";
    String FOLLOW_UP_RECORDED = "zsjos.lead.follow_up_recorded";
    String CATEGORY_CHANGED = "zsjos.lead.category_changed";
    String QUALIFICATION_SUSPENDED = "zsjos.lead.qualification_suspended";
    String QUALIFICATION_RESTORED = "zsjos.lead.qualification_restored";
    String QUALIFICATION_TRANSFERRED = "zsjos.lead.qualification_transferred";
    String QUALIFICATION_RECYCLED = "zsjos.lead.qualification_recycled";
    String QUALIFICATION_RELEASED = "zsjos.lead.qualification_released";
    String APPEAL_SUBMITTED = "zsjos.lead.appeal_submitted";
    String APPEAL_OVERTURNED = "zsjos.lead.appeal_overturned";
    String APPEAL_UPHELD = "zsjos.lead.appeal_upheld";

    String ROLE_SUBMITTER = "submitter";
    String ROLE_PENDING_SALES = "pending_sales";
    String ROLE_OWNER = "owner";
    String ROLE_OPERATOR = "operator";
    String ROLE_PREVIOUS_OWNER = "previous_owner";
    String ROLE_NEW_OWNER = "new_owner";
    String ROLE_QUALIFICATION_MANAGERS = "qualification_managers";
    String ROLE_APPEAL_REVIEWERS = "appeal_reviewers";
}
