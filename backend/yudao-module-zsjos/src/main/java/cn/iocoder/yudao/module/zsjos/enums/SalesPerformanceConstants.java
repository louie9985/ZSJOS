package cn.iocoder.yudao.module.zsjos.enums;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
public interface SalesPerformanceConstants {
 ErrorCode PERMISSION_DENIED=new ErrorCode(1900090001,"无权查看或修改该业绩范围");
 ErrorCode INVALID_REQUEST=new ErrorCode(1900090002,"{}");
 String BIZ_TARGET="sales-performance-target";
}
