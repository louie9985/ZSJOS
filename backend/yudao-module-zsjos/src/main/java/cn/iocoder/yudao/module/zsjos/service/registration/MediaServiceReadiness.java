package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;

/** Teaching acceptance must not delay independently entered media services. */
public final class MediaServiceReadiness {
    private MediaServiceReadiness() {}

    public static boolean ready(ServiceRelationDO relation) {
        return relation != null && (relation.getMediaReady() == null
                ? "accepted".equals(relation.getAcceptanceStatus()) : Boolean.TRUE.equals(relation.getMediaReady()));
    }

    public static final String SQL = "(media_ready=1 OR (media_ready IS NULL AND acceptance_status='accepted'))";
    /** Career-planner visibility remains teaching-acceptance based. The caller binds the actor as {0}. */
    public static final String COLLABORATOR_SQL = "((" + SQL
            + " AND (content_director_user_id={0} OR operator_user_id={0}))"
            + " OR (acceptance_status='accepted' AND career_planner_user_id={0}))";
}
