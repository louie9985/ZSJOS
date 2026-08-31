package cn.iocoder.yudao.module.infra.controller.admin.db;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminDataPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowDeleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowUpdateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDataRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableDetailRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminTableRespVO;
import cn.iocoder.yudao.module.infra.service.db.DatabaseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据库管理")
@RestController
@RequestMapping("/infra/database-admin")
@Validated
public class DatabaseAdminController {

    @Resource
    private DatabaseAdminService databaseAdminService;

    @GetMapping("/table/list")
    @Operation(summary = "获得数据库表列表")
    @Parameters({
            @Parameter(name = "dataSourceConfigId", description = "数据源配置编号", required = true, example = "0"),
            @Parameter(name = "name", description = "表名，模糊匹配", example = "system"),
            @Parameter(name = "comment", description = "表注释，模糊匹配", example = "用户")
    })
    @PreAuthorize("@ss.hasPermission('infra:database-admin:query')")
    public CommonResult<List<DatabaseAdminTableRespVO>> getTableList(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "comment", required = false) String comment) {
        return success(databaseAdminService.getTableList(dataSourceConfigId, name, comment));
    }

    @GetMapping("/table/detail")
    @Operation(summary = "获得数据库表详情")
    @PreAuthorize("@ss.hasPermission('infra:database-admin:query')")
    public CommonResult<DatabaseAdminTableDetailRespVO> getTableDetail(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @RequestParam("tableName") String tableName) {
        return success(databaseAdminService.getTableDetail(dataSourceConfigId, tableName));
    }

    @GetMapping("/data/page")
    @Operation(summary = "获得数据库表数据分页")
    @PreAuthorize("@ss.hasPermission('infra:database-admin:query')")
    public CommonResult<DatabaseAdminTableDataRespVO> getTableDataPage(@Valid DatabaseAdminDataPageReqVO reqVO) {
        return success(databaseAdminService.getTableDataPage(reqVO));
    }

    @PostMapping("/row/create")
    @Operation(summary = "新增数据库表行")
    @PreAuthorize("@ss.hasPermission('infra:database-admin:create')")
    public CommonResult<Boolean> createRow(@Valid @RequestBody DatabaseAdminRowCreateReqVO reqVO) {
        databaseAdminService.createRow(reqVO);
        return success(true);
    }

    @PutMapping("/row/update")
    @Operation(summary = "更新数据库表行")
    @PreAuthorize("@ss.hasPermission('infra:database-admin:update')")
    public CommonResult<Boolean> updateRow(@Valid @RequestBody DatabaseAdminRowUpdateReqVO reqVO) {
        databaseAdminService.updateRow(reqVO);
        return success(true);
    }

    @DeleteMapping("/row/delete")
    @Operation(summary = "删除数据库表行")
    @PreAuthorize("@ss.hasPermission('infra:database-admin:delete')")
    public CommonResult<Boolean> deleteRow(@Valid @RequestBody DatabaseAdminRowDeleteReqVO reqVO) {
        databaseAdminService.deleteRow(reqVO);
        return success(true);
    }

}
