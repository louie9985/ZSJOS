package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售订单审批的业务内容。
 *
 * <p>businessKey 形如 {@code sales-order:12}。订单是双人审批（报名 + 财务），
 * 且支持多轮修订，因此摘要必须带轮次——同一订单可能出现多条待办。
 *
 * <p>读取权限刻意取"能读管理视图"的较宽口径：审批人往往不是订单的提交人或归属销售，
 * 用 getOwn 会把审批人挡在门外，正是这个功能要解决的问题。
 */
@Component
public class SalesOrderContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = SalesOrderConstants.BUSINESS_KEY_PREFIX;

    @Resource
    private SalesOrderService salesOrderService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "sales_order";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(PREFIX)) {
            return null;
        }
        String id = businessKey.substring(PREFIX.length());
        return id.isBlank() ? null : id;
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        SalesOrderRespVO order = load(businessId, viewerId);
        if (order == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(order.getOrderNo());
        brief.setSubtitle(order.getStudentName() + " · 金额 " + BpmApprovalFormat.amount(order.getTotalAmount()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("学员", order.getStudentName()),
                BpmApprovalFieldVO.of("订单金额", BpmApprovalFormat.amount(order.getTotalAmount())),
                BpmApprovalFieldVO.of("订单类型", orderTypeText(order.getOrderType())),
                BpmApprovalFieldVO.of("状态", statusText(order.getStatus()))));
        brief.setRoute("/zsjos/sales-order-approvals");
        brief.setQuery(new java.util.LinkedHashMap<>());
        brief.getQuery().put("orderId", order.getId());
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        SalesOrderRespVO order = load(businessId, viewerId);
        if (order == null) {
            return null;
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(order.getOrderNo());
        card.setStatusText(statusText(order.getStatus()));
        card.setRoute("/zsjos/sales-order-approvals");
        card.getQuery().put("orderId", order.getId());

        card.getGroups().add(group("订单信息", List.of(
                BpmApprovalFieldVO.of("订单号", order.getOrderNo()),
                BpmApprovalFieldVO.of("状态", statusText(order.getStatus())),
                BpmApprovalFieldVO.of("订单类型", orderTypeText(order.getOrderType())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(order.getSubmittedAt())),
                BpmApprovalFieldVO.of("提交人", userName(order.getSubmitterUserId())),
                BpmApprovalFieldVO.of("正式销售", userName(order.getFormalSalesUserId())))));

        List<BpmApprovalFieldVO> studentFields = new ArrayList<>();
        studentFields.add(BpmApprovalFieldVO.of("学员姓名", order.getStudentName()));
        studentFields.add(BpmApprovalFieldVO.of("购买人", order.getBuyerName()));
        studentFields.add(BpmApprovalFieldVO.of("班型", order.getClassType()));
        studentFields.add(BpmApprovalFieldVO.of("学员性质", order.getStudentNatureLabelSnapshot()));
        studentFields.add(BpmApprovalFieldVO.of("服务周期", order.getServicePeriodLabelSnapshot()));
        studentFields.add(BpmApprovalFieldVO.of("约定考试时间", order.getAgreedExamTime()));
        studentFields.add(BpmApprovalFieldVO.wide("地区", regionText(order.getProvinceName(), order.getCityName())));
        studentFields.add(BpmApprovalFieldVO.wide("学员特殊要求", order.getStudentSpecialRequirements()));
        card.getGroups().add(group("学员信息", studentFields));

        card.getGroups().add(group("金额与支付", List.of(
                BpmApprovalFieldVO.of("订单金额", BpmApprovalFormat.amount(order.getTotalAmount())),
                BpmApprovalFieldVO.of("支付方式", order.getPaymentMethodLabelSnapshot()),
                BpmApprovalFieldVO.of("收费方式", order.getFeeModeLabelSnapshot()),
                BpmApprovalFieldVO.of("客户支付时间", BpmApprovalFormat.dateTime(order.getCustomerPaidAt())))));

        if (order.getRemark() != null || order.getRepurchaseReason() != null) {
            card.getGroups().add(wideGroup("备注",
                    joinNonBlank(order.getRemark(), order.getRepurchaseReason())));
        }
        if (order.getApprovalRoundNo() != null) {
            card.getGroups().add(group("审批轮次", List.of(
                    BpmApprovalFieldVO.of("轮次", "第 " + order.getApprovalRoundNo() + " 轮"),
                    BpmApprovalFieldVO.of("轮次状态", roundText(order.getApprovalRoundStatus())),
                    BpmApprovalFieldVO.wide("审批意见", order.getDecisionReason()))));
        }
        return card;
    }

    /**
     * 优先走管理视图（审批人的正常口径）；失败再退回本人视图（提交人自查）。
     * 两者都不可读时返回 null，由审批中心退回通用展示。
     */
    private SalesOrderRespVO load(String businessId, Long viewerId) {
        Long id;
        try {
            id = Long.valueOf(businessId);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (viewerId != null && permissionApi.hasAnyPermissions(viewerId,
                SalesOrderConstants.PERMISSION_QUERY, SalesOrderConstants.PERMISSION_QUERY_TEAM)) {
            try {
                return salesOrderService.getManagement(id, viewerId);
            } catch (Exception ignored) {
                // 落到本人视图再试一次。
            }
        }
        try {
            return salesOrderService.getOwn(id, viewerId);
        } catch (Exception ex) {
            return null;
        }
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    private static String regionText(String province, String city) {
        if (province == null && city == null) {
            return null;
        }
        return (province == null ? "" : province) + (city == null ? "" : city);
    }

    private static String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static String orderTypeText(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SalesOrderConstants.ORDER_TYPE_FIRST_PURCHASE -> "首购";
            case SalesOrderConstants.ORDER_TYPE_REPURCHASE -> "复购";
            default -> type;
        };
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SalesOrderConstants.STATUS_PENDING_APPROVAL -> "审批中";
            case SalesOrderConstants.STATUS_REVISION_REQUIRED -> "待修订";
            case SalesOrderConstants.STATUS_EFFECTIVE -> "已生效";
            case SalesOrderConstants.STATUS_SUPERSEDED -> "已被取代";
            case SalesOrderConstants.STATUS_TERMINATED -> "已终止";
            default -> status;
        };
    }

    private static String roundText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SalesOrderConstants.ROUND_PENDING -> "审批中";
            case SalesOrderConstants.ROUND_APPROVED -> "已通过";
            case SalesOrderConstants.ROUND_REJECTED -> "已驳回";
            case SalesOrderConstants.ROUND_TERMINATED -> "已终止";
            default -> status;
        };
    }

    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(fields));
        return group;
    }

    private static BpmApprovalDetailVO.Group wideGroup(String title, String value) {
        BpmApprovalDetailVO.Group group = group(title, List.of(BpmApprovalFieldVO.of(title, value)));
        group.setSpan(true);
        return group;
    }
}
