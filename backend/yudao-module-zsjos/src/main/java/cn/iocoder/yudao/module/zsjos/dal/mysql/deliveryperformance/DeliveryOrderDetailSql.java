package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

import java.util.List;

/** Every projection starts from the same authorized entry scope; optional filters never enlarge it. */
public final class DeliveryOrderDetailSql {
    private DeliveryOrderDetailSql() {}

    private static String key(String expression) {
        return "CASE WHEN " + expression + " IS NULL OR TRIM(" + expression + ")='' THEN 'missing' ELSE CONCAT('value:',HEX(" + expression + ")) END";
    }
    private static String label(String expression, String missing) {
        return "COALESCE(NULLIF(TRIM(" + expression + "),''),'" + missing + "')";
    }
    // HEX compares the full UTF-8 key exactly under case-insensitive MySQL collations as well as H2.
    private static final String SELF = "HEX(o.order_type)=HEX('repurchase') OR HEX(o.group_key)=HEX('self')";
    private static final String INBOUND = "HEX(o.group_key)=HEX('inbound')";
    private static String base(DeliveryOrderDetailQuery q) {
        return "<script>WITH " + DeliveryPerformanceDetailSql.orderDefinitions(q) + ", entry AS ("
            + "SELECT o.id,o.number,l.id lead_id,l.lead_no,o.occurred_at submitted_at,o.amount,b.amount total_amount,"
            + "CASE WHEN " + SELF + " THEN 'self' WHEN " + INBOUND + " THEN 'inbound' ELSE 'unknown' END source_key,"
            + "CASE WHEN " + SELF + " THEN '非引流' WHEN " + INBOUND + " THEN '线上引流' ELSE '其他' END source_label,"
            + key("o.order_type") + " order_type_key,CASE HEX(o.order_type) WHEN HEX('first_purchase') THEN '首购' WHEN HEX('repurchase') THEN '复购' ELSE '历史订单类型未识别' END order_type_label,"
            + key("l.lead_category_label_snapshot") + " category_key," + label("l.lead_category_label_snapshot", "客资分类快照缺失") + " category_label,"
            + key("o.label") + " channel_key," + label("o.label", "来源渠道快照缺失") + " channel_label "
            + "FROM orders o JOIN orders_base b ON b.id=o.id LEFT JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=#{tenant} AND l.deleted=0 "
            + "WHERE o.occurred_at &gt;= #{start} AND o.occurred_at &lt; #{end}),"
            + "item_names AS (SELECT i.order_id,i.product_ref ref,JSON_UNQUOTE(JSON_EXTRACT(i.product_snapshot,'$.name')) label "
            + "FROM zsjos_order_item i JOIN entry e ON e.id=i.order_id WHERE i.tenant_id=#{tenant} AND i.deleted=0),"
            + "entry_products AS (SELECT DISTINCT order_id,ref," + label("label", "商品名称快照缺失") + " label,"
            + "CONCAT(" + key("ref") + ",'.'," + key("label") + ") product_key FROM item_names)";
    }
    private static String filtered() {
        String sql = ", filtered AS (SELECT e.* FROM entry e WHERE 1=1";
        for (String field : List.of("source", "orderType", "category", "channel")) {
            String column = field.equals("orderType") ? "order_type" : field;
            sql += "<if test=\"filters." + field + "Key != null\"> AND HEX(e." + column + "_key)=HEX(#{filters." + field + "Key})</if>";
        }
        return sql + "<if test=\"filters.productKey != null\"> AND EXISTS (SELECT 1 FROM entry_products p WHERE p.order_id=e.id AND HEX(p.product_key)=HEX(#{filters.productKey}))</if>"
            + "<if test=\"filters.keyword != null and filters.keyword != ''\"> AND (LOCATE(LOWER(#{filters.keyword}),LOWER(COALESCE(e.number,'')))&gt;0 OR LOCATE(LOWER(#{filters.keyword}),LOWER(COALESCE(e.lead_no,'')))&gt;0)</if>)";
    }
    private static String pageRelation() { return ", page_rows AS (SELECT * FROM filtered ORDER BY submitted_at DESC,id DESC LIMIT #{size} OFFSET #{offset})"; }
    public static String summary(DeliveryOrderDetailQuery q) { return base(q) + " SELECT COUNT(*) orders,COALESCE(SUM(amount),0) amount FROM entry</script>"; }
    public static String filteredSummary(DeliveryOrderDetailQuery q) { return base(q) + filtered() + " SELECT COUNT(*) orders,COALESCE(SUM(amount),0) amount FROM filtered</script>"; }
    public static String page(DeliveryOrderDetailQuery q) { return base(q) + filtered() + pageRelation() + " SELECT * FROM page_rows ORDER BY submitted_at DESC,id DESC</script>"; }
    public static String pageProducts(DeliveryOrderDetailQuery q) { return base(q) + filtered() + pageRelation() + " SELECT p.order_id,p.product_key `key`,p.ref,p.label FROM entry_products p JOIN page_rows r ON r.id=p.order_id ORDER BY p.order_id,p.product_key</script>"; }
    public static String options(DeliveryOrderDetailQuery q) {
        String sql = base(q);
        boolean first = true;
        for (String dimension : List.of("source", "order_type", "category", "channel")) {
            sql += (first ? " " : " UNION ALL ") + "SELECT '" + dimension + "' dimension," + dimension + "_key `key`,MAX(" + dimension + "_label) label,COUNT(*) count FROM entry GROUP BY " + dimension + "_key";
            first = false;
        }
        return sql + " UNION ALL SELECT 'product' dimension,product_key `key`,MAX(label) label,COUNT(DISTINCT order_id) count FROM entry_products GROUP BY product_key</script>";
    }
}
