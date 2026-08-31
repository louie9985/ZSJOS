package cn.iocoder.yudao.module.zsjos.framework.audit;

public record ZsjosAuditOperation(String category, String action, String targetType, String targetId,
                                  String sourceType, String requestMethod, String requestPath) {
}
