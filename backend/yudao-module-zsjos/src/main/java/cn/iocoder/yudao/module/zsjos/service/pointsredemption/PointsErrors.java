package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
public final class PointsErrors {
    private PointsErrors() { }
    public static ServiceException invalid(String message) { return exception(new ErrorCode(1_090_296_001, message)); }
    public static ServiceException denied() { return exception(new ErrorCode(1_090_296_002, "无权操作该积分申请")); }
    public static ServiceException unavailable() { return exception(new ErrorCode(1_090_296_003, "积分平台尚未配置或配置无效")); }
    public static ServiceException process() { return exception(new ErrorCode(1_090_296_004, "请发布符合上级及董事长人工审批约束的积分申请流程")); }
}
