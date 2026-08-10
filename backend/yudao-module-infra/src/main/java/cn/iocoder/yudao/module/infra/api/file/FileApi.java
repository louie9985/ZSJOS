package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * 文件 API 接口
 *
 * @author 芋道源码
 */
public interface FileApi {

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @return 文件路径
     */
    default String createFile(byte[] content) {
        return createFile(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @return 文件路径
     */
    default String createFile(byte[] content, String name) {
        return createFile(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @param directory 目录，允许空
     * @param type 文件的 MIME 类型，允许空
     * @return 文件路径
     */
    String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content,
                      String name, String directory, String type);

    /**
     * 保存文件，并返回可用于业务持久化的文件标识和元数据。
     */
    FileInfoRespDTO createFileInfo(@NotEmpty(message = "文件内容不能为空") byte[] content,
                                   String name, String directory, String type);

    /**
     * 获得文件元数据。
     */
    FileInfoRespDTO getFileInfo(@NotNull(message = "文件编号不能为空") Long fileId);

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(@NotEmpty(message = "URL 不能为空") String url,
                         Integer expirationSeconds);

    /**
     * 按文件上传时使用的存储配置生成读取签名，主配置切换不影响历史文件。
     */
    String presignGetUrl(@NotNull(message = "文件编号不能为空") Long fileId,
                         Integer expirationSeconds);

    /**
     * 批量生成文件读取签名。
     */
    Map<Long, String> presignGetUrls(@NotEmpty(message = "文件编号不能为空") Collection<Long> fileIds,
                                     Integer expirationSeconds);

}
