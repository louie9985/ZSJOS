package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderSceneCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderSceneMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_ORDER_IDEMPOTENCY_CONFLICT;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceImplTest {
    @Mock WorkOrderSceneMapper sceneMapper;
    @Mock WorkOrderMapper orderMapper;
    @Mock WorkOrderHistoryMapper historyMapper;
    @Mock PostApi postApi;
    @Mock DeptApi deptApi;
    @Mock DictDataApi dictDataApi;
    @Mock AdminUserApi adminUserApi;
    @Mock FileApi fileApi;
    @InjectMocks WorkOrderServiceImpl service;

    @Test void completeMovesAssignedOrderToAcceptance() {
        WorkOrderDO row = order(11L, 22L);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(row);
        WorkOrderActionReqVO req = action(0, "complete-1");
        when(orderMapper.transition(1L, 0, "IN_PROGRESS", "COMPLETED_PENDING_ACCEPTANCE", null)).thenReturn(1);
        service.complete(1L, req, 22L);
        verify(orderMapper).transition(1L, 0, "IN_PROGRESS", "COMPLETED_PENDING_ACCEPTANCE", null);
        verify(historyMapper).insert(any(WorkOrderHistoryDO.class));
    }

    @Test void completeRejectsNonAssignee() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order(11L, 22L));
        WorkOrderActionReqVO req = action(0, "complete-2");
        assertThrows(RuntimeException.class, () -> service.complete(1L, req, 99L));
        verify(orderMapper, never()).transition(anyLong(), anyInt(), anyString(), anyString(), nullable(String.class));
    }

    @Test void returnedOrderCanBeCompletedAgain() {
        WorkOrderDO row = order(11L, 22L); row.setStatus("RETURNED"); row.setVersion(1);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(row);
        WorkOrderActionReqVO req = action(1, "complete-3");
        when(orderMapper.transition(1L, 1, "RETURNED", "COMPLETED_PENDING_ACCEPTANCE", null)).thenReturn(1);
        service.complete(1L, req, 22L);
        verify(orderMapper).transition(1L, 1, "RETURNED", "COMPLETED_PENDING_ACCEPTANCE", null);
    }

    @Test void requiredFieldDefinitionCanBeCreatedWithoutSubmittedValues() {
        WorkOrderSceneCreateReqVO req = sceneRequest();
        when(sceneMapper.selectByCode("support")).thenReturn(null);
        stubPost("source", 101L);
        stubPost("target", 102L);
        doAnswer(invocation -> {
            WorkOrderSceneDO row = invocation.getArgument(0);
            row.setId(8L);
            return 1;
        }).when(sceneMapper).insert(any(WorkOrderSceneDO.class));

        assertEquals(8L, service.createScene(req, 11L));
        verify(sceneMapper).insert(ArgumentMatchers.<WorkOrderSceneDO>argThat(
                row -> row.getFieldsJson().contains("required")));
    }

    @Test void createRejectsMissingRequiredValue() {
        WorkOrderSceneDO scene = directScene();
        when(sceneMapper.selectByCode("support")).thenReturn(scene);
        stubEligibleUser(11L, "source", 101L, "发起人");
        stubEligibleUser(22L, "target", 102L, "处理人");
        WorkOrderCreateReqVO req = createRequest(Map.of());

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(req, 11L));

        assertEquals(WORK_ORDER_FIELD_INVALID.getCode(), error.getCode());
        verify(orderMapper, never()).insert(any(WorkOrderDO.class));
    }

    @Test void exactCreateReplayReturnsBeforeMutableExternalValidation() {
        WorkOrderCreateReqVO req = createRequest(Map.of("subject", "已提交"));
        String fingerprint = ReflectionTestUtils.invokeMethod(service, "fingerprint", (Object) new Object[]{
                "create", 11L, req.getSceneCode(), req.getTargetUserId(),
                ReflectionTestUtils.invokeMethod(service, "canonicalize", req.getValues()), List.of()});
        WorkOrderDO replay = new WorkOrderDO();
        replay.setId(9L); replay.setCommandUserId(11L); replay.setRequestFingerprint(fingerprint);
        when(orderMapper.selectByIdempotencyKey("create-1")).thenReturn(replay);

        assertEquals(9L, service.create(req, 11L));

        verifyNoInteractions(sceneMapper, postApi, deptApi, dictDataApi, adminUserApi, fileApi);
        verify(orderMapper, never()).insert(any(WorkOrderDO.class));
    }

    @Test void createBatchResolvesDynamicFieldSnapshots() {
        WorkOrderSceneDO scene = directScene();
        scene.setFieldsJson(JsonUtils.toJsonString(List.of(
                new WorkOrderFieldDefinition("reviewer", "审核人", "user", true, null),
                new WorkOrderFieldDefinition("department", "部门", "department", true, null),
                new WorkOrderFieldDefinition("priority", "优先级", "dictionary", true, "work_order_priority"))));
        when(sceneMapper.selectByCode("support")).thenReturn(scene);
        stubEligibleUser(11L, "source", 101L, "发起人");
        stubEligibleUser(22L, "target", 102L, "处理人");
        AdminUserRespDTO reviewer = new AdminUserRespDTO();
        reviewer.setId(33L); reviewer.setNickname("审核人"); reviewer.setStatus(0);
        when(adminUserApi.getUserMap(Set.of(33L))).thenReturn(Map.of(33L, reviewer));
        DeptRespDTO department = new DeptRespDTO();
        department.setId(44L); department.setName("履约部"); department.setStatus(0);
        when(deptApi.getDeptMap(Set.of(44L))).thenReturn(Map.of(44L, department));
        DictDataRespDTO priority = new DictDataRespDTO();
        priority.setDictType("work_order_priority"); priority.setValue("high"); priority.setLabel("高");
        when(dictDataApi.getDictDataList("work_order_priority")).thenReturn(List.of(priority));
        doAnswer(invocation -> {
            WorkOrderDO row = invocation.getArgument(0);
            row.setId(8L);
            return 1;
        }).when(orderMapper).insert(any(WorkOrderDO.class));
        WorkOrderCreateReqVO req = createRequest(Map.of(
                "reviewer", 33L, "department", 44L, "priority", "high"));

        assertEquals(8L, service.create(req, 11L));

        verify(adminUserApi).getUserMap(Set.of(33L));
        verify(deptApi).getDeptMap(Set.of(44L));
        verify(dictDataApi).validateDictDataList("work_order_priority", Set.of("high"));
        verify(dictDataApi).getDictDataList("work_order_priority");
        verify(orderMapper).insert(ArgumentMatchers.<WorkOrderDO>argThat(row ->
                row.getValueJson().contains("审核人") && row.getValueJson().contains("履约部")
                        && row.getValueJson().contains("work_order_priority")
                        && row.getValueJson().contains("高")));
    }

    @Test void exactActionReplayUsesLockedRowAndDoesNotTransitionAgain() {
        WorkOrderDO row = order(11L, 22L);
        WorkOrderActionReqVO req = action(0, "complete-replay");
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(row);
        String fingerprint = ReflectionTestUtils.invokeMethod(service, "actionFingerprint", "complete", 1L, req, 22L);
        WorkOrderHistoryDO replay = new WorkOrderHistoryDO();
        replay.setWorkOrderId(1L); replay.setOperation("complete"); replay.setOperatorUserId(22L);
        replay.setRequestFingerprint(fingerprint);
        when(historyMapper.selectByOrderAndKey(1L, "complete-replay")).thenReturn(replay);

        service.complete(1L, req, 22L);

        verify(orderMapper, never()).transition(anyLong(), anyInt(), anyString(), anyString(), nullable(String.class));
    }

    @Test void reusedActionKeyWithDifferentOperationIsConflict() {
        WorkOrderDO row = order(11L, 22L);
        WorkOrderActionReqVO req = action(0, "shared-key");
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(row);
        WorkOrderHistoryDO replay = new WorkOrderHistoryDO();
        replay.setWorkOrderId(1L); replay.setOperation("claim"); replay.setOperatorUserId(22L);
        replay.setRequestFingerprint("different");
        when(historyMapper.selectByOrderAndKey(1L, "shared-key")).thenReturn(replay);

        ServiceException error = assertThrows(ServiceException.class, () -> service.complete(1L, req, 22L));

        assertEquals(WORK_ORDER_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    @Test void claimPersistsTargetSnapshotInAtomicUpdate() {
        WorkOrderDO row = order(11L, null); row.setStatus("POOL");
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(row);
        WorkOrderSceneDO scene = directScene(); scene.setAssignmentMode("PUBLIC_POOL");
        when(sceneMapper.selectByCode("support")).thenReturn(scene);
        stubEligibleUser(22L, "target", 102L, "处理人");
        WorkOrderActionReqVO req = action(0, "claim-1");
        when(orderMapper.claim(1L, 22L, "处理人", 0)).thenReturn(1);

        service.claim(1L, req, 22L);

        verify(orderMapper).claim(1L, 22L, "处理人", 0);
        verify(historyMapper).insert(ArgumentMatchers.<WorkOrderHistoryDO>argThat(
                history -> "claim".equals(history.getOperation())));
    }

    @Test void attachmentListRejectsDuplicatesBeforeFileLookup() {
        ServiceException error = assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "validateAttachments", List.of(5L, 5L), 11L));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID.getCode(),
                error.getCode());
        verifyNoInteractions(fileApi);
    }

    @Test void poolUsesFrameworkPageParameters() {
        WorkOrderSceneDO scene = directScene(); scene.setAssignmentMode("PUBLIC_POOL");
        when(sceneMapper.selectByCode("support")).thenReturn(scene);
        stubEligibleUser(22L, "target", 102L, "处理人");
        when(orderMapper.selectPool(any(PageParam.class), eq("support"))).thenReturn(PageResult.empty());

        assertEquals(0, service.pool("support", 2, 30, 22L).getTotal());
        verify(orderMapper).selectPool(argThat(page -> page.getPageNo() == 2 && page.getPageSize() == 30),
                eq("support"));
    }

    private WorkOrderDO order(Long source, Long target) {
        WorkOrderDO row = new WorkOrderDO(); row.setId(1L); row.setSceneCode("support"); row.setSourceUserId(source); row.setTargetUserId(target); row.setStatus("IN_PROGRESS"); row.setVersion(0); return row;
    }
    private WorkOrderActionReqVO action(int version, String key) { WorkOrderActionReqVO req = new WorkOrderActionReqVO(); req.setVersion(version); req.setIdempotencyKey(key); return req; }

    private WorkOrderSceneCreateReqVO sceneRequest() {
        WorkOrderSceneCreateReqVO req = new WorkOrderSceneCreateReqVO();
        req.setCode("support"); req.setName("支持工单"); req.setSourcePostCode("source");
        req.setTargetPostCode("target"); req.setAssignmentMode("DIRECT"); req.setStatus(1);
        req.setFields(List.of(new WorkOrderFieldDefinition("subject", "主题", "text", true, null)));
        return req;
    }

    private WorkOrderSceneDO directScene() {
        WorkOrderSceneDO scene = new WorkOrderSceneDO();
        scene.setCode("support"); scene.setName("支持工单"); scene.setSourcePostCode("source");
        scene.setTargetPostCode("target"); scene.setAssignmentMode("DIRECT"); scene.setStatus(1);
        scene.setFieldsJson(JsonUtils.toJsonString(sceneRequest().getFields()));
        return scene;
    }

    private WorkOrderCreateReqVO createRequest(Map<String, Object> values) {
        WorkOrderCreateReqVO req = new WorkOrderCreateReqVO();
        req.setSceneCode("support"); req.setTargetUserId(22L); req.setValues(values);
        req.setAttachmentIds(List.of()); req.setIdempotencyKey("create-1");
        return req;
    }

    private void stubPost(String code, Long id) {
        PostRespDTO post = new PostRespDTO(); post.setId(id); post.setCode(code); post.setStatus(0);
        when(postApi.getPostByCode(code)).thenReturn(post);
    }

    private void stubEligibleUser(Long userId, String postCode, Long postId, String nickname) {
        stubPost(postCode, postId);
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(userId); user.setNickname(nickname);
        user.setStatus(0); user.setPostIds(Set.of(postId));
        when(adminUserApi.getUser(userId)).thenReturn(user);
    }
}
