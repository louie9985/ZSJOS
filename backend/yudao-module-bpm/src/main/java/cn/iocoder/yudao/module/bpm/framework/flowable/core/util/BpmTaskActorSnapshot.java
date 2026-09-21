package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import java.util.Map;

/** These variables describe an action, never the task assignee or current account profile. */
public final class BpmTaskActorSnapshot {
    public static final String ACTOR = "bpm_action_actor_snapshot";
    public static final String EVENTS = "bpm_action_actor_events";
    private BpmTaskActorSnapshot() {}
    public static Map<?, ?> read(Map<String, Object> variables) {
        Object actor = variables == null ? null : variables.get(ACTOR);
        return actor instanceof Map<?, ?> value ? value : Map.of();
    }
    public static String name(Map<String, Object> variables) {
        Object name = read(variables).get("name");
        return name == null ? null : name.toString();
    }
    public static Long userId(Map<String, Object> variables) {
        Object id = read(variables).get("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
