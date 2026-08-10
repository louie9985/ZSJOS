package cn.iocoder.yudao.module.system.api.notify;

import java.util.Set;

/** Controlled actions supported by persisted business notifications. */
public final class NotifyActionType {

    public static final String NONE = "none";
    public static final String MESSAGE_DETAIL = "message_detail";
    public static final String BUSINESS_DETAIL = "business_detail";

    public static final Set<String> ALL = Set.of(NONE, MESSAGE_DETAIL, BUSINESS_DETAIL);

    private NotifyActionType() {
    }
}
