package cn.iocoder.yudao.module.infra.framework.file.core.client;

import java.util.Map;

/**
 * 浏览器直传所需的签名地址和请求头。
 */
public record FileUploadPresignResult(String uploadUrl, Map<String, String> headers) {
}
