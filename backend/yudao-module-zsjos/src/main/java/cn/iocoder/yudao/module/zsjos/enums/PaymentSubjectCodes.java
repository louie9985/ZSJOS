package cn.iocoder.yudao.module.zsjos.enums;

/** 新支付单的业务路由编码；实际商户身份和凭据始终读取当前租户配置。 */
public final class PaymentSubjectCodes {
    public static final String SCHOOL = "school";
    public static final String COMPANY = "company";

    private PaymentSubjectCodes() {
    }
}
