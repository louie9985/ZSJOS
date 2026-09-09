package cn.iocoder.yudao.module.infra.framework.file.core.client;

/**
 * 对象存储中的真实文件元数据。
 */
public record FileObjectMetadata(long size, String contentType, String etag) {
}
