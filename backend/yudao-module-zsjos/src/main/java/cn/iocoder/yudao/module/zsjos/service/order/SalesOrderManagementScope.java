package cn.iocoder.yudao.module.zsjos.service.order;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolved System data scope for the unified sales-order management page. */
public final class SalesOrderManagementScope {
    private final boolean all;
    private final boolean self;
    private final Set<Long> deptIds;
    private final Set<Long> submitterUserIds;

    public SalesOrderManagementScope(boolean all, boolean self, Set<Long> deptIds, Set<Long> submitterUserIds) {
        this.all = all;
        this.self = self;
        this.deptIds = immutable(deptIds);
        this.submitterUserIds = immutable(submitterUserIds);
    }

    public static SalesOrderManagementScope empty() {
        return new SalesOrderManagementScope(false, false, Set.of(), Set.of());
    }

    private static Set<Long> immutable(Set<Long> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }

    public boolean isAll() { return all; }
    public boolean isSelf() { return self; }
    public Set<Long> getDeptIds() { return deptIds; }
    public Set<Long> getSubmitterUserIds() { return submitterUserIds; }
    public boolean isEmpty() { return !all && submitterUserIds.isEmpty(); }
}
