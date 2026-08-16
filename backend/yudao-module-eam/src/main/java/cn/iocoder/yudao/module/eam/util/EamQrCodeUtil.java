package cn.iocoder.yudao.module.eam.util;

import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;

/**
 * EAM 二维码工具类
 */
public class EamQrCodeUtil {

    private static final int DEFAULT_SIZE = 300;

    private EamQrCodeUtil() {
    }

    /**
     * 生成二维码 PNG 字节数组
     *
     * @param content 二维码内容
     * @param size    边长像素，传 null 使用默认值
     */
    public static byte[] generatePng(String content, Integer size) {
        int edge = size != null && size > 0 ? size : DEFAULT_SIZE;
        QrConfig config = QrConfig.create().setWidth(edge).setHeight(edge).setMargin(1);
        return QrCodeUtil.generatePng(content, config);
    }

}
