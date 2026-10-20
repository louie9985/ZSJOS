package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;

/** Explicit participation; missing legacy Java values retain the existing delivery contract. */
public final class StudentDeliveryParticipation {
    private StudentDeliveryParticipation() {}
    public static boolean includes(ServiceRelationDO relation) {
        return relation != null && !Boolean.FALSE.equals(relation.getTeachingDelivery());
    }
    public static final String SQL = "teaching_delivery=1";
}
