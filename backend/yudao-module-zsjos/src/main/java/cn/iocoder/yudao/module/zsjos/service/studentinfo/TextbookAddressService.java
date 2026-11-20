package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import jakarta.validation.constraints.*;
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
    @Resource private TextbookShippingPermission permissions;
    @Resource private TextbookShippingMapper shipping;
    @Resource private StudentInfoTaskMapper tasks;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentArchiveRevisionMapper revisions;
    @Resource private StudentInfoFormValueMapper answers;

    public record Product(Long itemId, String name, String sku, BigDecimal quantity, TextbookShippingMapper.Shipment shipment) {}
    public record Destination(String name, String mobile, String address) {}
    public record Row(Long orderId, String orderNo, String studentName, String wechat, LocalDateTime effectiveAt,
                      Destination destination, List<Product> products) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<Row> page(int pageNo, int pageSize, String keyword, Long actor) {
        var query = new SalesOrderMyPageReqVO();
        query.setStatus(STATUS_EFFECTIVE);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        var page = orders.selectTextbookPage(query, actor, permissions.canViewAll(actor));
        var rows = new ArrayList<Row>();
        for (var order : page.getList()) {
            var shipments = new HashMap<Long, TextbookShippingMapper.Shipment>();
            shipping.forOrder(order.getId(), TenantContextHolder.getRequiredTenantId()).forEach(s -> shipments.put(s.itemId(), s));
            var groups = new LinkedHashMap<Destination, List<Product>>();
            for (var item : items.selectListByOrderId(order.getId())) {
                var snapshot = LeadProductSnapshot.readHistoricalQuietly(item.getProductSnapshot());
                var product = new Product(item.getId(), snapshot == null ? item.getProductRef() : snapshot.name(),
                        snapshot == null ? item.getSkuRef() : snapshot.skuName(), item.getQuantity(), shipments.get(item.getId()));
                groups.computeIfAbsent(destination(order, item), ignored -> new ArrayList<>()).add(product);
            }
            groups.forEach((destination, products) -> rows.add(new Row(order.getId(), order.getOrderNo(), order.getStudentName(),
                    order.getStudentWechatId(), order.getEffectiveAt(), destination, products)));
        }
        // Page complete orders so a split shipment never straddles two pages.
        return new PageResult<>(rows, page.getTotal());
    }

    public record ShippingInput(@NotBlank @Size(max=100) String carrier, @NotBlank @Size(max=100) String trackingNo,
                                @NotNull @Min(0) Integer version) {}

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType="textbook-shipping", bizId="#orderId", action="update")
    public void saveShipping(Long orderId, Long itemId, ShippingInput input, Long actor) {
        var tenant = TenantContextHolder.getRequiredTenantId();
        // Ownership and order status may change after the initial object permission check.
        if (!permissions.visible(orders.selectByIdForUpdate(orderId, tenant), actor)) throw exception(LEAD_PERMISSION_DENIED);
        if (shipping.save(tenant, orderId, itemId, input.version(), input.carrier().strip(), input.trackingNo().strip(), actor) != 1)
            throw invalidParamException("商品明细或邮寄信息已变化，请刷新后再填写");
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
