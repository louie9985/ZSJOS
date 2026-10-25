package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Teaching closure must not change the shared media relationship's lifecycle. */
public final class TeachingServiceLifecycle {
    private TeachingServiceLifecycle() {}
    public static final String CLOSE = "zsjos:student-service:close";
    public static final String RESTORE = "zsjos:student-service:restore";
    public static final String STATUS_SQL = "CASE WHEN sr.teaching_closed=1 THEN 'closed' ELSE sr.status END";
    public static final ErrorCode CLOSED = new ErrorCode(1_900_079_001, "课程服务已关闭，仅可查看历史");
    public static final ErrorCode INVALID_STATE = new ErrorCode(1_900_079_002, "课程服务状态已变化，请刷新后重试");
    public static final ErrorCode TARGET_INVALID = new ErrorCode(1_900_079_003, "请选择管理范围内有效的服务中普通班级");
    public static final ErrorCode KEY_CONFLICT = new ErrorCode(1_900_079_004, "重复请求内容不一致，请刷新后重试");
    public static boolean hasExplicitStatus(cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO group) {
        if (group == null) return false;
        if (group.getConditions() != null && group.getConditions().stream().anyMatch(c -> "service.status".equals(c.getFieldKey()))) return true;
        return group.getGroups() != null && group.getGroups().stream().anyMatch(TeachingServiceLifecycle::hasExplicitStatus);
    }
    public static boolean closed(ServiceRelationDO row) { return row != null && Boolean.TRUE.equals(row.getTeachingClosed()); }
    public static String status(ServiceRelationDO row) { return closed(row) ? "closed" : row.getStatus(); }
    public static void requireOpen(ServiceRelationDO row) { if (closed(row)) throw exception(CLOSED); }
}
