package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportPreviewRespVO;

public interface EamAssetLedgerImportService {

    EamAssetImportPreviewRespVO preview(byte[] content, String fileName, boolean updateExisting);

    EamAssetImportPreviewRespVO commit(byte[] content, String fileName, boolean updateExisting);

}
