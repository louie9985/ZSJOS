package maintenance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** One-shot test-environment model correction; no instance start, recovery, or direct engine-table writes. */
public final class TestAppealModelMaintenanceRunner {
    private static final String KEY = "zsjos_lead_appeal_review";
    private static final String MODEL = "90f8c2f1-a79a-11f1-b2c2-8a4c52521e0c";
    private static final String OLD_DEFINITION = "zsjos_lead_appeal_review:2:e44ed817-b05f-11f1-a954-8a4c52521e0c";

    public static void run(Object existingContext, Properties props) throws Exception {
        String environment = System.getenv("ZSJOS_AGENT_ENV");
        if (environment == null || environment.isBlank()) {
            environment = Files.readString(Path.of("/etc/zsjos/agent-environment")).trim();
        }
        require("test".equals(environment), "Wrong environment");
        var context = (ApplicationContext) existingContext;
        var oldSecurity = SecurityContextHolder.getContext();
        Long oldTenant = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(1L);
            var engine = context.getBean(ProcessEngine.class);
            var config = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
            var sql = new JdbcTemplate(config.getDataSource());
            require("zsjos".equals(sql.queryForObject("SELECT DATABASE()", String.class)), "Wrong database");
            var models = context.getBean(BpmModelService.class);
            var definitions = context.getBean(BpmDefinitionReadApi.class);
            Path directory = Path.of(props.getProperty("directory"));
            Path result = Path.of(props.getProperty("result"));
            String mode = props.getProperty("mode");
            var current = models.exportModel(MODEL);
            require(KEY.equals(current.getKey()) && Objects.equals(20, current.getType()), "Wrong model");

            if ("export".equals(mode)) {
                requireOriginal(definitions);
                Files.writeString(directory.resolve("model-before.json"), JsonUtils.toJsonString(current),
                        StandardOpenOption.CREATE_NEW);
                Files.write(directory.resolve("published-before.bpmn"),
                        engine.getRepositoryService().getProcessModel(OLD_DEFINITION).readAllBytes(),
                        StandardOpenOption.CREATE_NEW);
                Files.writeString(result, "EXPORTED_EXISTING_MODEL\nORIGINAL_CONTRACT_REJECTED\n");
                return;
            }

            // The user must name the operational actor; membership and permissions are still checked by System/BPM.
            var user = context.getBean(AdminUserService.class).getUserByUsername(props.getProperty("operator"));
            require(user != null && Objects.equals(user.getStatus(), 0) && Objects.equals(user.getTenantId(), 1L),
                    "Operator unavailable");
            long actor = user.getId();
            var permissions = context.getBean(PermissionApi.class);
            for (String permission : List.of("bpm:model:export", "bpm:model:update", "bpm:model:deploy")) {
                require(permissions.hasAnyPermissions(actor, permission), "Operator permission missing");
            }
            require(current.getManagerUserIds() != null && current.getManagerUserIds().contains(actor),
                    "Operator is not the model manager");
            var login = new LoginUser();
            login.setId(actor); login.setUserType(2); login.setTenantId(1L);
            login.setInfo(Map.of(LoginUser.INFO_KEY_NICKNAME, user.getNickname()));
            var security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
            SecurityContextHolder.setContext(security);

            var before = JsonUtils.parseObject(Files.readString(directory.resolve("model-before.json")), BpmModelSaveReqVO.class);
            var update = JsonUtils.parseObject(Files.readString(directory.resolve("model-update.json")), BpmModelSaveReqVO.class);
            requireExactCorrection(before, update);
            if ("verify".equals(mode)) {
                requireEquivalent(current, update);
                String id = requireCorrected(definitions);
                Files.writeString(result, "COMMITTED_MODEL_AND_PUBLIC_API_VERIFIED\n" + id + "\n");
                return;
            }
            require("preflight".equals(mode) || "publish".equals(mode), "Unsupported mode");
            requireEquivalent(current, before);
            requireOriginal(definitions);
            if ("preflight".equals(mode)) {
                Files.writeString(result, "OPERATOR_AND_MODEL_VERIFIED\nEXACT_TWO_FIELD_CORRECTION\n");
                return;
            }
            var tx = new TransactionTemplate(config.getTransactionManager());
            String newId = FlowableUtils.executeAuthenticatedUserId(actor, () -> tx.execute(status -> {
                // Serialize this model's update and recheck its full export before normal model service writes.
                sql.queryForObject("SELECT ID_ FROM ACT_RE_MODEL WHERE ID_=? AND TENANT_ID_='1' FOR UPDATE",
                        String.class, MODEL);
                requireEquivalent(models.exportModel(MODEL), before);
                requireOriginal(definitions);
                models.updateModel(actor, update);
                models.deployModel(actor, MODEL);
                requireEquivalent(models.exportModel(MODEL), update);
                return requireCorrected(definitions);
            }));
            Files.writeString(directory.resolve("model-after.json"), JsonUtils.toJsonString(models.exportModel(MODEL)),
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(result, "PUBLISHED\n" + newId + "\nOPERATOR_ID=" + actor + "\n");
        } finally {
            SecurityContextHolder.setContext(oldSecurity);
            if (oldTenant == null) TenantContextHolder.clear(); else TenantContextHolder.setTenantId(oldTenant);
        }
    }

    private static void requireExactCorrection(BpmModelSaveReqVO before, BpmModelSaveReqVO update) {
        var expected = JsonUtils.parseObject(JsonUtils.toJsonString(before), BpmModelSaveReqVO.class);
        var start = expected.getSimpleModel();
        require(start != null && "StartUserNode".equals(start.getId()) && Objects.equals(10, start.getType()),
                "Unexpected starter");
        var review = start.getChildNode();
        require(review != null && "appealReview".equals(review.getId()) && Objects.equals(11, review.getType())
                && Objects.equals(60, review.getCandidateStrategy()) && "${coll_userList}".equals(review.getCandidateParam()),
                "Unexpected reviewer configuration");
        var end = review.getChildNode();
        require(end != null && Objects.equals(1, end.getType()) && end.getChildNode() == null, "Unexpected successor");
        review.setCandidateStrategy(35);
        review.setCandidateParam(null);
        expected.setId(MODEL);
        expected.setBpmnXml(null);
        requireEquivalent(expected, update);
    }

    private static void requireEquivalent(BpmModelSaveReqVO left, BpmModelSaveReqVO right) {
        // DTO equality does not reliably include inherited metadata; compare normalized serialized trees.
        require((left.getId() == null || MODEL.equals(left.getId()))
                && (right.getId() == null || MODEL.equals(right.getId())), "Unexpected model identity");
        var leftTree = JsonUtils.parseObject(JsonUtils.toJsonString(left), Map.class);
        var rightTree = JsonUtils.parseObject(JsonUtils.toJsonString(right), Map.class);
        // The normal export omits the model ID; update commands must supply the exact existing ID.
        leftTree.remove("id");
        rightTree.remove("id");
        require(leftTree.equals(rightTree), "Model changed beyond reviewed scope");
    }

    private static void requireOriginal(BpmDefinitionReadApi definitions) {
        var definition = definitions.getPublishedProcessDefinition(KEY);
        require(definition != null && OLD_DEFINITION.equals(definition.getId())
                && Boolean.FALSE.equals(definition.getSuspended()), "Published definition changed");
        var reviews = definition.getUserTasks().stream().filter(t -> "appealReview".equals(t.getKey())).toList();
        require(reviews.size() == 1 && Objects.equals(60, reviews.getFirst().getCandidateStrategy())
                && !Boolean.TRUE.equals(reviews.getFirst().getStartUserSelectAssignees())
                && "${coll_userList}".equals(reviews.getFirst().getCandidateParam()), "Original contract changed");
    }

    private static String requireCorrected(BpmDefinitionReadApi definitions) {
        var definition = definitions.getPublishedProcessDefinition(KEY);
        require(definition != null && definition.getId() != null && !OLD_DEFINITION.equals(definition.getId())
                && KEY.equals(definition.getKey()) && Boolean.FALSE.equals(definition.getSuspended())
                && Objects.equals(3, definition.getVersion()), "New definition missing");
        var reviews = definition.getUserTasks().stream().filter(t -> "appealReview".equals(t.getKey())).toList();
        require(reviews.size() == 1 && Objects.equals(35, reviews.getFirst().getCandidateStrategy())
                && Boolean.TRUE.equals(reviews.getFirst().getStartUserSelectAssignees())
                && (reviews.getFirst().getCandidateParam() == null || reviews.getFirst().getCandidateParam().isBlank()),
                "New reviewer contract invalid");
        return definition.getId();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
