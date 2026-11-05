package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

public record OrderLeadCandidateRespVO(Long id, String status, SalesOrderRespVO.LeadProfileVO profile) {
    public String getStatusLabel() {
        if (status == null) return "未知状态";
        return switch (status) {
            case "submitted" -> "待处理";
            case "suspended" -> "已挂起";
            case "valid", "converted" -> "有效";
            case "invalid" -> "无效";
            case "closed" -> "已关闭";
            case "won" -> "已成交";
            default -> "未知状态";
        };
    }
}
