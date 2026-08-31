package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.advancedfilter.AdvancedFilterTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_TEMPLATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedFilterTemplateServiceImplTest {
    @InjectMocks private AdvancedFilterTemplateServiceImpl service;
    @Mock private AdvancedFilterTemplateMapper mapper;
    @Mock private AdvancedFilterService advancedFilterService;

    @Test
    void createPersonalStoresStructuredFilterAndClearsOtherDefault() {
        AdvancedFilterTemplateSaveReqVO reqVO = request(true);
        when(advancedFilterService.supportsScene("lead")).thenReturn(true);

        service.createPersonal(reqVO, 11L);

        ArgumentCaptor<AdvancedFilterTemplateDO> captor = ArgumentCaptor.forClass(AdvancedFilterTemplateDO.class);
        verify(mapper).insert(captor.capture());
        AdvancedFilterTemplateDO saved = captor.getValue();
        assertEquals("personal", saved.getScope());
        assertEquals(11L, saved.getOwnerUserId());
        assertEquals("lead", saved.getScene());
        assertEquals("lead_management", saved.getPageKey());
        assertEquals("待判定", saved.getName());
        assertEquals("lead.status", JsonUtils.parseObject(saved.getFilterJson(), AdvancedFilterGroupReqVO.class)
                .getConditions().getFirst().getFieldKey());
        verify(advancedFilterService).validate(eq("lead"), any(AdvancedFilterGroupReqVO.class));
        verify(mapper).clearDefault("lead", "lead_management", "personal", 11L, saved.getId());
    }

    @Test
    void updatePersonalRejectsTemplateOwnedByAnotherUser() {
        AdvancedFilterTemplateDO existing = template("personal", 22L);
        when(mapper.selectById(9L)).thenReturn(existing);
        AdvancedFilterTemplateSaveReqVO reqVO = request(false);
        reqVO.setId(9L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updatePersonal(reqVO, 11L));

        assertEquals(ADVANCED_FILTER_TEMPLATE_NOT_EXISTS.getCode(), error.getCode());
    }

    @Test
    void updateSystemDoesNotAcceptSceneOrPageMutation() {
        AdvancedFilterTemplateDO existing = template("system", null);
        existing.setPageKey("lead_claim_pool");
        when(mapper.selectById(9L)).thenReturn(existing);
        AdvancedFilterTemplateSaveReqVO reqVO = request(false);
        reqVO.setId(9L);

        assertThrows(ServiceException.class, () -> service.updateSystem(reqVO));
    }

    @Test
    void updateSystemRejectsStaleVersion() {
        AdvancedFilterTemplateDO existing = template("system", null);
        existing.setVersion(2);
        when(mapper.selectById(9L)).thenReturn(existing);
        AdvancedFilterTemplateSaveReqVO reqVO = request(false);
        reqVO.setId(9L);
        reqVO.setVersion(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateSystem(reqVO));

        assertEquals(ADVANCED_FILTER_TEMPLATE_INVALID.getCode(), error.getCode());
    }

    @Test
    void visibleListReturnsParsedFilters() {
        AdvancedFilterTemplateDO system = template("system", null);
        AdvancedFilterTemplateDO personal = template("personal", 11L);
        personal.setName("个人模板");
        when(advancedFilterService.supportsScene("lead")).thenReturn(true);
        when(mapper.selectVisibleList("lead", "lead_management", 11L)).thenReturn(List.of(system, personal));

        var result = service.visibleList("lead", "lead_management", 11L);

        assertEquals(List.of("系统模板", "个人模板"), result.stream().map(item -> item.getName()).toList());
        assertEquals("lead.status", result.getFirst().getFilter().getConditions().getFirst().getFieldKey());
    }

    private static AdvancedFilterTemplateSaveReqVO request(boolean defaultTemplate) {
        AdvancedFilterTemplateSaveReqVO reqVO = new AdvancedFilterTemplateSaveReqVO();
        reqVO.setScene("lead");
        reqVO.setPageKey("lead_management");
        reqVO.setName("待判定");
        reqVO.setFilter(filter());
        reqVO.setSort(10);
        reqVO.setEnabled(true);
        reqVO.setDefaultTemplate(defaultTemplate);
        return reqVO;
    }

    private static AdvancedFilterTemplateDO template(String scope, Long ownerUserId) {
        AdvancedFilterTemplateDO template = new AdvancedFilterTemplateDO();
        template.setId(9L);
        template.setScene("lead");
        template.setPageKey("lead_management");
        template.setScope(scope);
        template.setOwnerUserId(ownerUserId);
        template.setName("系统模板");
        template.setFilterJson(JsonUtils.toJsonString(filter()));
        template.setSort(10);
        template.setEnabled(true);
        template.setDefaultTemplate(false);
        template.setVersion(0);
        return template;
    }

    private static AdvancedFilterGroupReqVO filter() {
        AdvancedFilterConditionReqVO condition = new AdvancedFilterConditionReqVO();
        condition.setFieldKey("lead.status");
        condition.setOperator("in");
        condition.setValue(List.of("submitted"));
        AdvancedFilterGroupReqVO group = new AdvancedFilterGroupReqVO();
        group.getConditions().add(condition);
        return group;
    }
}
