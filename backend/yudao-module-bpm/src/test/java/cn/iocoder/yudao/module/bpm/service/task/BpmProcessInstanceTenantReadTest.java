package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BpmProcessInstanceTenantReadTest {
    @Test
    void liveAndHistoricalReadsCannotResolveForeignTenantIds() {
        var config = new StandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:tenant-read-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate("create-drop");
        config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="tenant-read-test">
                      <process id="tenantRead" isExecutable="true">
                        <startEvent id="start"/><sequenceFlow id="flow" sourceRef="start" targetRef="wait"/>
                        <userTask id="wait" name="Wait"/>
                      </process>
                    </definitions>
                    """;
            for (String tenant : Set.of("1", "2")) {
                engine.getRepositoryService().createDeployment().tenantId(tenant)
                        .addString("tenant-read.bpmn20.xml", xml).deploy();
            }
            String own = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("tenantRead", "1").getId();
            String other = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("tenantRead", "2").getId();
            var service = new BpmProcessInstanceServiceImpl();
            ReflectionTestUtils.setField(service, "runtimeService", engine.getRuntimeService());
            ReflectionTestUtils.setField(service, "historyService", engine.getHistoryService());
            var tasks = new BpmTaskServiceImpl();
            ReflectionTestUtils.setField(tasks, "historyService", engine.getHistoryService());
            TenantContextHolder.setTenantId(1L);
            assertNotNull(service.getProcessInstance(own));
            assertNull(service.getProcessInstance(other));
            assertNotNull(service.getHistoricProcessInstance(own));
            assertNull(service.getHistoricProcessInstance(other));
            assertEquals(1, service.getProcessInstances(Set.of(own, other)).size());
            assertEquals(1, service.getHistoricProcessInstances(Set.of(own, other)).size());
            String foreignTask = engine.getTaskService().createTaskQuery().processInstanceId(other).singleResult().getId();
            assertNull(tasks.getHistoricTask(foreignTask));
            assertTrue(tasks.getHistoricTasks(Set.of(foreignTask)).isEmpty());
            assertTrue(tasks.getTaskListByProcessInstanceId(other, true).isEmpty());
            assertTrue(tasks.getActivityListByProcessInstanceId(other).isEmpty());
            assertFalse(tasks.getTaskListByProcessInstanceId(own, true).isEmpty());
            TenantContextHolder.setTenantId(2L);
            assertNull(service.getHistoricProcessInstance(own));
            assertNotNull(service.getHistoricProcessInstance(other));
        } finally {
            TenantContextHolder.clear();
            engine.close();
        }
    }
}
