package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import java.util.Map;

/** One-to-one joins preserve service-level pagination and counts. */
public final class StudentServicePageSql {
    private StudentServicePageSql() {}
    public static final String FROM = " FROM zsjos_service_relation sr"
        + " JOIN zsjos_person p ON p.id=sr.person_id AND p.tenant_id=sr.tenant_id AND p.deleted=0"
        + " LEFT JOIN zsjos_order ro ON ro.id=sr.order_id AND ro.tenant_id=sr.tenant_id AND ro.deleted=0"
        + " LEFT JOIN zsjos_order_item oi ON oi.id=sr.order_item_id AND oi.order_id=sr.order_id AND oi.tenant_id=sr.tenant_id AND oi.deleted=0"
        + " LEFT JOIN zsjos_lead rl ON rl.id=ro.lead_id AND rl.tenant_id=sr.tenant_id AND rl.deleted=0"
        + " LEFT JOIN zsjos_delivery_class dc ON dc.id=sr.class_id AND dc.tenant_id=sr.tenant_id AND dc.deleted=0"
        + " WHERE sr.deleted=0 AND sr.tenant_id=#{query.parameters.tenantId} AND (${query.whereSql})";
    public static String count(Map<String,Object> args) { return "SELECT COUNT(*)" + FROM; }
    public static String page(Map<String,Object> args) {
        var q = (AdvancedFilterQuery) args.get("query");
        String field = (String) q.getParameters().get("sortField");
        String expression = switch (field == null ? "" : field) {
            case "name" -> "p.name"; case "mobile" -> "p.mobile"; case "wechatId" -> "p.wechat_id";
            case "leadNo" -> "rl.lead_no"; case "className" -> "dc.class_name";
            case "courses" -> "JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(COALESCE(oi.product_snapshot,sr.service_snapshot)),COALESCE(oi.product_snapshot,sr.service_snapshot),'{}'),'$.name'))";
            case "serviceStatus" -> "sr.status"; case "orderNos" -> "ro.order_no";
            case "activatedAt" -> "sr.activated_at";
            default -> "sr.update_time";
        };
        String direction = "ascend".equals(q.getParameters().get("sortOrder")) ? " ASC" : " DESC";
        return "SELECT sr.*" + FROM + " ORDER BY " + expression + direction + ",sr.id DESC LIMIT #{query.parameters.limit} OFFSET #{query.parameters.offset}";
    }
}
