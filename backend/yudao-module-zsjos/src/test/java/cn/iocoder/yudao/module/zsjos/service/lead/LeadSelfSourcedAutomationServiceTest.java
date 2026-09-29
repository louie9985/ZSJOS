package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadSelfSourcedAutomationServiceTest {
 @InjectMocks LeadSelfSourcedAutomationService service;
 @Mock PermissionApi permissionApi;
 @Mock DictDataApi dictDataApi;
 @Mock LeadFollowUpRuleService followUpRuleService;
 private LeadCreateReqVO request(){var req=new LeadCreateReqVO();req.setRemark("  已联系客户，有意向  ");return req;}
 private DictDataRespDTO dict(String value,int status){var d=new DictDataRespDTO();d.setValue(value);d.setStatus(status);d.setLabel("管理员维护标签");return d;}
 private void permissions(){when(permissionApi.hasAnyPermissions(eq(1L),any(String.class))).thenReturn(true);}
 private void dictionaries(){when(dictDataApi.getDictDataList(DICT_FOLLOW_UP_METHOD)).thenReturn(List.of(dict("other",0)));when(dictDataApi.getDictDataList(DICT_FOLLOW_UP_RESULT)).thenReturn(List.of(dict("interested",0)));}
 @Test void optionalReminderAndTrimmedRemark(){permissions();dictionaries();var req=request();service.validate(req,1L);assertEquals("已联系客户，有意向",req.getRemark());assertNull(req.getSelfSourcedNextFollowUpAt());verify(followUpRuleService).requireEnabledRule();}
 @Test void futureReminder(){permissions();dictionaries();var req=request();req.setSelfSourcedNextFollowUpAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));assertDoesNotThrow(()->service.validate(req,1L));}
 @Test void blankRemark(){permissions();for(String remark:Arrays.asList(null,"","  ")){var req=request();req.setRemark(remark);assertEquals(LEAD_SELF_SOURCED_REMARK_REQUIRED.getCode(),assertThrows(ServiceException.class,()->service.validate(req,1L)).getCode());}verifyNoInteractions(dictDataApi,followUpRuleService);}
 @Test void expiredReminder(){permissions();var req=request();req.setSelfSourcedNextFollowUpAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1));assertEquals(LEAD_FOLLOW_UP_TIME_INVALID.getCode(),assertThrows(ServiceException.class,()->service.validate(req,1L)).getCode());verifyNoInteractions(dictDataApi);}
 @Test void eachFeaturePermissionIsRequired(){for(String denied:List.of("zsjos:lead:self-sourced:create","zsjos:lead-follow-up:create","zsjos:lead:qualify")){reset(permissionApi);when(permissionApi.hasAnyPermissions(eq(1L),any(String.class))).thenAnswer(call->!denied.equals(call.getArgument(1)));assertEquals(LEAD_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,()->service.validate(request(),1L)).getCode());}verifyNoInteractions(dictDataApi);}
 @Test void missingDisabledAndWrongValueNeverFallBackByLabel(){permissions();for(List<DictDataRespDTO> entries:List.of(List.<DictDataRespDTO>of(),List.of(dict("other",1)),List.of(dict("phone",0)))){when(dictDataApi.getDictDataList(DICT_FOLLOW_UP_METHOD)).thenReturn(entries);assertEquals(LEAD_FOLLOW_UP_DICT_INVALID.getCode(),assertThrows(ServiceException.class,()->service.validate(request(),1L)).getCode());}verifyNoInteractions(followUpRuleService);}
 @Test void disabledResultAndRuleFailExplicitly(){permissions();dictionaries();when(dictDataApi.getDictDataList(DICT_FOLLOW_UP_RESULT)).thenReturn(List.of(dict("interested",1)));assertThrows(ServiceException.class,()->service.validate(request(),1L));when(dictDataApi.getDictDataList(DICT_FOLLOW_UP_RESULT)).thenReturn(List.of(dict("interested",0)));when(followUpRuleService.requireEnabledRule()).thenThrow(new IllegalStateException("rule disabled"));assertThrows(IllegalStateException.class,()->service.validate(request(),1L));}
 @Test void provenanceRequiresExactMarker(){assertFalse(LeadAutomaticGeneration.isAutomatic(null));assertFalse(LeadAutomaticGeneration.isAutomatic("{}"));assertFalse(LeadAutomaticGeneration.isAutomatic("{\"remark\":\"sales_self_sourced_auto\"}"));assertTrue(LeadAutomaticGeneration.isAutomatic("{\"generationSource\":\"sales_self_sourced_auto\"}"));}
}
