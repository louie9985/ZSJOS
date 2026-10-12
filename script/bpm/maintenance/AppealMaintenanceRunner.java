package maintenance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.task.BpmStartUserTaskRecovery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** No normal application entry point: requires OS attach authorization and a named, permitted operator. */
public final class AppealMaintenanceRunner {
    private static final String KEY = "zsjos_lead_appeal_review";
    private static final String LEAD = "KZ202610051234290004";
    private static final String MODEL = "90f8c2f1-a79a-11f1-b2c2-8a4c52521e0c";
    private static final String INSTANCE = "87ac1aa0-c1f4-11f1-a3d5-8e534d5dae38";
    private static final class RehearsalRollback extends RuntimeException {}

    public static void run(Object existingContext, Properties props) throws Exception {
        var context = (ApplicationContext) existingContext;
        var oldSecurity = SecurityContextHolder.getContext();
        Long oldTenant = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(1L);
            var engine = context.getBean(ProcessEngine.class);
            var config = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
            var sql = new JdbcTemplate(config.getDataSource());
            require("ruoyi-vue-pro".equals(sql.queryForObject("SELECT DATABASE()", String.class)), "Wrong database");
            Path directory = Path.of(props.getProperty("directory"));
            Path result = Path.of(props.getProperty("result"));
            String mode = props.getProperty("mode");
            if ("inspect-context".equals(mode)) {
                Files.writeString(result, "EXISTING_APPLICATION_CONTEXT_OK\nDATABASE_VERIFIED\n");
                return;
            }
            var user = context.getBean(AdminUserService.class).getUserByUsername(props.getProperty("operator"));
            require(user != null && Objects.equals(user.getStatus(), 0) && Objects.equals(user.getTenantId(), 1L),
                    "Operator unavailable");
            long actor = user.getId();
            var permissions = context.getBean(PermissionApi.class);
            for (String permission : List.of("bpm:model:update", "bpm:model:deploy", "bpm:task:update")) {
                require(permissions.hasAnyPermissions(actor, permission), "Operator permission missing");
            }
            var login = new LoginUser();
            login.setId(actor); login.setUserType(2); login.setTenantId(1L);
            login.setInfo(Map.of(LoginUser.INFO_KEY_NICKNAME, user.getNickname()));
            var security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
            SecurityContextHolder.setContext(security);
            var models = context.getBean(BpmModelService.class);
            require(models.exportModel(MODEL).getManagerUserIds().contains(actor), "Operator is not the model manager");
            Map<?, ?> report = JsonUtils.parseObject(Files.readString(directory.resolve("target-recovery-preflight.json")), Map.class);
            require(LEAD.equals(report.get("leadNo")) && INSTANCE.equals(report.get("instanceId"))
                    && "1".equals(report.get("tenantId")), "Target mismatch");
            var reviewers = ((List<?>) report.get("reviewers")).stream().map(id -> ((Number) id).longValue()).toList();
            var request = new BpmStartUserTaskRecovery.Request("1", INSTANCE, (String) report.get("definitionId"),
                    (String) report.get("businessKey"), (String) report.get("startTaskId"), "appealReview", "coll_userList",
                    (String) report.get("definitionXmlSha256"), reviewers,
                    "zsjos:lead:appeal:review-sales-manager", actor,
                    "Authorized repair of missing reviewer input for " + LEAD);
            var recovery = new BpmStartUserTaskRecovery(engine, context.getBean(AdminUserApi.class), permissions,
                    config.getTransactionManager());
            var tx = new TransactionTemplate(config.getTransactionManager());
            Runnable validate = () -> lockBusiness(sql, ((Number) report.get("appealId")).longValue(), reviewers);
            if ("verify-inbox".equals(mode)) {
                // Read the same application service as the appeals page, under the frozen reviewer identity.
                ClassLoader loader = context.getClassLoader();
                Class<?> serviceType = Class.forName("cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealService", true, loader);
                Class<?> requestType = Class.forName("cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealPageReqVO", true, loader);
                Object pageRequest = requestType.getConstructor().newInstance();
                requestType.getMethod("setKeyword", String.class).invoke(pageRequest, LEAD);
                requestType.getMethod("setHandled", Boolean.class).invoke(pageRequest, false);
                requestType.getMethod("setPageNo", Integer.class).invoke(pageRequest, 1);
                requestType.getMethod("setPageSize", Integer.class).invoke(pageRequest, 20);
                for (Long reviewer : reviewers) {
                    require(permissions.hasAnyPermissions(reviewer, "zsjos:lead:appeal:query"), "Reviewer inbox permission missing");
                    var reviewerLogin = new LoginUser();
                    reviewerLogin.setId(reviewer); reviewerLogin.setTenantId(1L); reviewerLogin.setUserType(2);
                    var reviewerSecurity = SecurityContextHolder.createEmptyContext();
                    reviewerSecurity.setAuthentication(new UsernamePasswordAuthenticationToken(reviewerLogin, null, List.of()));
                    SecurityContextHolder.setContext(reviewerSecurity);
                    Object page = serviceType.getMethod("getInboxPage", requestType, Long.class)
                            .invoke(context.getBean(serviceType), pageRequest, reviewer);
                    var items = (List<?>) page.getClass().getMethod("getList").invoke(page);
                    require(items.size() == 1, "Expected exactly one matching reviewer inbox row");
                    Object row = items.getFirst();
                    require(LEAD.equals(row.getClass().getMethod("getLeadNo").invoke(row))
                            && row.getClass().getMethod("getTaskId").invoke(row) != null,
                            "Inbox row or task correlation invalid");
                }
                Files.writeString(result, "REVIEWER_INBOX_VERIFIED\nLEAD_AND_ACTIONABLE_TASK_PRESENT\n");
            } else if ("preflight".equals(mode)) {
                tx.executeWithoutResult(status -> validate.run());
                compareModel(models, directory);
                Files.writeString(result, recovery.inspect(request) + "\nOPERATOR_AND_MODEL_VERIFIED\n");
            } else if ("publish".equals(mode)) {
                var update = JsonUtils.parseObject(Files.readString(directory.resolve("model-update.json")), BpmModelSaveReqVO.class);
                require(MODEL.equals(update.getId()), "Update model mismatch");
                String newId = FlowableUtils.executeAuthenticatedUserId(actor, () -> tx.execute(status -> {
                    sql.queryForObject("SELECT ID_ FROM ACT_RE_MODEL WHERE ID_=? AND TENANT_ID_='1' FOR UPDATE", String.class, MODEL);
                    compareModel(models, directory);
                    models.updateModel(actor, update);
                    models.deployModel(actor, MODEL);
                    var definition = engine.getRepositoryService().createProcessDefinitionQuery()
                            .processDefinitionKey(KEY).processDefinitionTenantId("1").latestVersion().singleResult();
                    require(definition != null && !definition.isSuspended(), "New definition missing");
                    var review = (UserTask) BpmnModelUtils.getFlowElementById(engine.getRepositoryService()
                            .getBpmnModel(definition.getId()), "appealReview");
                    require(Objects.equals(35, BpmnModelUtils.parseCandidateStrategy(review)), "Wrong deployed strategy");
                    require(BpmnModelUtils.parseCandidateParam(review) == null
                            || BpmnModelUtils.parseCandidateParam(review).isBlank(), "Unexpected candidate parameter");
                    return definition.getId();
                }));
                Files.writeString(result, "PUBLISHED\n" + newId + "\n");
            } else if ("rehearse".equals(mode)) {
                AtomicBoolean reachedCommit = new AtomicBoolean();
                try {
                    recovery.recover(request, () -> {
                        validate.run();
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void beforeCommit(boolean readOnly) {
                                reachedCommit.set(true);
                                throw new RehearsalRollback();
                            }
                        });
                    });
                    throw new IllegalStateException("Rehearsal failed to roll back");
                } catch (RuntimeException expected) {
                    Throwable cause = expected;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (!(cause instanceof RehearsalRollback)) throw expected;
                    require(reachedCommit.get() && "READY".equals(recovery.inspect(request)), "Rehearsal state changed");
                    Files.writeString(result, "REHEARSED_AND_ROLLED_BACK\nEXACT_REVIEWER_TASKS_VERIFIED\n");
                }
            } else if ("recover".equals(mode)) {
                require(Files.readString(directory.resolve("rehearse-result.txt")).startsWith("REHEARSED_AND_ROLLED_BACK"),
                        "Successful target-engine rehearsal required");
                require(Files.readString(directory.resolve("publish-result.txt")).startsWith("PUBLISHED"),
                        "Model publication required");
                String outcome = recovery.recover(request, validate);
                Files.writeString(result, outcome + "\n" + recovery.inspect(request) + "\n");
            } else throw new IllegalArgumentException("Unknown maintenance mode");
        } finally {
            TenantContextHolder.setTenantId(oldTenant);
            SecurityContextHolder.setContext(oldSecurity);
        }
    }

    private static void compareModel(BpmModelService models, Path directory) {
        try {
            var expected = JsonUtils.parseObject(Files.readString(directory.resolve("model-before.json")), BpmModelSaveReqVO.class);
            expected.setId(null); expected.setBpmnXml(null);
            var actual = models.exportModel(MODEL);
            require(JsonUtils.parseObject(JsonUtils.toJsonString(expected), Map.class)
                    .equals(JsonUtils.parseObject(JsonUtils.toJsonString(actual), Map.class)), "Model changed since review");
        } catch (java.io.IOException error) { throw new IllegalStateException("Reviewed export unavailable"); }
    }

    private static void lockBusiness(JdbcTemplate sql, long appealId, List<Long> reviewers) {
        require(TransactionSynchronizationManager.isActualTransactionActive(), "Business lock requires engine transaction");
        var lead = sql.queryForMap("SELECT id,status,assignment_status,owner_user_id,closed_at FROM zsjos_lead "
                + "WHERE tenant_id=1 AND lead_no=? AND deleted=0 FOR UPDATE", LEAD);
        require("invalid".equals(lead.get("status")) && "owned".equals(lead.get("assignment_status"))
                && lead.get("closed_at") == null, "Lead lifecycle changed");
        var appeal = sql.queryForMap("SELECT status,round_no,review_stage,process_instance_id,reviewer_user_ids_snapshot,"
                + "owner_user_id_snapshot,decided_at FROM zsjos_lead_appeal WHERE tenant_id=1 AND id=? AND lead_id=? AND deleted=0 FOR UPDATE",
                appealId, lead.get("id"));
        require("sales_manager_reviewing".equals(appeal.get("status")) && ((Number) appeal.get("round_no")).intValue() == 1
                && "sales_manager".equals(appeal.get("review_stage")) && INSTANCE.equals(appeal.get("process_instance_id"))
                && Objects.equals(lead.get("owner_user_id"), appeal.get("owner_user_id_snapshot"))
                && appeal.get("decided_at") == null, "Appeal state or owner changed");
        require(reviewers.equals(JsonUtils.parseArray((String) appeal.get("reviewer_user_ids_snapshot"), Long.class)),
                "Frozen reviewers changed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
