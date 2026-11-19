package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;

@Service
public class TextbookAddressService {
    @Resource private SalesOrderMapper orders;
    @Resource private SalesOrderItemMapper items;
    @Resource private SalesOrderObjectPermissionService permissions;
    @Resource private StudentInfoTaskMapper tasks;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentArchiveRevisionMapper revisions;
    @Resource private StudentInfoFormValueMapper answers;

    public record Product(String name, String sku, BigDecimal quantity) {}
    public record Destination(String name, String mobile, String address) {}
    public record Row(String orderNo, String studentName, String wechat, LocalDateTime effectiveAt,
                      Destination destination, List<Product> products) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<Row> page(int pageNo, int pageSize, String keyword, Long actor) {
        var query = new SalesOrderMyPageReqVO();
        query.setStatus(STATUS_EFFECTIVE);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        var page = orders.selectManagementPage(permissions.resolveManagementScope(actor), query, null);
        var rows = new ArrayList<Row>();
        for (var order : page.getList()) {
            var groups = new LinkedHashMap<Destination, List<Product>>();
            for (var item : items.selectListByOrderId(order.getId())) {
                var snapshot = LeadProductSnapshot.readHistoricalQuietly(item.getProductSnapshot());
                var product = new Product(snapshot == null ? item.getProductRef() : snapshot.name(),
                        snapshot == null ? item.getSkuRef() : snapshot.skuName(), item.getQuantity());
                groups.computeIfAbsent(destination(order, item), ignored -> new ArrayList<>()).add(product);
            }
            groups.forEach((destination, products) -> rows.add(new Row(order.getOrderNo(), order.getStudentName(),
                    order.getStudentWechatId(), order.getEffectiveAt(), destination, products)));
        }
        // Page complete orders so a split shipment never straddles two pages.
        return new PageResult<>(rows, page.getTotal());
    }

    private Destination destination(SalesOrderDO order, SalesOrderItemDO item) {
        var task = tasks.byItem(item.getId());
        if (task == null || task.getFormId() == null || !Objects.equals(order.getId(), task.getOrderId())
                || !Objects.equals(order.getPersonId(), task.getPersonId())) return null;
        var form = forms.selectById(task.getFormId());
        if (form == null || !"SUBMITTED".equals(form.getStatus()) || !Objects.equals(item.getId(), form.getOrderItemId())
                || !Objects.equals(task.getPersonId(), form.getPersonId())) return null;
        var revision = revisions.latest(form.getId());
        var values = revision == null ? answers.byForm(form.getId())
                : JsonUtils.parseArray(revision.getAnswersJson(), StudentInfoFormValueDO.class);
        return fromAnswers(values);
    }

    static Destination fromAnswers(List<StudentInfoFormValueDO> values) {
        Map<String, String> fields = new HashMap<>();
        for (var answer : values) {
            if (answer.getValueText() != null) fields.put(answer.getFieldKey(), answer.getValueText().strip());
        }
        var address = fields.get("textbook_shipping_address");
        return address == null || address.isBlank() ? null : new Destination(fields.get("name"), fields.get("mobile"), address);
    }
}
