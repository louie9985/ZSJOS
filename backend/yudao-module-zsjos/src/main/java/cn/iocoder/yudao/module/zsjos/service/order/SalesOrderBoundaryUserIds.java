package cn.iocoder.yudao.module.zsjos.service.order;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sales-boundary formal owners readable by a sales actor, carrying the actor itself. The actor's own submissions
 * stay visible even when the formal owner is unset or belongs to another department, so every mapper boundary
 * predicate and object check reads {@link #actorOf(Collection)} in addition to the owner ids.
 */
public final class SalesOrderBoundaryUserIds extends AbstractSet<Long> {
    private final Long actorId;
    private final Set<Long> ids;

    public SalesOrderBoundaryUserIds(Long actorId, Collection<Long> ids) {
        this.actorId = actorId;
        this.ids = Collections.unmodifiableSet(new LinkedHashSet<>(ids == null ? Set.of() : ids));
    }

    /** Submitter whose own orders bypass the owner boundary, or null for a plain id collection. */
    public static Long actorOf(Collection<Long> ids) {
        return ids instanceof SalesOrderBoundaryUserIds boundary ? boundary.actorId : null;
    }

    @Override public Iterator<Long> iterator() { return ids.iterator(); }
    @Override public int size() { return ids.size(); }
    @Override public boolean contains(Object value) { return ids.contains(value); }
}
