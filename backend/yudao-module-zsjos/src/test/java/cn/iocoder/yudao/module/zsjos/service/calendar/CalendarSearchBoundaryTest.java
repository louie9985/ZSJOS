package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarSearchReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarSearchReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.PersonalCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.service.personalcalendar.PersonalCalendarEventService;
import cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarSearchBoundaryTest {
    @Test void validatesBlankOversizedKeywordPageAndDateRange() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            var req=new CalendarSearchReqVO();req.setKeyword("   ");assertFalse(validator.validate(req).isEmpty());
            req.setKeyword(" 中文%_ ");assertEquals("中文%_",req.getKeyword());assertEquals(20,req.getPageSize());assertTrue(validator.validate(req).isEmpty());
            req.setPageSize(101);assertFalse(validator.validate(req).isEmpty());req.setPageSize(-1);assertFalse(validator.validate(req).isEmpty());req.setPageSize(20);
            req.setSort("id; SQL");assertFalse(validator.validate(req).isEmpty());req.setSort("nearest");
            req.setKeyword("字".repeat(101));assertFalse(validator.validate(req).isEmpty());req.setKeyword("标题");
            req.setRangeStart(java.time.LocalDate.of(2027,1,2));req.setRangeEnd(java.time.LocalDate.of(2027,1,1));assertFalse(validator.validate(req).isEmpty());
        }
    }
    @Test void personalSearchUsesActualScopeResolverAndNeverQueriesDeniedOtherOwner() {
        var permissions=mock(PermissionApi.class);var users=mock(AdminUserApi.class);
        var scope=new BusinessReadScopeService();ReflectionTestUtils.setField(scope,"permissionApi",permissions);ReflectionTestUtils.setField(scope,"userApi",users);
        var mapper=mock(PersonalCalendarEventMapper.class);var service=new PersonalCalendarEventService();
        ReflectionTestUtils.setField(service,"readScopeService",scope);ReflectionTestUtils.setField(service,"mapper",mapper);
        var req=new PersonalCalendarSearchReqVO();req.setKeyword("标题");
        when(mapper.selectSearch(req,7L)).thenReturn(new PageResult<>(List.of(),0L));
        service.search(req,7L);verify(mapper).selectSearch(req,7L);
        req.setReadScope("ALL");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.search(req,7L));
        verify(mapper,never()).selectSearch(req,null);
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
        when(mapper.selectSearch(req,null)).thenReturn(new PageResult<>(List.of(),0L));
        service.search(req,7L);verify(mapper).selectSearch(req,null);
        req.setReadScope("USER");req.setTargetUserId(99L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.search(req,7L));
        verify(mapper,never()).selectSearch(req,99L);
    }
    @Test void allSearchEndpointsPreserveTheirExistingQueryPermission() throws Exception {
        for(var entry:List.of(
                new String[]{"personalcalendar.PersonalCalendarEventController","personalcalendar.vo.PersonalCalendarSearchReqVO","zsjos:personal-calendar:query"},
                new String[]{"coursecalendar.CourseCalendarController","coursecalendar.vo.CourseCalendarSearchReqVO","zsjos:course-calendar:query"},
                new String[]{"examcalendar.ExamScheduleController","examcalendar.vo.ExamCalendarSearchReqVO","zsjos:exam-calendar:query"},
                new String[]{"account.MediaAccountController","account.vo.MediaCalendarSearchReqVO","zsjos:media-calendar:query"},
                new String[]{"lead.LeadCalendarController","lead.vo.calendar.LeadCalendarSearchReqVO","zsjos:lead-follow-up-calendar:query"})) {
            String base="cn.iocoder.yudao.module.zsjos.controller.admin.";
            var method=Class.forName(base+entry[0]).getMethod("search",Class.forName(base+entry[1]));
            var expression=method.getAnnotation(PreAuthorize.class).value();
            assertTrue(expression.contains("@ss.hasPermission('"+entry[2]+"')"));
            if(entry[0].startsWith("lead.")) assertTrue(expression.contains("&& @ss.hasPermission('zsjos:lead:query')"));
            assertNotNull(method.getParameters()[0].getAnnotation(jakarta.validation.Valid.class));
        }
    }
}

