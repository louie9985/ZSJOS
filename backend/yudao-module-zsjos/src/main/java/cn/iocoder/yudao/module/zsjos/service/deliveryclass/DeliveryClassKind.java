package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;

public final class DeliveryClassKind {
    private DeliveryClassKind() {}
    public static final String NORMAL = "NORMAL";
    public static final String PENDING = "PENDING";
    public static final String UNSCHEDULED = "UNSCHEDULED";
    public static final String EXCEPTION = "EXCEPTION";
    public static boolean pending(DeliveryClassDO row) {
        return row != null && (PENDING.equals(row.getClassKind())
                || (row.getClassKind() == null && Boolean.TRUE.equals(row.getSystemClass()) && row.getHomeroomUserId() == null));
    }
    /** Owned default classes participate in ordinary teaching; pending and archives do not. */
    public static boolean assignable(DeliveryClassDO row) {
        return row != null && (UNSCHEDULED.equals(row.getClassKind())
                || (!Boolean.TRUE.equals(row.getSystemClass()) && (row.getClassKind() == null || NORMAL.equals(row.getClassKind()))));
    }
    public static boolean exception(DeliveryClassDO row) { return row != null && EXCEPTION.equals(row.getClassKind()); }
}
