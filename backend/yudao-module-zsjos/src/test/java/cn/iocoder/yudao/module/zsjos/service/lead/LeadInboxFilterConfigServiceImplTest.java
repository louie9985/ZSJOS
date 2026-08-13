package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterSchemeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_INBOX_FILTER_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.AGING_POOL_ASSIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadInboxFilterConfigServiceImplTest {

    @InjectMocks
    private LeadInboxFilterConfigServiceImpl service;
    @Mock
    private LeadInboxFilterSchemeMapper schemeMapper;
    @Mock
    private LeadInboxFilterVersionMapper versionMapper;

    @Test
    void saveDraftRejectsUnknownConditionValue() {
        LeadInboxFilterSaveReqVO reqVO = validRequest();
        reqVO.getGroups().get(1).getConditions().getFirst().setValues(List.of("invented"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(reqVO));

        assertEquals(LEAD_INBOX_FILTER_INVALID.getCode(), error.getCode());
    }

    @Test
    void saveDraftRejectsConditionFieldFromAnotherAudience() {
        LeadInboxFilterSaveReqVO reqVO = validRequest();
        reqVO.getGroups().get(1).setConditions(List.of(condition("handled", "todo")));

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(reqVO));

        assertEquals(LEAD_INBOX_FILTER_INVALID.getCode(), error.getCode());
    }

    @Test
    void saveDraftRequiresAllGroupForLeadAudience() {
        LeadInboxFilterSaveReqVO reqVO = validRequest();
        reqVO.setGroups(List.of(reqVO.getGroups().get(1)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.saveDraft(reqVO));

        assertEquals(LEAD_INBOX_FILTER_INVALID.getCode(), error.getCode());
    }

    @Test
    void saveDraftAcceptsReviewerWithoutAllGroupAndNormalizesLegacyOptionKeys() {
        LeadInboxFilterSaveReqVO reqVO = reviewerRequest();
        LeadInboxFilterSchemeDO scheme = scheme(reqVO);
        scheme.setAudience("reviewer");
        when(schemeMapper.selectByAudience("reviewer")).thenReturn(scheme);

        service.saveDraft(reqVO);

        ArgumentCaptor<LeadInboxFilterSchemeDO> updateCaptor = ArgumentCaptor.forClass(LeadInboxFilterSchemeDO.class);
        verify(schemeMapper).updateById(updateCaptor.capture());
        LeadInboxFilterConfigVO saved = JsonUtils.parseObject(updateCaptor.getValue().getDraftConfigJson(),
                LeadInboxFilterConfigVO.class);
        assertEquals("registration_review", saved.getGroups().getFirst().getOptions().get(1).getKey());
        assertEquals("registrationReview",
                saved.getGroups().getFirst().getOptions().get(1).getConditions().getFirst().getValues().getFirst());
    }

    @Test
    void getAdminConfigNormalizesLegacyReviewerOptionKeys() {
        LeadInboxFilterSaveReqVO config = reviewerRequest();
        LeadInboxFilterSchemeDO scheme = scheme(config);
        scheme.setAudience("reviewer");
        when(schemeMapper.selectByAudience("reviewer")).thenReturn(scheme);

        var result = service.getAdminConfig("reviewer");

        assertEquals("registration_review", result.getDraftGroups().getFirst().getOptions().get(1).getKey());
        assertEquals("registrationReview", result.getDraftGroups().getFirst().getOptions().get(1)
                .getConditions().getFirst().getValues().getFirst());
    }

    @Test
    void getAdminConfigNormalizesLegacyConvertedLeadStatus() {
        LeadInboxFilterSaveReqVO config = validRequest();
        LeadInboxFilterConfigVO.GroupVO convertedGroup = group("valid", "有效客资", 20);
        convertedGroup.setConditions(List.of(condition("status", "converted")));
        LeadInboxFilterConfigVO.OptionVO converted = option("converted", "已进入转化", 10);
        converted.setConditions(List.of(condition("status", "converted")));
        convertedGroup.setOptions(List.of(option("all", "全部", 0), converted));
        config.setGroups(List.of(config.getGroups().getFirst(), convertedGroup));
        LeadInboxFilterSchemeDO scheme = scheme(config);
        when(schemeMapper.selectByAudience("submitter")).thenReturn(scheme);

        var result = service.getAdminConfig("submitter");

        LeadInboxFilterConfigVO.GroupVO normalized = result.getDraftGroups().get(1);
        assertEquals(List.of("won"), normalized.getConditions().getFirst().getValues());
        assertEquals("won", normalized.getOptions().get(1).getKey());
        assertEquals("已成交", normalized.getOptions().get(1).getLabel());
        assertEquals(List.of("won"), normalized.getOptions().get(1).getConditions().getFirst().getValues());
    }

    @Test
    void getAdminConfigKeepsCustomConvertedOptionWithoutLegacyStatusCondition() {
        LeadInboxFilterSaveReqVO config = validRequest();
        LeadInboxFilterConfigVO.OptionVO custom = option("converted", "自定义选项", 20);
        custom.setConditions(List.of(condition("assignment_status", "owned")));
        config.getGroups().get(1).setOptions(new java.util.ArrayList<>(config.getGroups().get(1).getOptions()));
        config.getGroups().get(1).getOptions().add(custom);
        LeadInboxFilterSchemeDO scheme = scheme(config);
        when(schemeMapper.selectByAudience("submitter")).thenReturn(scheme);

        var result = service.getAdminConfig("submitter");

        assertEquals("converted", result.getDraftGroups().get(1).getOptions().get(2).getKey());
        assertEquals("自定义选项", result.getDraftGroups().get(1).getOptions().get(2).getLabel());
    }

    @Test
    void rollbackNormalizesHistoricalConvertedLeadStatus() {
        LeadInboxFilterSaveReqVO historical = validRequest();
        historical.getGroups().get(1).getConditions().getFirst().setValues(List.of("submitted", "converted"));
        LeadInboxFilterSchemeDO scheme = scheme(validRequest());
        scheme.setPublishedVersion(4);
        LeadInboxFilterVersionDO history = new LeadInboxFilterVersionDO();
        history.setConfigJson(JsonUtils.toJsonString(historical));
        when(schemeMapper.selectByAudience("submitter")).thenReturn(scheme);
        when(versionMapper.selectBySchemeIdAndVersion(1L, 2)).thenReturn(history);
        when(schemeMapper.updatePublished(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.rollback("submitter", 2, 9L);

        ArgumentCaptor<LeadInboxFilterVersionDO> versionCaptor = ArgumentCaptor.forClass(LeadInboxFilterVersionDO.class);
        verify(versionMapper).insert(versionCaptor.capture());
        LeadInboxFilterConfigVO normalized = JsonUtils.parseObject(versionCaptor.getValue().getConfigJson(),
                LeadInboxFilterConfigVO.class);
        assertEquals(List.of("submitted", "won"), normalized.getGroups().get(1)
                .getConditions().getFirst().getValues());
    }

    @Test
    void publishCreatesImmutableVersionSnapshot() {
        LeadInboxFilterSaveReqVO config = validRequest();
        LeadInboxFilterSchemeDO scheme = scheme(config);
        scheme.setPublishedVersion(2);
        when(schemeMapper.selectByAudience("submitter")).thenReturn(scheme);
        when(schemeMapper.updatePublished(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        Integer result = service.publish("submitter", 9L);

        assertEquals(3, result);
        ArgumentCaptor<LeadInboxFilterVersionDO> versionCaptor = ArgumentCaptor.forClass(LeadInboxFilterVersionDO.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(3, versionCaptor.getValue().getVersionNo());
        assertEquals(9L, versionCaptor.getValue().getPublishedBy());
    }

    @Test
    void rollbackPublishesHistoricalConfigAsNewVersion() {
        LeadInboxFilterSaveReqVO config = validRequest();
        LeadInboxFilterSchemeDO scheme = scheme(config);
        scheme.setPublishedVersion(4);
        LeadInboxFilterVersionDO history = new LeadInboxFilterVersionDO();
        history.setConfigJson(JsonUtils.toJsonString(config));
        when(schemeMapper.selectByAudience("submitter")).thenReturn(scheme);
        when(versionMapper.selectBySchemeIdAndVersion(1L, 2)).thenReturn(history);
        when(schemeMapper.updatePublished(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        Integer result = service.rollback("submitter", 2, 9L);

        assertEquals(5, result);
    }

    @Test
    void resolveQueryCombinesGroupAndOptionConditions() {
        LeadInboxFilterSaveReqVO config = validRequest();

        LeadInboxFilterQuery query = service.resolveQuery(config, "pending", "owned");

        assertEquals(Set.of("submitted"), query.statuses());
        assertEquals(Set.of("owned"), query.assignmentStatuses());
    }

    @Test
    void saveDraftAcceptsAgingPoolStatusAudience() {
        LeadInboxFilterSaveReqVO reqVO = new LeadInboxFilterSaveReqVO();
        reqVO.setAudience("agingPool");
        LeadInboxFilterConfigVO.GroupVO all = group("all", "全部公海客资", 0);
        LeadInboxFilterConfigVO.OptionVO allOption = option("all", "全部", 0);
        LeadInboxFilterConfigVO.OptionVO assigned = option("assigned", "协同跟进中", 10);
        assigned.setConditions(List.of(condition("pool_status", AGING_POOL_ASSIGNED)));
        all.setOptions(List.of(allOption, assigned));
        reqVO.setGroups(List.of(all));
        LeadInboxFilterSchemeDO scheme = scheme(reqVO);
        scheme.setAudience("agingPool");
        when(schemeMapper.selectByAudience("agingPool")).thenReturn(scheme);

        service.saveDraft(reqVO);

        verify(schemeMapper).updateById(org.mockito.ArgumentMatchers.<LeadInboxFilterSchemeDO>argThat(update ->
                update.getDraftConfigJson().contains("pool_status")));
    }

    private static LeadInboxFilterSchemeDO scheme(LeadInboxFilterConfigVO config) {
        LeadInboxFilterSchemeDO scheme = new LeadInboxFilterSchemeDO();
        scheme.setId(1L);
        scheme.setAudience("submitter");
        scheme.setDraftConfigJson(JsonUtils.toJsonString(config));
        return scheme;
    }

    private static LeadInboxFilterSaveReqVO validRequest() {
        LeadInboxFilterSaveReqVO config = new LeadInboxFilterSaveReqVO();
        config.setAudience("submitter");

        LeadInboxFilterConfigVO.GroupVO all = group("all", "全部客资", 0);
        LeadInboxFilterConfigVO.GroupVO pending = group("pending", "待判定", 10);
        pending.setSectionLabel("当前环节");
        pending.setConditions(List.of(condition("status", "submitted")));
        LeadInboxFilterConfigVO.OptionVO allOption = option("all", "全部", 0);
        LeadInboxFilterConfigVO.OptionVO owned = option("owned", "已归属", 10);
        owned.setConditions(List.of(condition("assignment_status", "owned")));
        pending.setOptions(List.of(allOption, owned));
        config.setGroups(List.of(all, pending));
        return config;
    }

    private static LeadInboxFilterSaveReqVO reviewerRequest() {
        LeadInboxFilterSaveReqVO config = new LeadInboxFilterSaveReqVO();
        config.setAudience("reviewer");
        LeadInboxFilterConfigVO.GroupVO todo = group("todo", "待处理", 10);
        todo.setConditions(List.of(condition("handled", "todo")));
        LeadInboxFilterConfigVO.OptionVO all = option("all", "全部", 0);
        LeadInboxFilterConfigVO.OptionVO registration = option("registrationReview", "报名履约中心审批", 10);
        registration.setConditions(List.of(condition("task_definition_key", "registrationReview")));
        todo.setOptions(List.of(all, registration));
        config.setGroups(List.of(todo));
        return config;
    }

    private static LeadInboxFilterConfigVO.GroupVO group(String key, String label, int sort) {
        LeadInboxFilterConfigVO.GroupVO group = new LeadInboxFilterConfigVO.GroupVO();
        group.setKey(key); group.setLabel(label); group.setSort(sort); group.setEnabled(true);
        return group;
    }

    private static LeadInboxFilterConfigVO.OptionVO option(String key, String label, int sort) {
        LeadInboxFilterConfigVO.OptionVO option = new LeadInboxFilterConfigVO.OptionVO();
        option.setKey(key); option.setLabel(label); option.setSort(sort); option.setEnabled(true);
        return option;
    }

    private static LeadInboxFilterConfigVO.ConditionVO condition(String field, String value) {
        LeadInboxFilterConfigVO.ConditionVO condition = new LeadInboxFilterConfigVO.ConditionVO();
        condition.setField(field); condition.setValues(List.of(value));
        return condition;
    }
}
