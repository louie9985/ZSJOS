package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance;

/** Count and page use the same authorized population, including historical duplicate facts. */
public final class DeliveryPerformanceDetailSql {
    private DeliveryPerformanceDetailSql() {}
    static String scoped(String fact) {
        String body = fact.replaceFirst("(?s)\\s*ORDER BY[^<]+</script>", "</script>")
                .replace("<script>", "").replace("</script>", "");
        return "SELECT f.* FROM (SELECT raw.*, CASE WHEN raw.business_domain IN ('sales','delivery') THEN raw.business_domain "
                + "WHEN raw.business_domain IS NULL AND raw.org_source='frozen' THEN "
                + "(SELECT org.business_domain FROM zsjos_performance_org org WHERE org.tenant_id=#{tenant} AND org.deleted=0 "
                + "AND org.dept_id=raw.dept_id AND org.create_time &lt;= raw.occurred_at AND org.update_time &lt;= raw.occurred_at) "
                + "ELSE NULL END resolved_domain FROM (" + body + ") raw) f WHERE "
                + "<choose><when test=\"unclassified\">f.resolved_domain IS NULL</when><otherwise>f.resolved_domain=#{businessDomain}</otherwise></choose> AND <choose>"
                + "<when test=\"allDepartments\"><choose><when test=\"missingDepartment\">1=1</when>"
                + "<otherwise>f.org_source='frozen' AND f.dept_id IS NOT NULL</otherwise></choose></when>"
                + "<when test=\"departments != null and !departments.isEmpty()\">f.org_source='frozen' AND f.dept_id IN "
                + "<foreach collection=\"departments\" item=\"dept\" open=\"(\" separator=\",\" close=\")\">#{dept}</foreach></when>"
                + "<otherwise>1=0</otherwise></choose>";
    }
    private static String interval(String column, String end) {
        return column + " &gt;= #{start} AND " + column + " &lt; #{" + end + "}";
    }
    private static String equal(String expression) {
        return "CAST(" + expression + " AS BINARY)=CAST(#{groupKey} AS BINARY)";
    }
    static String orderDefinitions(DeliveryPerformanceDetailQuery q) {
        String result = "orders_base AS (" + scoped(DeliveryPerformanceFactSql.ORDERS) + ")";
        if (q.getGroupKey() == null || q.getDimension() == null) return result + ", orders AS (SELECT * FROM orders_base)";
        return result + switch (q.getDimension()) {
            // Retain the legacy unknown key while matching the current report's display label.
            case "source" -> ", orders AS (SELECT * FROM orders_base WHERE CAST(CASE WHEN CAST(order_type AS BINARY)=CAST('repurchase' AS BINARY) OR CAST(group_key AS BINARY)=CAST('self' AS BINARY) THEN 'self|非引流' WHEN CAST(group_key AS BINARY)=CAST('inbound' AS BINARY) THEN 'inbound|线上引流' ELSE 'unknown|其他' END AS BINARY)"
                    + "=CAST(CASE WHEN #{groupKey}='unknown|历史来源缺失' THEN 'unknown|其他' ELSE #{groupKey} END AS BINARY))";
            case "contributor" -> ", orders AS (SELECT * FROM orders_base WHERE " + equal("COALESCE(CAST(user_id AS CHAR),'')") + ")";
            case "product" -> ", products AS (" + scoped(DeliveryPerformanceFactSql.PRODUCTS) + "), product_amounts AS ("
                    + "SELECT lead_id,SUM(amount) amount FROM products WHERE " + equal("CONCAT(COALESCE(CAST(group_key AS CHAR),'unknown'),'|',COALESCE(label,'历史产品名称缺失'))")
                    + " GROUP BY lead_id), orders AS (SELECT o.id,o.number,o.lead_id,o.source_type,o.source_provider_user_id,o.submission_source_type,o.source_provider_recorded,o.provider_owner_type,o.provider_owner_id,o.user_id,o.user_name,"
                    + "o.occurred_at,p.amount,o.order_type,o.received_at,o.assignment_id,o.group_key,o.label,o.channel_code,o.dept_id,o.center_id "
                    + "FROM orders_base o JOIN product_amounts p ON p.lead_id=o.id)";
            default -> ", orders AS (SELECT * FROM orders_base)";
        };
    }
    public static String conversionOrders(DeliveryPerformanceDetailQuery q) {
        return "<script>WITH " + orderDefinitions(q) + " SELECT " + conversionColumns(true)
                + " FROM orders r " + conversionOwnershipJoin() + " WHERE " + interval("r.occurred_at", "end")
                + " ORDER BY r.occurred_at DESC,r.id DESC</script>";
    }
    private static String conversionColumns(boolean order) {
        return "r.*,cl.owner_user_id,(cl.owner_user_id=r.user_id AND ch.id IS NOT NULL AND "
                + (order ? "COALESCE(r.assignment_id,(SELECT oh.id FROM zsjos_lead_assignment_history oh "
                         + "WHERE oh.tenant_id=cl.tenant_id AND oh.lead_id=cl.id AND oh.deleted=0 "
                         + "AND oh.action_type IN ('accept','claim','transfer','recycle','release_to_claim_pool','public_pool') "
                         + "AND oh.occurred_at &lt;= r.occurred_at ORDER BY oh.occurred_at DESC,oh.id DESC LIMIT 1))=ch.id "
                         + "AND r.received_at=COALESCE(ca.received_at,ch.occurred_at) AND r.occurred_at &gt;= ch.occurred_at"
                         : "r.assignment_id=ch.id") + ") current_assignment";
    }
    private static String conversionOwnershipJoin() {
        // Only conversion resolves current ownership. Shared receipt facts must retain actual historical workload.
        // Legacy links may identify an "other" event rather than a receipt. Resolve those through real
        // ownership transitions across ALL owners, including unowned intervals; never treat "other" as a receipt.
        return "LEFT JOIN zsjos_lead cl ON cl.id=r.lead_id AND cl.tenant_id=#{tenant} AND cl.deleted=0 "
                + "LEFT JOIN zsjos_lead_assignment_history ch ON ch.tenant_id=cl.tenant_id AND ch.lead_id=cl.id AND ch.deleted=0 "
                + "AND ch.id=COALESCE(CASE WHEN EXISTS (SELECT 1 FROM zsjos_lead_assignment_history linked "
                + "WHERE linked.id=cl.current_assignment_history_id AND linked.tenant_id=cl.tenant_id "
                + "AND linked.lead_id=cl.id AND linked.deleted=0 AND linked.action_type='other') "
                + "THEN NULL ELSE cl.current_assignment_history_id END,(SELECT h.id FROM zsjos_lead_assignment_history h "
                + "WHERE h.tenant_id=cl.tenant_id AND h.lead_id=cl.id AND h.deleted=0 "
                + "AND h.action_type IN ('accept','claim','transfer','recycle','release_to_claim_pool','public_pool') "
                + "ORDER BY h.occurred_at DESC,h.id DESC LIMIT 1)) "
                + "AND ch.to_owner_user_id=cl.owner_user_id AND ch.action_type IN ('accept','claim','transfer') "
                + "LEFT JOIN zsjos_performance_attribution ca ON ca.tenant_id=cl.tenant_id AND ca.deleted=0 "
                + "AND ca.fact_type='ASSIGNMENT' AND ca.fact_id=ch.id ";
    }
    public static String conversionReceipts(DeliveryPerformanceDetailQuery q) {
        // Retain out-of-period histories for candidate leads without re-running the order query for normal pages.
        if(q.getConversionLeadIds().size() <= 512) {
            return "<script>WITH receipts AS (" + scoped(DeliveryPerformanceFactSql.RECEIPTS) + ") SELECT " + conversionColumns(false)
                    + " FROM receipts r " + conversionOwnershipJoin() + " WHERE ("
                    + interval("r.received_at", "end") + ") <if test=\"!conversionLeadIds.isEmpty()\"> OR r.lead_id IN "
                    + "<foreach collection=\"conversionLeadIds\" item=\"lead\" open=\"(\" separator=\",\" close=\")\">#{lead}</foreach></if> "
                    + "ORDER BY r.occurred_at DESC,r.id DESC</script>";
        }
        // Large periods use a relational predicate instead of an unbounded IN list.
        return "<script>WITH " + orderDefinitions(q) + ", receipts AS (" + scoped(DeliveryPerformanceFactSql.RECEIPTS) + ") "
                + "SELECT " + conversionColumns(false) + " FROM receipts r " + conversionOwnershipJoin()
                + " WHERE (" + interval("r.received_at", "end") + ") OR EXISTS "
                + "(SELECT 1 FROM orders o WHERE o.lead_id=r.lead_id AND " + interval("o.occurred_at", "end") + ") "
                + "ORDER BY r.occurred_at DESC,r.id DESC</script>";
    }
    private static String relation(DeliveryPerformanceDetailQuery q) {
        if (DeliveryCurrentFollowUpSql.supports(q.getMetric())) return DeliveryCurrentFollowUpSql.relation(q);
        return switch (q.getMetric()) {
            case "orders" -> orderDefinitions(q) + ", result AS (SELECT * FROM orders WHERE " + interval("occurred_at", "end") + ")";
            case "leads", "valid" -> "receipts AS (" + scoped(DeliveryPerformanceFactSql.RECEIPTS) + "), ranked AS ("
                    + "SELECT r.*,ROW_NUMBER() OVER(PARTITION BY lead_id ORDER BY occurred_at DESC,id DESC) receipt_rank FROM receipts r WHERE "
                    + interval("received_at", "end") + ("valid".equals(q.getMetric()) ? " AND status IN ('valid','converted','won')" : "")
                    + ("category".equals(q.getDimension()) ? " AND " + equal("COALESCE(category,'历史分类缺失')") : "")
                    + ("stage".equals(q.getDimension()) ? " AND " + equal("COALESCE(stage,'阶段未记录')") : "")
                    + "), result AS (SELECT lead_id id,number,COALESCE(category,'历史分类缺失') label,received_at occurred_at,status,"
                    + "occurred_at receipt_occurred_at,id receipt_id FROM ranked WHERE receipt_rank=1)";
            case "assigned", "missed", "followUps" -> "facts AS (" + scoped("followUps".equals(q.getMetric()) ? DeliveryPerformanceFactSql.FOLLOW_UPS : DeliveryPerformanceFactSql.ASSIGNMENTS)
                    + "), result AS (SELECT * FROM facts WHERE " + interval("occurred_at", "end")
                    + ("followUps".equals(q.getMetric()) ? "" : " AND group_key='" + ("assigned".equals(q.getMetric()) ? "dispatch" : "timeout") + "'") + ")";
            default -> "facts AS (" + scoped(DeliveryPerformanceFactSql.TASKS) + "), result AS (SELECT * FROM facts WHERE "
                    + "NOT (group_key IN ('lead_first_follow_up','lead_qualification') AND COALESCE(generation_source,'') IN ('sales_self_sourced_auto','education_self_sourced_auto')) AND "
                    + switch (q.getMetric()) {
                        case "accept" -> "status='pending' AND group_key='lead_assignment_accept'";
                        case "qualification" -> "current_qualification=TRUE AND (status='pending' OR outcome='overdue') AND group_key='lead_qualification' AND due_at &lt;= #{now}";
                        case "todayFollowUp" -> "status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &gt;= #{today} AND due_at &lt; #{tomorrow}";
                        case "overdueFollowUp" -> "status='pending' AND group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND due_at &lt; #{now}";
                        default -> "group_key IN ('lead_first_follow_up','lead_follow_up_reminder') AND " + interval("due_at", "dueEnd");
                    } + ")";
        };
    }
    public static String count(DeliveryPerformanceDetailQuery q) {
        if ("leads".equals(q.getMetric()) || "valid".equals(q.getMetric())) return "<script>WITH receipts AS ("
                + scoped(DeliveryPerformanceFactSql.RECEIPTS) + ") SELECT COUNT(DISTINCT lead_id) FROM receipts WHERE "
                + interval("received_at", "end") + ("valid".equals(q.getMetric()) ? " AND status IN ('valid','converted','won')" : "")
                + ("category".equals(q.getDimension()) ? " AND " + equal("COALESCE(category,'历史分类缺失')") : "")
                + ("stage".equals(q.getDimension()) ? " AND " + equal("COALESCE(stage,'阶段未记录')") : "") + "</script>";
        return "<script>WITH " + relation(q) + " SELECT COUNT(*) FROM result</script>";
    }
    public static String page(DeliveryPerformanceDetailQuery q) {
        String order = switch (q.getMetric()) {
            case "todayFollowUp", "overdueFollowUp" -> "due_at,id";
            case "leads", "valid" -> "receipt_occurred_at DESC,receipt_id DESC,id";
            case "orders", "assigned", "missed", "followUps" -> "occurred_at DESC,id DESC";
            default -> "CASE WHEN due_at IS NULL THEN 1 ELSE 0 END,due_at,id,assignment_id,outcome,completed_at";
        };
        return "<script>WITH " + relation(q) + " SELECT * FROM result ORDER BY " + order + " LIMIT #{size} OFFSET #{offset}</script>";
    }
    public static String historicalPeople(DeliveryPerformanceDetailQuery q) {
        String snapshots = "<script>SELECT a.user_id,a.user_name,a.business_domain,a.org_source,a.dept_id,a.create_time occurred_at FROM zsjos_performance_attribution a WHERE a.tenant_id=#{tenant} AND a.deleted=0 AND a.user_id IS NOT NULL</script>";
        return "<script>SELECT user_id,MAX(user_name) user_name FROM (" + scoped(snapshots) + ") visible GROUP BY user_id</script>";
    }
    private static String unclassifiedRelation() {
        return "WITH orders AS (" + scoped(DeliveryPerformanceFactSql.ORDERS) + "), receipts AS (" + scoped(DeliveryPerformanceFactSql.RECEIPTS)
                + "), followups AS (" + scoped(DeliveryPerformanceFactSql.FOLLOW_UPS) + "), tasks AS (" + scoped(DeliveryPerformanceFactSql.TASKS)
                + "), assignments AS (" + scoped(DeliveryPerformanceFactSql.ASSIGNMENTS) + "), unknown_facts AS ("
                + "SELECT id,number,'order' kind,'成交订单' label,occurred_at,amount,dept_id,org_source,'生效' state FROM orders UNION ALL "
                + "SELECT id,number,'receipt','接收客资',occurred_at,NULL,dept_id,org_source,status FROM receipts UNION ALL "
                + "SELECT id,number,'followup','跟进',occurred_at,NULL,dept_id,org_source,NULL FROM followups UNION ALL "
                + "SELECT id,number,'task',group_key,occurred_at,NULL,dept_id,org_source,status FROM tasks UNION ALL "
                + "SELECT id,number,'assignment',group_key,occurred_at,NULL,dept_id,org_source,NULL FROM assignments) ";
    }
    public static String unclassifiedCount(DeliveryPerformanceDetailQuery q) {
        return "<script>" + unclassifiedRelation() + "SELECT COUNT(*) FROM unknown_facts</script>";
    }
    public static String unclassifiedPage(DeliveryPerformanceDetailQuery q) {
        return "<script>" + unclassifiedRelation() + "SELECT *,CASE WHEN dept_id IS NULL THEN '未记录业务域和历史部门' "
                + "WHEN COALESCE(org_source,'') != 'frozen' THEN '未记录可靠的历史组织快照' ELSE '历史部门业务域缺少有效证据' END attribution_reason "
                + "FROM unknown_facts ORDER BY occurred_at DESC,kind,id LIMIT #{size} OFFSET #{offset}</script>";
    }
    public static String firstDate(DeliveryPerformanceDetailQuery q) {
        return "<script>WITH orders AS (" + scoped(DeliveryPerformanceFactSql.ORDERS) + "), receipts AS (" + scoped(DeliveryPerformanceFactSql.RECEIPTS)
                + ") SELECT MIN(at) FROM (SELECT MIN(occurred_at) at FROM orders UNION ALL SELECT MIN(received_at) at FROM receipts) dates</script>";
    }
}
