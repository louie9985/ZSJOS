package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
public final class PointsErrors {
    private PointsErrors() { }
    public static ServiceException invalid(String message) { return exception(new ErrorCode(1_090_296_001, message)); }
    public static ServiceException denied() { return exception(new ErrorCode(1_090_296_002, "无权操作该积分申请")); }
    public static ServiceException unavailable() { return exception(new ErrorCode(1_090_296_003, "积分平台尚未配置或配置无效")); }
    public static ServiceException process() { return process("积分审批节点配置不完整，请检查并重新发布流程"); }
    public static ServiceException process(String message) { return exception(new ErrorCode(1_090_296_004, message)); }
    public static ServiceException processNotPublished() { return exception(new ErrorCode(1_090_296_005, "积分申请流程未发布或已停用，请发布并启用流程")); }
    public static ServiceException processStructure() { return exception(new ErrorCode(1_090_296_006, "积分审批流程必须为无条件串行审批，可启用连续同人自动去重及纯超时提醒，请检查其他自动审批、分支或不支持的事件并重新发布")); }
    public static ServiceException processApprover(String message) { return exception(new ErrorCode(1_090_296_007, message)); }
}
