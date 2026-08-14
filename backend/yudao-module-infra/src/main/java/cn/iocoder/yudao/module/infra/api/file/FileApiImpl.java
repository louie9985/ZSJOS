package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;

/**
 * 文件 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public String createFile(byte[] content, String name, String directory, String type) {
        return fileService.createFile(content, name, directory, type);
    }

    @Override
    public FileInfoRespDTO createFileInfo(byte[] content, String name, String directory, String type) {
        return BeanUtils.toBean(fileService.createFileInfo(content, name, directory, type), FileInfoRespDTO.class);
    }

    @Override
    public FileInfoRespDTO getFileInfo(Long fileId) {
        return BeanUtils.toBean(fileService.getFile(fileId), FileInfoRespDTO.class);
    }

    @Override
    public boolean deleteFileIfExists(Long fileId) throws Exception {
        return fileService.deleteFileIfExists(fileId);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public String presignGetUrl(Long fileId, Integer expirationSeconds) {
        return fileService.presignGetUrl(fileId, expirationSeconds);
    }

    @Override
    public Map<Long, String> presignGetUrls(Collection<Long> fileIds, Integer expirationSeconds) {
        return fileService.presignGetUrls(fileIds, expirationSeconds);
    }

}
