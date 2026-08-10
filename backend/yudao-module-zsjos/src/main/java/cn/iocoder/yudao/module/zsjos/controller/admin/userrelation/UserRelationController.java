package cn.iocoder.yudao.module.zsjos.controller.admin.userrelation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentLogPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentLogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationScenePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import cn.iocoder.yudao.module.zsjos.service.userrelation.UserRelationSceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 用户关系场景")
@RestController
@RequestMapping("/zsjos/user-relation")
public class UserRelationController {

    @Resource
    private UserRelationSceneService sceneService;
    @Resource
    private LeadAssignmentService relationService;

    @PostMapping("/scene/create")
    @Operation(summary = "创建用户关系场景")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:create')")
    public CommonResult<Long> createScene(@Valid @RequestBody UserRelationSceneSaveReqVO reqVO) {
        return success(sceneService.createScene(reqVO));
    }

    @PutMapping("/scene/update")
    @Operation(summary = "更新用户关系场景")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:update')")
    public CommonResult<Boolean> updateScene(@Valid @RequestBody UserRelationSceneSaveReqVO reqVO) {
        sceneService.updateScene(reqVO);
        return success(true);
    }

    @DeleteMapping("/scene/delete")
    @Operation(summary = "删除用户关系场景")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:delete')")
    public CommonResult<Boolean> deleteScene(@RequestParam("id") Long id) {
        sceneService.deleteScene(id);
        return success(true);
    }

    @GetMapping("/scene/get")
    @Operation(summary = "获得用户关系场景")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:query')")
    public CommonResult<UserRelationSceneRespVO> getScene(@RequestParam("id") Long id) {
        return success(sceneService.getScene(id));
    }

    @GetMapping("/scene/page")
    @Operation(summary = "获得用户关系场景分页")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:query')")
    public CommonResult<PageResult<UserRelationSceneRespVO>> getScenePage(
            @Valid UserRelationScenePageReqVO reqVO) {
        return success(sceneService.getScenePage(reqVO));
    }

    @GetMapping("/scene/simple-list")
    @Operation(summary = "获得用户关系场景精简列表")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation-scene:query')")
    public CommonResult<List<UserRelationSceneRespVO>> getSceneSimpleList() {
        return success(sceneService.getSceneSimpleList());
    }

    @GetMapping("/relation/page")
    @Operation(summary = "获得场景关系分页")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation:query')")
    public CommonResult<PageResult<UserRelationRespVO>> getRelationPage(
            @Valid UserRelationPageReqVO reqVO) {
        return success(relationService.getAdminRelationPage(reqVO));
    }

    @GetMapping("/target/simple-list")
    @Operation(summary = "获得场景可选目标用户")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getTargetSimpleList(
            @RequestParam("sceneCode") String sceneCode) {
        return success(relationService.getAdminEligibleTargetUsers(sceneCode));
    }

    @PutMapping("/relation/save")
    @Operation(summary = "保存场景用户关系")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation:update')")
    public CommonResult<Boolean> saveRelations(@Valid @RequestBody UserRelationSaveReqVO reqVO) {
        relationService.saveAdminRelations(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/log/page")
    @Operation(summary = "获得场景关系操作日志")
    @PreAuthorize("@ss.hasPermission('zsjos:user-relation:log-query')")
    public CommonResult<PageResult<LeadAssignmentLogRespVO>> getLogPage(
            @Valid LeadAssignmentLogPageReqVO reqVO) {
        return success(relationService.getAdminLogPage(reqVO));
    }

}
