package cn.iocoder.yudao.module.zsjos.framework.permission;

public interface ZsjosObjectPermissionProvider {
    String getBizType();
    boolean hasPermission(Long bizId, String action, Long userId);
    default void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) {
            throw new IllegalArgumentException("ZSJOS object permission denied");
        }
    }
}
