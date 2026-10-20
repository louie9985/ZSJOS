package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

/** SQL expressions shared by pagination and candidate reads; no user input is interpolated. */
public final class DeliveryClassFilterSql {
    private DeliveryClassFilterSql() {}

    public static final String PROJECT_FROM = " FROM zsjos_service_relation sr JOIN zsjos_order_item oi"
            + " ON oi.id=sr.order_item_id AND oi.tenant_id=sr.tenant_id AND oi.deleted=0"
            + " WHERE sr.teaching_delivery=1 AND sr.class_id=zsjos_delivery_class.id AND sr.tenant_id=zsjos_delivery_class.tenant_id"
            + " AND sr.deleted=0 AND sr.status IN ('active','paused','completed')";
    public static final String SNAPSHOT = "IF(JSON_VALID(oi.product_snapshot),oi.product_snapshot,JSON_OBJECT())";
    public static final String CATEGORY = "JSON_UNQUOTE(JSON_EXTRACT(" + SNAPSHOT + ", '$.categoryId'))";
    public static final String PROJECT_EXISTS = "EXISTS (SELECT 1" + PROJECT_FROM
            + " AND " + CATEGORY + " = CAST({0} AS CHAR))";

    // Same suffix grammar as DeliveryClassDates; names and mutable calendar associations are not dates.
    private static final String SUFFIX = "SUBSTRING_INDEX(exam_schedule_snapshot, ' · ', -1)";
    private static final String START = "SUBSTRING_INDEX(" + SUFFIX + ", '~', 1)";
    private static final String END = "SUBSTRING_INDEX(" + SUFFIX + ", '~', -1)";
    private static String validDay(String value) {
        return "(SUBSTRING(" + value + ",6,2) BETWEEN '01' AND '12'"
                + " AND SUBSTRING(" + value + ",9,2) BETWEEN '01' AND"
                + " DATE_FORMAT(LAST_DAY(CONCAT(SUBSTRING(" + value + ",1,7),'-01')),'%d'))";
    }
    private static final String VALID = SUFFIX
            + " REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}(~[0-9]{4}-[0-9]{2}-[0-9]{2})?$'"
            + " AND " + validDay(START) + " AND " + validDay(END) + " AND " + END + ">=" + START;
    public static final String DATE_START = "(CASE WHEN " + VALID + " THEN " + START + " ELSE NULL END)";
    public static final String DATE_END = "(CASE WHEN " + VALID + " THEN " + END + " ELSE NULL END)";
}
