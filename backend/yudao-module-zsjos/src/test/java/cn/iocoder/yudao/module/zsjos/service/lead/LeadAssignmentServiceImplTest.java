package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentLogPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentLogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.userrelation.UserRelationSceneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.LeadAssignmentConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.NEW_MEDIA_OPERATOR;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.SALES_SPECIALIST;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_ASSIGNMENT_SCOPE_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentServiceImplTest {

    private static final Long OPERATOR_ID = 99L;

    @InjectMocks
    private LeadAssignmentServiceImpl service;
    @Mock
    private LeadAssignmentRelationMapper relationMapper;
    @Mock
    private LeadAssignmentRelationLogMapper relationLogMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private PostApi postApi;
    @Mock
    private DeptApi deptApi;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private UserRelationSceneService sceneService;

    private AdminUserRespDTO sourceUser;

    @BeforeEach
    void setUp() {
        sourceUser = user(1L, 10L, "来源员工", CommonStatusEnum.ENABLE.getStatus());
        lenient().when(sceneService.getEnabledSceneByCode(SCENE)).thenReturn(leadScene());
    }

    @Test
    void getAssignableSalesUsersReturnsOnlyActiveBoundSales() {
        AdminUserRespDTO enabledSales = user(2L, 20L, "销售甲", CommonStatusEnum.ENABLE.getStatus());
        AdminUserRespDTO disabledSales = user(3L, 20L, "销售乙", CommonStatusEnum.DISABLE.getStatus());
        when(postApi.getPostByCode(SALES_SPECIALIST)).thenReturn(post(12L));
        when(adminUserApi.getUserListByPostIds(Set.of(12L)))
                .thenReturn(List.of(enabledSales, disabledSales));
        when(relationMapper.selectListBySourceUserIds(SCENE, Set.of(1L))).thenReturn(List.of(
                relation(1L, 2L, CommonStatusEnum.ENABLE.getStatus()),
                relation(1L, 3L, CommonStatusEnum.ENABLE.getStatus()),
                relation(1L, 4L, CommonStatusEnum.ENABLE.getStatus()),
                relation(1L, 5L, CommonStatusEnum.DISABLE.getStatus())));
        when(deptApi.getDeptMap(Set.of(20L))).thenReturn(Map.of(20L, dept(20L)));

        List<LeadAssignmentUserRespVO> result = service.getAssignableSalesUsers(1L);

        assertEquals(List.of(2L), result.stream().map(LeadAssignmentUserRespVO::getId).toList());
    }

    @Test
    void saveRelationsAppendEnablesAndInsertsTargets() {
        stubSavePrerequisites();
        stubSalesUsers(2L, 3L, 4L);
        LeadAssignmentRelationDO active = relation(1L, 2L, CommonStatusEnum.ENABLE.getStatus());
        LeadAssignmentRelationDO disabled = relation(1L, 3L, CommonStatusEnum.DISABLE.getStatus());
        when(relationMapper.selectListBySourceUserIds(SCENE, Set.of(1L)))
                .thenReturn(List.of(active, disabled));

        service.saveRelations(saveReq(MODE_APPEND, Set.of(3L, 4L)), OPERATOR_ID);

        assertEquals(CommonStatusEnum.ENABLE.getStatus(), active.getStatus());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), disabled.getStatus());
        verify(relationMapper).updateById(disabled);
        assertInsertedTarget(4L);
    }

    @Test
    void saveRelationsReplaceDisablesMissingTargets() {
        stubSavePrerequisites();
        stubSalesUsers(2L, 3L, 4L);
        LeadAssignmentRelationDO active = relation(1L, 2L, CommonStatusEnum.ENABLE.getStatus());
        LeadAssignmentRelationDO disabled = relation(1L, 3L, CommonStatusEnum.DISABLE.getStatus());
        when(relationMapper.selectListBySourceUserIds(SCENE, Set.of(1L)))
                .thenReturn(List.of(active, disabled));

        service.saveRelations(saveReq(MODE_REPLACE, Set.of(3L, 4L)), OPERATOR_ID);

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), active.getStatus());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), disabled.getStatus());
        verify(relationMapper).updateById(active);
        verify(relationMapper).updateById(disabled);
        assertInsertedTarget(4L);
    }

    @Test
    void saveRelationsRemoveDisablesRequestedTargets() {
        stubSavePrerequisites();
        stubSalesUsers(2L, 3L);
        LeadAssignmentRelationDO kept = relation(1L, 2L, CommonStatusEnum.ENABLE.getStatus());
        LeadAssignmentRelationDO removed = relation(1L, 3L, CommonStatusEnum.ENABLE.getStatus());
        when(relationMapper.selectListBySourceUserIds(SCENE, Set.of(1L)))
                .thenReturn(List.of(kept, removed));

        service.saveRelations(saveReq(MODE_REMOVE, Set.of(3L)), OPERATOR_ID);

        assertEquals(CommonStatusEnum.ENABLE.getStatus(), kept.getStatus());
        assertEquals(CommonStatusEnum.DISABLE.getStatus(), removed.getStatus());
        verify(relationMapper).updateById(removed);
        verify(relationMapper, never()).insert(any(LeadAssignmentRelationDO.class));
    }

    @Test
    void saveRelationsRejectsSourceOutsideManagedDepartments() {
        when(postApi.getPostByCode(NEW_MEDIA_OPERATOR)).thenReturn(post(11L));
        when(adminUserApi.getUserListByPostIds(Set.of(11L))).thenReturn(List.of(sourceUser));
        when(permissionApi.hasAnyPermissions(OPERATOR_ID, PERMISSION_MANAGE_ALL)).thenReturn(false);
        when(deptApi.getDeptListByLeaderUserId(OPERATOR_ID)).thenReturn(List.of(dept(20L)));
        when(deptApi.getChildDeptList(20L)).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveRelations(saveReq(MODE_REPLACE, Set.of(2L)), OPERATOR_ID));

        assertEquals(LEAD_ASSIGNMENT_SCOPE_DENIED.getCode(), exception.getCode());
        verifyNoInteractions(relationMapper, relationLogMapper);
    }

    @Test
    void getLogPageHidesOtherDepartmentAndMixedDepartmentLogs() {
        when(permissionApi.hasAnyPermissions(OPERATOR_ID, PERMISSION_MANAGE_ALL)).thenReturn(false);
        when(deptApi.getDeptListByLeaderUserId(OPERATOR_ID)).thenReturn(List.of(dept(10L)));
        when(deptApi.getChildDeptList(10L)).thenReturn(List.of());
        LeadAssignmentRelationLogDO visible = log(1L, "1", "3");
        LeadAssignmentRelationLogDO hidden = log(2L, "2", "3");
        LeadAssignmentRelationLogDO mixed = log(3L, "1,2", "3");
        when(relationLogMapper.selectList(any(LeadAssignmentLogPageReqVO.class)))
                .thenReturn(List.of(mixed, hidden, visible));
        Map<Long, AdminUserRespDTO> users = List.of(
                        sourceUser,
                        user(2L, 20L, "其他部门员工", CommonStatusEnum.ENABLE.getStatus()),
                        user(3L, 20L, "销售甲", CommonStatusEnum.ENABLE.getStatus()),
                        user(OPERATOR_ID, 10L, "部门负责人", CommonStatusEnum.ENABLE.getStatus()))
                .stream().collect(Collectors.toMap(AdminUserRespDTO::getId, Function.identity()));
        when(adminUserApi.getUserMap(anySet())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return users.entrySet().stream().filter(entry -> ids.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
        LeadAssignmentLogPageReqVO reqVO = new LeadAssignmentLogPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        PageResult<LeadAssignmentLogRespVO> result = service.getLogPage(reqVO, OPERATOR_ID);

        assertEquals(1L, result.getTotal());
        assertEquals(List.of(1L), result.getList().stream().map(LeadAssignmentLogRespVO::getId).toList());
        assertEquals("来源员工", result.getList().get(0).getSourceUsers());
    }

    @Test
    void saveAdminRelationsUsesScenePostsWithoutDepartmentScope() {
        UserRelationSceneDO scene = leadScene();
        scene.setCode("custom_scene");
        when(sceneService.getEnabledSceneByCode("custom_scene")).thenReturn(scene);
        when(postApi.getPostByCode(NEW_MEDIA_OPERATOR)).thenReturn(post(11L));
        when(postApi.getPostByCode(SALES_SPECIALIST)).thenReturn(post(12L));
        when(adminUserApi.getUserListByPostIds(Set.of(11L))).thenReturn(List.of(sourceUser));
        when(adminUserApi.getUserListByPostIds(Set.of(12L))).thenReturn(List.of(
                user(2L, 20L, "销售甲", CommonStatusEnum.ENABLE.getStatus())));
        when(relationMapper.selectListBySourceUserIds("custom_scene", Set.of(1L)))
                .thenReturn(List.of());
        UserRelationSaveReqVO reqVO = new UserRelationSaveReqVO();
        reqVO.setSceneCode("custom_scene");
        reqVO.setSourceUserIds(Set.of(1L));
        reqVO.setTargetUserIds(Set.of(2L));
        reqVO.setMode(MODE_REPLACE);

        service.saveAdminRelations(reqVO, OPERATOR_ID);

        verifyNoInteractions(permissionApi, deptApi);
        assertInsertedTarget(2L);
    }

    private void stubSalesUsers(Long... ids) {
        List<AdminUserRespDTO> users = List.of(ids).stream()
                .map(id -> user(id, 20L, "销售" + id, CommonStatusEnum.ENABLE.getStatus()))
                .toList();
        when(adminUserApi.getUserListByPostIds(Set.of(12L))).thenReturn(users);
    }

    private void stubSavePrerequisites() {
        when(postApi.getPostByCode(NEW_MEDIA_OPERATOR)).thenReturn(post(11L));
        when(postApi.getPostByCode(SALES_SPECIALIST)).thenReturn(post(12L));
        when(adminUserApi.getUserListByPostIds(Set.of(11L))).thenReturn(List.of(sourceUser));
        when(permissionApi.hasAnyPermissions(OPERATOR_ID, PERMISSION_MANAGE_ALL)).thenReturn(true);
    }

    private void assertInsertedTarget(Long targetUserId) {
        ArgumentCaptor<LeadAssignmentRelationDO> captor =
                ArgumentCaptor.forClass(LeadAssignmentRelationDO.class);
        verify(relationMapper).insert(captor.capture());
        assertEquals(targetUserId, captor.getValue().getTargetUserId());
        assertTrue(CommonStatusEnum.ENABLE.getStatus().equals(captor.getValue().getStatus()));
    }

    private static LeadAssignmentSaveReqVO saveReq(String mode, Set<Long> targetIds) {
        LeadAssignmentSaveReqVO reqVO = new LeadAssignmentSaveReqVO();
        reqVO.setSourceUserIds(Set.of(1L));
        reqVO.setTargetUserIds(targetIds);
        reqVO.setMode(mode);
        return reqVO;
    }

    private static AdminUserRespDTO user(Long id, Long deptId, String nickname, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setDeptId(deptId);
        user.setNickname(nickname);
        user.setMobile("13800000000");
        user.setStatus(status);
        return user;
    }

    private static PostRespDTO post(Long id) {
        PostRespDTO post = new PostRespDTO();
        post.setId(id);
        post.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return post;
    }

    private static DeptRespDTO dept(Long id) {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(id);
        dept.setName("部门" + id);
        return dept;
    }

    private static LeadAssignmentRelationDO relation(Long sourceId, Long targetId, Integer status) {
        LeadAssignmentRelationDO relation = new LeadAssignmentRelationDO();
        relation.setSourceUserId(sourceId);
        relation.setTargetUserId(targetId);
        relation.setStatus(status);
        return relation;
    }

    private static LeadAssignmentRelationLogDO log(Long id, String sourceIds, String targetIds) {
        LeadAssignmentRelationLogDO log = new LeadAssignmentRelationLogDO();
        log.setId(id);
        log.setSourceUserIds(sourceIds);
        log.setTargetUserIds(targetIds);
        log.setOperatorUserId(OPERATOR_ID);
        log.setActionType(MODE_REPLACE);
        return log;
    }

    private static UserRelationSceneDO leadScene() {
        UserRelationSceneDO scene = new UserRelationSceneDO();
        scene.setCode(SCENE);
        scene.setSourcePostCode(NEW_MEDIA_OPERATOR);
        scene.setTargetPostCode(SALES_SPECIALIST);
        scene.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return scene;
    }

}
