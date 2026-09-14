package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewPublishReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.content.ContentObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator.ACTION_APPROVE;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_ACCEPTANCE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_READY_TO_PUBLISH;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReviewBatchServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OPERATOR_ID = 7L;

    @InjectMocks private ContentReviewBatchService service;
    @Mock private ContentReviewBatchMapper batchMapper;
    @Mock private ContentReviewBatchItemMapper itemMapper;
    @Mock private ContentReviewRelationMapper relationMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ContentVersionMapper contentVersionMapper;
    @Mock private ContentVersionFileMapper contentVersionFileMapper;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private ContentReviewConfigService configService;
    @Mock private ContentReviewMaterialService reviewMaterialService;
    @Mock private ContentReviewAccessService accessService;
    @Mock private ContentService contentService;
    @Mock private ContentObjectPermissionProvider contentPermissionProvider;
    @Mock private MaterialService materialService;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createRejectsTwentyOneItemsBeforeDatabaseAccess() {
        ContentReviewBatchCreateReqVO request = new ContentReviewBatchCreateReqVO();
        request.setContentVersionIds(LongStream.rangeClosed(1, 21).boxed().toList());

        assertServiceCode(CONTENT_REVIEW_BATCH_ITEMS_INVALID,
                () -> service.create(request, OPERATOR_ID));

        verifyNoInteractions(contentVersionMapper, contentMapper, itemMapper, batchMapper);
    }

    @Test
    void createAcceptsTwentyCompleteCurrentVersionsForOneAccount() {
        List<Long> versionIds = LongStream.rangeClosed(1, 20).boxed().toList();
        List<ContentDO> contents = new ArrayList<>();
        for (Long versionId : versionIds) {
            long contentId = 1000L + versionId;
            ContentVersionDO version = completeVersion(versionId, contentId);
            ContentDO content = reviewableContent(contentId, 90L, 1, 0);
            contents.add(content);
            when(contentVersionMapper.selectByIdForUpdate(versionId, TENANT_ID)).thenReturn(version);
        }
        // 历史内容保留原运营快照；组批资格以账号当前运营为准。
        contents.getFirst().setOwnerOperatorUserId(999L);
        when(contentMapper.selectByIds(any())).thenReturn(contents);
        when(contentPermissionProvider.hasPermission(anyLong(), eq("read"), eq(OPERATOR_ID))).thenReturn(true);
        when(accountMapper.selectById(90L)).thenReturn(new MediaAccountDO().setId(90L)
                .setOwnerOperatorUserId(OPERATOR_ID));
        doAnswer(invocation -> {
            ((ContentReviewBatchDO) invocation.getArgument(0)).setId(50L);
            return 1;
        }).when(batchMapper).insert(any(ContentReviewBatchDO.class));

        ContentReviewBatchCreateReqVO request = new ContentReviewBatchCreateReqVO();
        request.setContentVersionIds(versionIds);
        Long batchId = service.create(request, OPERATOR_ID);

        assertEquals(50L, batchId);
        verify(itemMapper, times(20)).insert(any(ContentReviewBatchItemDO.class));
    }

    @Test
    void cancelDraftReleasesBatchWithoutTouchingContentVersions() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DRAFT, STAGE_DRAFT);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        when(batchMapper.cancelDraft(eq(batch), eq(0), any())).thenReturn(1);

        service.cancelDraft(batch.getId(), 0, OPERATOR_ID);

        verify(batchMapper).cancelDraft(eq(batch), eq(0), any());
        verifyNoInteractions(contentVersionMapper, contentService);
    }

    @Test
    void directorCompletionRejectsMissingItemDecision() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, null, null, false)));

        assertServiceCode(CONTENT_REVIEW_DECISION_INCOMPLETE,
                () -> service.validateTaskAction(taskContext("director", OPERATOR_ID)));

        verify(batchMapper, never()).markDirectorCompleted(any(), any());
    }

    @Test
    void directorCompletionAcceptsMixedApprovedAndReturnedItems() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_APPROVED, null, false),
                reviewItem(2L, DECISION_RETURNED, null, false)));
        when(batchMapper.markDirectorCompleted(eq(batch), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.validateTaskAction(taskContext("director", OPERATOR_ID)));

        verify(batchMapper).markDirectorCompleted(eq(batch), any());
    }

    @Test
    void finalCompletionAllowsBatchWhereDirectorReturnedEveryItem() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLockedBatch(batch);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(
                reviewItem(1L, DECISION_RETURNED, null, false),
                reviewItem(2L, DECISION_RETURNED, null, false)));

        assertDoesNotThrow(() -> service.validateTaskAction(taskContext("final", 9L)));

        verifyNoInteractions(reviewMaterialService);
    }

    @Test
    void processResultIgnoresRepeatedEventKey() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL).setLastEventKey("event-1");
        mockLocatedEventBatch(batch);

        service.handleProcessResult(processEvent("event-1"));

        verifyNoInteractions(itemMapper, contentMapper, contentVersionMapper, contentService, materialService);
    }

    @Test
    void nonApprovedProcessResultFailsWhenFrozenVersionCannotBeReleased() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_DIRECTOR_REVIEW, STAGE_DIRECTOR);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, null, null, false).setContentVersionId(201L);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        when(contentVersionMapper.unfreeze(201L)).thenReturn(0);

        BpmProcessInstanceStatusEvent event = processEvent("event-rejected");
        event.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus());
        assertServiceCode(CONTENT_REVIEW_VERSION_CONFLICT,
                () -> service.handleProcessResult(event));

        verify(itemMapper, never()).finalizeItem(any(), anyString(), any(), any());
        verify(batchMapper, never()).finalizeBatch(any(), anyString(), anyString(), any());
    }

    @Test
    void processResultValidatesEveryCollectionSnapshotBeforeWritingContent() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, true)
                .setCollectionSnapshotJson("{}");
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(item));
        when(reviewMaterialService.readAndValidate(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new ServiceException(CONTENT_REVIEW_COLLECTION_INVALID));

        assertServiceCode(CONTENT_REVIEW_COLLECTION_INVALID,
                () -> service.handleProcessResult(processEvent("event-2")));

        verifyNoInteractions(contentMapper, contentVersionMapper, contentService, materialService);
        verify(itemMapper, never()).finalizeItem(any(), anyString(), any(), any());
        verify(batchMapper, never()).finalizeBatch(any(), anyString(), anyString(), any());
    }

    @Test
    void processResultAppliesMixedOutcomesAsOneCompletedBatch() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_FINAL_REVIEW, STAGE_FINAL);
        mockLocatedEventBatch(batch);
        ContentReviewBatchItemDO approved = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, false)
                .setContentId(101L).setContentVersionId(201L).setFinalComment("通过")
                .setFinalReviewedByUserId(9L).setVersion(0);
        ContentReviewBatchItemDO returned = reviewItem(2L, DECISION_RETURNED, null, false)
                .setContentId(102L).setContentVersionId(202L).setDirectorComment("需要修改")
                .setDirectorReviewedByUserId(OPERATOR_ID).setVersion(0);
        when(itemMapper.selectByBatchId(batch.getId())).thenReturn(List.of(approved, returned));

        ContentDO approvedContent = reviewableContent(101L, 90L, 1, 3);
        ContentDO returnedContent = reviewableContent(102L, 90L, 1, 4);
        ContentVersionDO approvedVersion = frozenVersion(201L, 101L);
        ContentVersionDO returnedVersion = frozenVersion(202L, 102L);
        when(contentMapper.selectByIdForUpdate(101L, TENANT_ID)).thenReturn(approvedContent);
        when(contentMapper.selectByIdForUpdate(102L, TENANT_ID)).thenReturn(returnedContent);
        when(contentVersionMapper.selectByIdForUpdate(201L, TENANT_ID)).thenReturn(approvedVersion);
        when(contentVersionMapper.selectByIdForUpdate(202L, TENANT_ID)).thenReturn(returnedVersion);
        when(contentVersionMapper.finishReview(eq(201L), eq("approved"), eq("通过"), eq(9L), any()))
                .thenReturn(1);
        when(contentVersionMapper.finishReview(eq(202L), eq("rejected"), eq("需要修改"),
                eq(OPERATOR_ID), any())).thenReturn(1);
        when(itemMapper.finalizeItem(eq(approved), eq(RESULT_READY_TO_PUBLISH), eq(null), eq(null)))
                .thenReturn(1);
        when(itemMapper.finalizeItem(eq(returned), eq(RESULT_RETURNED), eq(null), eq(null)))
                .thenReturn(1);
        when(batchMapper.finalizeBatch(eq(batch), eq(BATCH_NEED_MODIFY), eq("event-3"), any()))
                .thenReturn(1);

        service.handleProcessResult(processEvent("event-3"));

        verify(contentService).applyBatchReview(approvedContent, 3, true, "通过", 9L);
        verify(contentService).applyBatchReview(returnedContent, 4, false, "需要修改", OPERATOR_ID);
        verify(itemMapper).finalizeItem(approved, RESULT_READY_TO_PUBLISH, null, null);
        verify(itemMapper).finalizeItem(returned, RESULT_RETURNED, null, null);
        verify(batchMapper).finalizeBatch(eq(batch), eq(BATCH_NEED_MODIFY), eq("event-3"), any());
    }

    @Test
    void registerPublishedRejectsStaleContentRecordVersion() {
        ContentReviewBatchDO batch = reviewBatch(BATCH_COMPLETED, STAGE_DONE);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
        ContentReviewBatchItemDO item = reviewItem(1L, DECISION_APPROVED, DECISION_APPROVED, false)
                .setContentId(101L).setContentVersionId(201L).setResultStatus(RESULT_READY_TO_PUBLISH);
        when(itemMapper.selectByIdForUpdate(item.getId(), TENANT_ID)).thenReturn(item);
        ContentDO content = reviewableContent(101L, 90L, 1, 4).setStatus(CONTENT_READY_TO_PUBLISH);
        ContentVersionDO version = frozenVersion(201L, 101L).setReviewDecision("approved");
        when(contentMapper.selectByIdForUpdate(101L, TENANT_ID)).thenReturn(content);
        when(contentVersionMapper.selectByIdForUpdate(201L, TENANT_ID)).thenReturn(version);
        ContentReviewPublishReqVO request = new ContentReviewPublishReqVO();
        request.setPlatformUrl("https://example.com/published/101");
        request.setPublishedAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        request.setExpectedContentVersion(3);

        assertServiceCode(CONTENT_REVIEW_PUBLISH_INVALID,
                () -> service.registerPublished(batch.getId(), item.getId(), request, OPERATOR_ID));

        verify(contentService, never()).registerPublished(any(), any(), anyString(), any(), anyLong());
        verify(itemMapper, never()).markPublished(any(), anyString(), any(), anyLong());
    }

    private void mockLockedBatch(ContentReviewBatchDO batch) {
        when(batchMapper.selectByProcessInstanceId("process-1")).thenReturn(batch);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
    }

    private void mockLocatedEventBatch(ContentReviewBatchDO batch) {
        when(batchMapper.selectByProcessInstanceId("process-1")).thenReturn(batch);
        when(batchMapper.selectByIdForUpdate(batch.getId(), TENANT_ID)).thenReturn(batch);
    }

    private ContentReviewBatchDO reviewBatch(String status, String stage) {
        return new ContentReviewBatchDO().setId(50L).setBatchNo("CRB-1").setAccountId(90L)
                .setOperatorUserId(OPERATOR_ID).setDirectorUserId(OPERATOR_ID)
                .setStatus(status).setCurrentStage(stage).setProcessDefinitionId("definition-1")
                .setProcessDefinitionKey("content-review").setProcessDefinitionVersion(3)
                .setProcessInstanceId("process-1").setBusinessKey("content-review-batch:50")
                .setContextSnapshotJson(JsonUtils.toJsonString(Map.of(
                        "directorTaskKey", "director",
                        "finalTaskKey", "final",
                        "productionMaterialTypeCode", MATERIAL_TYPE_PRODUCTION_CONTENT,
                        "productionMaterialSchemaVersionId", 99L,
                        "productionMaterialSchemaHash", "schema-hash",
                        "materialFieldMapping", Map.of(),
                        "materialDefaultValues", Map.of(),
                        "account", Map.of())))
                .setVersion(0);
    }

    private ContentReviewBatchItemDO reviewItem(Long id, String directorDecision, String finalDecision,
                                                boolean collectMaterial) {
        return new ContentReviewBatchItemDO().setId(id).setBatchId(50L)
                .setDirectorDecision(directorDecision).setFinalDecision(finalDecision)
                .setCollectMaterial(collectMaterial).setVersion(0);
    }

    private BpmTaskActionContext taskContext(String taskKey, Long userId) {
        return new BpmTaskActionContext().setUserId(userId).setAction(ACTION_APPROVE)
                .setTaskId("task-1").setTaskDefinitionKey(taskKey).setProcessInstanceId("process-1")
                .setProcessDefinitionId("definition-1").setProcessDefinitionKey("content-review")
                .setProcessDefinitionVersion(3).setBusinessKey("content-review-batch:50")
                .setTenantId(String.valueOf(TENANT_ID));
    }

    private BpmProcessInstanceStatusEvent processEvent(String eventKey) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent(this);
        event.setId("process-1");
        event.setProcessDefinitionId("definition-1");
        event.setProcessDefinitionKey("content-review");
        event.setProcessDefinitionVersion(3);
        event.setBusinessKey("content-review-batch:50");
        event.setStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        event.setEventKey(eventKey);
        return event;
    }

    private ContentDO reviewableContent(Long id, Long accountId, Integer currentVersionNo, Integer version) {
        return new ContentDO().setId(id).setContentNo("CT-" + id).setAccountId(accountId)
                .setOwnerOperatorUserId(OPERATOR_ID).setStatus(CONTENT_ACCEPTANCE)
                .setCurrentVersionNo(currentVersionNo).setVersion(version);
    }

    private ContentVersionDO completeVersion(Long id, Long contentId) {
        return new ContentVersionDO().setId(id).setContentId(contentId).setVersionNo(1)
                .setTitleSnapshot("标题 " + id).setScriptText("正文 " + id)
                .setCoverSnapshotJson("[{\"fileId\":1}]")
                .setDeliverableSnapshotJson("[{\"fileId\":2}]")
                .setLeadResourceUrl("https://example.com/lead/" + id)
                .setPlannedPublishAt(LocalDateTime.of(2026, 9, 8, 12, 0));
    }

    private ContentVersionDO frozenVersion(Long id, Long contentId) {
        return new ContentVersionDO().setId(id).setContentId(contentId).setVersionNo(1)
                .setFrozenAt(LocalDateTime.of(2026, 9, 8, 9, 0));
    }

    private void assertServiceCode(ErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(expected.getCode(), error.getCode());
    }
}
