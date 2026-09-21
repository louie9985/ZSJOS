package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Historical presentation never resolves names from current master data. */
@Data
@Slf4j
public class SalesOrderSnapshot {
    private Integer snapshotVersion;
    private LocalDateTime capturedAt;
    private Facts order;
    private Map<String, String> orderLabels = new LinkedHashMap<>();
    private Map<String, Selection> selections = new LinkedHashMap<>();
    private Actor submitter;
    private Actor formalSales;
    private SalesOrderRespVO.LeadProfileVO leadProfile;

    public record Actor(String subjectType, Long id, String name, String identity) {}
    public record Selection(String type, String value, String label) {}

    @Data
    public static class Facts {
        private String buyerName;
        private String studentName;
        private String studentNature;
        private String studentMobile;
        private String studentWechatId;
        private String provinceCode;
        private String provinceName;
        private String cityCode;
        private String cityName;
        private String agreedExamTime;
        private String classType;
        private String servicePeriod;
        private String studentSource;
        private String feeMode;
        private String paymentMethod;
        private String remark;
        private String studentSpecialRequirements;
        private String materialDeliveryContact;
        private String giftItems;
        private String giftShippingAddress;
        private String paymentVoucherRefs;
        private String repurchaseReason;
        private String formalOwnerIdentity;
        private BigDecimal totalAmount;
        private LocalDateTime customerPaidAt;
    }

    public static SalesOrderSnapshot read(String raw) {
        if (raw == null || raw.isBlank()) return new SalesOrderSnapshot();
        try {
            SalesOrderSnapshot value = JsonUtils.getObjectMapper().readValue(raw, SalesOrderSnapshot.class);
            if (value == null) return new SalesOrderSnapshot();
            return value;
        } catch (RuntimeException ex) {
            // No raw JSON or exception message: both can contain personal data.
            log.warn("Order history snapshot could not be parsed");
            SalesOrderSnapshot value = new SalesOrderSnapshot();
            value.setSnapshotVersion(-1);
            return value;
        }
    }

    public String label(String field) { return orderLabels == null ? null : orderLabels.get(field); }

    public SalesOrderDO project(SalesOrderDO current) {
        SalesOrderDO result = BeanUtils.toBean(current, SalesOrderDO.class);
        if (Integer.valueOf(2).equals(snapshotVersion) && order != null) {
            result.setBuyerName(order.getBuyerName());
            result.setStudentName(order.getStudentName());
            result.setStudentNature(order.getStudentNature());
            result.setStudentMobile(order.getStudentMobile());
            result.setStudentWechatId(order.getStudentWechatId());
            result.setProvinceCode(order.getProvinceCode());
            result.setProvinceName(order.getProvinceName());
            result.setCityCode(order.getCityCode());
            result.setCityName(order.getCityName());
            result.setAgreedExamTime(order.getAgreedExamTime());
            result.setClassType(order.getClassType());
            result.setServicePeriod(order.getServicePeriod());
            result.setStudentSource(order.getStudentSource());
            result.setFeeMode(order.getFeeMode());
            result.setPaymentMethod(order.getPaymentMethod());
            result.setRemark(order.getRemark());
            result.setStudentSpecialRequirements(order.getStudentSpecialRequirements());
            result.setMaterialDeliveryContact(order.getMaterialDeliveryContact());
            result.setGiftItems(order.getGiftItems());
            result.setGiftShippingAddress(order.getGiftShippingAddress());
            result.setPaymentVoucherRefs(order.getPaymentVoucherRefs());
            result.setRepurchaseReason(order.getRepurchaseReason());
            result.setFormalOwnerIdentity(order.getFormalOwnerIdentity());
            result.setTotalAmount(order.getTotalAmount());
            result.setCustomerPaidAt(order.getCustomerPaidAt());
        }
        return result;
    }

    public void apply(SalesOrderRespVO target) {
        target.setLeadProfile(leadProfile);
        target.setSubmitterUserName(submitter == null ? null : submitter.name());
        target.setFormalSalesUserName(formalSales == null ? null : formalSales.name());
        target.setStudentNatureLabelSnapshot(label("studentNature"));
        target.setServicePeriodLabelSnapshot(label("servicePeriod"));
        target.setStudentSourceLabelSnapshot(label("studentSource"));
        target.setFeeModeLabelSnapshot(label("feeMode"));
        target.setPaymentMethodLabelSnapshot(label("paymentMethod"));
        Map<String, String> missing = new LinkedHashMap<>();
        for (String field : List.of("studentNature", "servicePeriod", "studentSource", "feeMode", "paymentMethod")) {
            if (label(field) == null || label(field).isBlank()) missing.put(field, "history_not_recorded");
        }
        if (target.getSubmitterUserName() == null) missing.put("submitterUserName", "history_not_recorded");
        if (target.getFormalSalesUserName() == null) missing.put("formalSalesUserName", "history_not_recorded");
        if (target.getLeadId() != null && leadProfile == null) missing.put("leadProfile", "history_not_recorded");
        if (Integer.valueOf(-1).equals(snapshotVersion)) missing.put("snapshot", "invalid_snapshot");
        target.setHistoryMissingFields(missing);
    }
}
