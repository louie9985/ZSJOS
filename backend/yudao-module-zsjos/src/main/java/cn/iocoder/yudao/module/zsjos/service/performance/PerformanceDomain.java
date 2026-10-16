package cn.iocoder.yudao.module.zsjos.service.performance;

/** Server-selected domain. Never selected by client request parameters. */
public final class PerformanceDomain {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private PerformanceDomain() {}
    public static String current() { return CURRENT.get() == null ? "sales" : CURRENT.get(); }
    public static boolean delivery() { return "delivery".equals(current()); }
    public static String permission(String salesPermission) {
        return delivery() ? salesPermission.replace("zsjos:sales-performance", "zsjos:delivery-performance") : salesPermission;
    }
    static String enter(String domain) { String previous = CURRENT.get(); CURRENT.set(domain); return previous; }
    static void restore(String previous) { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
