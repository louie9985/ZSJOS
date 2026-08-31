package cn.iocoder.yudao.module.eam.service.category;

import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;

import java.io.IOException;

public interface EamCategoryImportService {

    EamCategoryImportRespVO preview(byte[] content) throws IOException;

    EamCategoryImportRespVO commit(byte[] content) throws IOException;

}
