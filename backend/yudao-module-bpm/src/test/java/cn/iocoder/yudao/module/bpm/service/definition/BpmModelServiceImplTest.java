package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link BpmModelServiceImpl} 的单元测试
 *
 * @author 芋道源码
 */
public class BpmModelServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 1L;
    private static final String MODEL_ID = "model-id";

    @InjectMocks
    private BpmModelServiceImpl modelService;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ModelQuery modelQuery;
    @Mock
    private BpmCategoryService categoryService;

    @Mock
    private BpmFormService bpmFormService;

    @Test
    public void testImportViralAssetCreatesTenantForm() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("script/bpm"))) root = root.getParent();
        assertNotNull(root);
        for (String kind : Arrays.asList("account", "content")) {
            BpmModelSaveReqVO request = JsonUtils.parseObject(Files.readString(root.resolve(
                    "script/bpm/zsjos_viral_" + kind + "_review/1.0.0/process-model.json")), BpmModelSaveReqVO.class);
            request.setManagerUserIds(Collections.singletonList(50L));
            ValidationUtils.validate(request);
            assertNotNull(request.getImportForm());
            request.getImportForm().setId(999L);
            Model model = mock(Model.class);
            when(model.getId()).thenReturn(MODEL_ID);
            when(model.getKey()).thenReturn(request.getKey());
            when(model.getName()).thenReturn(request.getName());
            when(repositoryService.createModelQuery()).thenReturn(modelQuery);
            when(modelQuery.modelTenantId(anyString())).thenReturn(modelQuery);
            when(modelQuery.modelKey(anyString())).thenReturn(modelQuery);
            when(repositoryService.newModel()).thenReturn(model);
            when(categoryService.getCategoryListByCode(anyCollection()))
                    .thenReturn(Collections.singletonList(new BpmCategoryDO()));
            doAnswer(call -> {
                assertNull(((cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.form.BpmFormSaveReqVO)
                        call.getArgument(0)).getId());
                return 77L;
            }).when(bpmFormService).createForm(any());
            assertEquals(MODEL_ID, modelService.importModel(50L, request));
            assertEquals(77L, request.getFormId());
            assertNull(request.getImportForm());
            BpmnModel bpmn = SimpleModelUtils.buildBpmnModel(request.getKey(), request.getName(), request.getSimpleModel());
            assertInstanceOf(UserTask.class, bpmn.getMainProcess().getFlowElement("viralReview"));
            verify(model).setTenantId("1");
        }
    }

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void testExportModel_bpmn() {
        // 准备参数
        Model model = mockModel(BpmModelTypeEnum.BPMN.getType());
        String bpmnXml = "<definitions />";
        // mock 方法（repositoryService）
        mockGetModel(model);
        when(repositoryService.getModelEditorSource(eq(MODEL_ID))).thenReturn(StrUtil.utf8Bytes(bpmnXml));

        // 调用
        BpmModelSaveReqVO result = modelService.exportModel(MODEL_ID);

        // 断言
        assertEquals(model.getKey(), result.getKey());
        assertEquals(model.getName(), result.getName());
        assertEquals(model.getCategory(), result.getCategory());
        assertEquals(BpmModelTypeEnum.BPMN.getType(), result.getType());
        assertEquals(bpmnXml, result.getBpmnXml());
        assertNull(result.getSimpleModel());
    }

    @Test
    public void testExportModel_simple() {
        // 准备参数
        Model model = mockModel(BpmModelTypeEnum.SIMPLE.getType());
        BpmSimpleModelNodeVO simpleModel = new BpmSimpleModelNodeVO();
        simpleModel.setId("start");
        simpleModel.setType(BpmSimpleModelNodeTypeEnum.START_NODE.getType());
        // mock 方法（repositoryService）
        mockGetModel(model);
        when(repositoryService.getModelEditorSourceExtra(eq(MODEL_ID)))
                .thenReturn(JsonUtils.toJsonByte(simpleModel));

        // 调用
        BpmModelSaveReqVO result = modelService.exportModel(MODEL_ID);

        // 断言
        assertEquals(BpmModelTypeEnum.SIMPLE.getType(), result.getType());
        assertNotNull(result.getSimpleModel());
        assertEquals(simpleModel.getId(), result.getSimpleModel().getId());
        assertEquals(simpleModel.getType(), result.getSimpleModel().getType());
        assertNull(result.getBpmnXml());
    }

    @Test
    public void testExportModel_notExists() {
        // mock 方法（repositoryService）
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelId(eq(MODEL_ID))).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID.toString()))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(null);

        // 调用，并断言异常
        assertServiceException(() -> modelService.exportModel(MODEL_ID), MODEL_NOT_EXISTS);
    }

    @Test
    public void testImportModel_bpmn() {
        // 准备参数
        BpmModelSaveReqVO reqVO = new BpmModelSaveReqVO();
        reqVO.setKey("test_process");
        reqVO.setName("测试流程");
        reqVO.setCategory("OA");
        reqVO.setType(BpmModelTypeEnum.BPMN.getType());
        reqVO.setBpmnXml("<definitions />");
        reqVO.setStartUserIds(Arrays.asList(10L, 20L));
        reqVO.setStartDeptIds(Collections.singletonList(30L));
        reqVO.setManagerUserIds(Collections.singletonList(40L));
        Model model = mock(Model.class);
        when(model.getId()).thenReturn(MODEL_ID);
        // mock 方法（repositoryService）
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID.toString()))).thenReturn(modelQuery);
        when(modelQuery.modelKey(eq(reqVO.getKey()))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(null);
        when(repositoryService.newModel()).thenReturn(model);
        when(categoryService.getCategoryListByCode(anyCollection()))
                .thenReturn(Collections.singletonList(new BpmCategoryDO()));

        // 调用
        String result = modelService.importModel(50L, reqVO);

        // 断言
        assertEquals(MODEL_ID, result);
        verify(model).setTenantId(TENANT_ID.toString());
        verify(repositoryService).saveModel(same(model));
        verify(repositoryService).addModelEditorSource(eq(MODEL_ID),
                aryEq(StrUtil.utf8Bytes(reqVO.getBpmnXml())));
        ArgumentCaptor<String> metaInfoCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).setMetaInfo(metaInfoCaptor.capture());
        BpmModelMetaInfoVO metaInfo = JsonUtils.parseObject(metaInfoCaptor.getValue(), BpmModelMetaInfoVO.class);
        assertNotNull(metaInfo);
        assertEquals(Arrays.asList(10L, 20L), metaInfo.getStartUserIds());
        assertEquals(Collections.singletonList(30L), metaInfo.getStartDeptIds());
        assertEquals(Collections.singletonList(50L), metaInfo.getManagerUserIds());
    }

    @Test
    public void testEamAssetTransferSimpleAsset() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path asset = null;
        while (root != null) {
            Path candidate = root.resolve("script/bpm/eam_asset_transfer/1.0.0/process-model.json");
            if (Files.isRegularFile(candidate)) {
                asset = candidate;
                break;
            }
            root = root.getParent();
        }
        assertNotNull(asset);
        BpmModelSaveReqVO reqVO = JsonUtils.parseObject(Files.readString(asset), BpmModelSaveReqVO.class);
        reqVO.setManagerUserIds(Collections.singletonList(50L));
        ValidationUtils.validate(reqVO);

        BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(
                reqVO.getKey(), reqVO.getName(), reqVO.getSimpleModel());
        for (String taskKey : Arrays.asList("departmentLeaderReview", "sourceDepartmentReview",
                "targetDepartmentReview", "assetAdministratorReview", "receiverSign")) {
            assertInstanceOf(UserTask.class, bpmnModel.getMainProcess().getFlowElement(taskKey));
        }
        assertEquals("${isAllocate == true}", ((UserTask) bpmnModel.getMainProcess()
                .getFlowElement("departmentLeaderReview")).getSkipExpression());
        assertEquals("${isAllocate != true}", ((UserTask) bpmnModel.getMainProcess()
                .getFlowElement("sourceDepartmentReview")).getSkipExpression());
        assertEquals("${isAllocate != true || sameDepartment == true}", ((UserTask) bpmnModel.getMainProcess()
                .getFlowElement("targetDepartmentReview")).getSkipExpression());
    }

    @Test
    public void testMigratedSimpleAssetsPreserveTaskKeysAndGatewaySemantics() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("script/bpm/manifest.json"))) root = root.getParent();
        assertNotNull(root);
        String[] keys = {"zsjos_feedback_requirement_approval", "zsjos_lead_appeal_review",
                "zsjos_lead_transfer_request", "zsjos_media_over_entitlement", "zsjos_media_positioning_ip",
                "zsjos_media_rebind", "zsjos_media_reposition", "zsjos_partner_withdrawal",
                "zsjos_sales_order_dual_approval", "zsjos_student_contact_extension"};
        for (String key : keys) {
            Path asset = root.resolve("script/bpm").resolve(key).resolve("2.0.0/process-model.json");
            BpmModelSaveReqVO req = JsonUtils.parseObject(Files.readString(asset), BpmModelSaveReqVO.class);
            assertNotNull(req.getSimpleModel(), key);
            BpmnModel bpmn = SimpleModelUtils.buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel());
            // Deployment serializes and reparses XML; this also resolves incoming/outgoing flow references.
            bpmn = cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.getBpmnModel(
                    cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.getBpmnXml(bpmn)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BpmnModel original = cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.getBpmnModel(
                    Files.readAllBytes(root.resolve("script/bpm").resolve(key).resolve("1.0.0/process.bpmn20.xml")));
            java.util.Set<String> expected = original.getMainProcess().getFlowElements().stream()
                    .filter(UserTask.class::isInstance).map(e -> e.getId()).collect(java.util.stream.Collectors.toSet());
            java.util.Set<String> actual = bpmn.getMainProcess().getFlowElements().stream()
                    .filter(UserTask.class::isInstance).map(e -> e.getId()).filter(id -> !id.equals("StartUserNode"))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, actual, key);
            for (String taskId : expected) {
                UserTask task = (UserTask) bpmn.getMainProcess().getFlowElement(taskId);
                assertEquals(35, cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.parseCandidateStrategy(task));
            }
            assertNotNull(bpmn.getMainProcess().getFlowElement("EndEvent"), key);
            assertTrue(bpmn.getMainProcess().getFlowElements().stream().anyMatch(UserTask.class::isInstance), key);
            if (key.equals("zsjos_feedback_requirement_approval")) {
                assertTrue(bpmn.getMainProcess().getFlowElements().stream().anyMatch(ExclusiveGateway.class::isInstance));
                ExclusiveGateway gateway = (ExclusiveGateway) bpmn.getMainProcess().getFlowElement("LeaderGateway");
                assertEquals("flow_skip_leader", gateway.getDefaultFlow());
                org.flowable.bpmn.model.SequenceFlow condition = (org.flowable.bpmn.model.SequenceFlow)
                        bpmn.getMainProcess().getFlowElement("flow_department_leader");
                assertEquals("${hasDepartmentLeader == true}", condition.getConditionExpression());
                assertEquals("departmentLeaderReview", condition.getTargetRef());
                org.flowable.bpmn.model.SequenceFlow fallback = (org.flowable.bpmn.model.SequenceFlow)
                        bpmn.getMainProcess().getFlowElement("flow_skip_leader");
                assertEquals("chairmanReview", fallback.getTargetRef());
            }
            if (key.equals("zsjos_sales_order_dual_approval")) {
                assertEquals(2, bpmn.getMainProcess().getFlowElements().stream().filter(InclusiveGateway.class::isInstance).count());
                InclusiveGateway split = (InclusiveGateway) bpmn.getMainProcess().getFlowElement("ParallelSplit");
                assertEquals(2, split.getOutgoingFlows().size());
                split.getOutgoingFlows().forEach(flow -> assertEquals("${true}", flow.getConditionExpression()));
                InclusiveGateway join = (InclusiveGateway) bpmn.getMainProcess().getFlowElement("ParallelSplit_join");
                assertEquals(2, join.getIncomingFlows().size());
            }
        }
    }

    private Model mockModel(Integer type) {
        Model model = mock(Model.class);
        when(model.getKey()).thenReturn("test_process");
        when(model.getName()).thenReturn("测试流程");
        when(model.getCategory()).thenReturn("OA");
        when(model.getCreateTime()).thenReturn(new Date(1_000L));
        BpmModelMetaInfoVO metaInfo = new BpmModelMetaInfoVO();
        metaInfo.setType(type);
        when(model.getMetaInfo()).thenReturn(JsonUtils.toJsonString(metaInfo));
        return model;
    }

    private void mockGetModel(Model model) {
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelId(eq(MODEL_ID))).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID.toString()))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(model);
    }

}
