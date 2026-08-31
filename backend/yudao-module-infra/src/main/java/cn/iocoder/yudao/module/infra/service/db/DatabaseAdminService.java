package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminDataPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowDeleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowUpdateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDataRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDetailRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableRespVO;
import jakarta.validation.Valid;

import java.util.List;

public interface DatabaseAdminService {

    List<DatabaseAdminTableRespVO> getTableList(Long dataSourceConfigId, String name, String comment);

    DatabaseAdminTableDetailRespVO getTableDetail(Long dataSourceConfigId, String tableName);

    DatabaseAdminTableDataRespVO getTableDataPage(@Valid DatabaseAdminDataPageReqVO reqVO);

    void createRow(@Valid DatabaseAdminRowCreateReqVO reqVO);

    void updateRow(@Valid DatabaseAdminRowUpdateReqVO reqVO);

    void deleteRow(@Valid DatabaseAdminRowDeleteReqVO reqVO);

}
