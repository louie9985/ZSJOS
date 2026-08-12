package cn.iocoder.yudao.module.system.api.notify;

import java.util.Set;

/** Supported business-notification delivery channels. */
public final class NotifyChannelType {

    public static final String IN_APP = "in_app";
    public static final String WEBSOCKET = "websocket";
    public static final String WECOM = "wecom";
    public static final String SMS = "sms";
    public static final Set<String> ALL = Set.of(IN_APP, WEBSOCKET, WECOM, SMS);

    private NotifyChannelType() {
    }
}
