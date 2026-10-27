package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServiceAnnotationVO.Save;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentServiceAnnotationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceAnnotationServiceTest {
    @InjectMocks StudentServiceAnnotationService service;
    @Mock StudentServiceAnnotationMapper annotations;
    @Mock ServiceRelationMapper relations;
    @Mock StudentServiceAnnotationPermissionProvider permissions;
    @Mock DictDataApi dictionaries;
    MockedStatic<SecurityFrameworkUtils> security;
    @BeforeEach void setup() {
        security=mockStatic(SecurityFrameworkUtils.class); security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
    }
    @AfterEach void close() { security.close(); }
    Save request() { var r=new Save();r.setVersion(0);r.setProxyStudent("true");r.setAgentName("  代理人  ");r.setServiceNeeded("false");return r; }
    DictDataRespDTO dict(String value,String label,int status) { var d=new DictDataRespDTO();d.setValue(value);d.setLabel(label);d.setStatus(status);return d; }
    StudentServiceAnnotationDO existing() { var r=new StudentServiceAnnotationDO();r.setId(1L);r.setServiceRelationId(11L);r.setVersion(2);r.setProxyStudent("true");r.setProxyStudentLabel("历史是");r.setAgentName("旧代理人");r.setServiceNeeded("false");r.setServiceNeededLabel("历史否");return r; }
    @Test void emptyIsUnmarkedAndReadDoesNotWrite() {
        var result=service.detail(11L); assertEquals(0,result.getVersion());assertNull(result.getProxyStudent());assertNull(result.getServiceNeeded());
        verify(annotations,never()).insert(any(StudentServiceAnnotationDO.class));
    }
    @Test void createUsesServerLabelsAndDoesNotMutateService() {
        when(dictionaries.getDictDataList(StudentServiceAnnotationConstants.DICT)).thenReturn(List.of(dict("true","是",0),dict("false","否",0)));
        var result=service.save(11L,request());assertEquals(1,result.getVersion());assertEquals("是",result.getProxyStudentLabel());assertEquals("代理人",result.getAgentName());
        var captor=ArgumentCaptor.forClass(StudentServiceAnnotationDO.class);verify(annotations).insert(captor.capture());assertEquals(11L,captor.getValue().getServiceRelationId());
        verify(relations,never()).updateById(any(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO.class));
    }
    @Test void retainsHistoricalLabelsWhenDictionaryRenamedOrDeleted() {
        when(annotations.byRelation(11L)).thenReturn(existing());when(annotations.replace(any(),eq(2))).thenReturn(1);
        var req=request();req.setVersion(2);
        var result=service.save(11L,req);assertEquals("历史是",result.getProxyStudentLabel());assertEquals("历史否",result.getServiceNeededLabel());verifyNoInteractions(dictionaries);
    }
    @Test void newSelectionRequiresEnabledDictionary() {
        when(dictionaries.getDictDataList(StudentServiceAnnotationConstants.DICT)).thenReturn(List.of(dict("true","已停用",1)));
        assertEquals(StudentServiceAnnotationConstants.INVALID.getCode(),assertThrows(ServiceException.class,()->service.save(11L,request())).getCode());
        verify(annotations,never()).insert(any(StudentServiceAnnotationDO.class));
    }
    @Test void noProxyClearsAgentAndExplicitNullClearsChoice() {
        when(annotations.byRelation(11L)).thenReturn(existing());when(annotations.replace(any(),eq(2))).thenReturn(1);
        when(dictionaries.getDictDataList(StudentServiceAnnotationConstants.DICT)).thenReturn(List.of(dict("false","否",0)));
        var req=request();req.setVersion(2);req.setProxyStudent("false");req.setServiceNeeded(null);
        var result=service.save(11L,req);assertNull(result.getAgentName());assertNull(result.getServiceNeededLabel());assertNull(result.getServiceNeeded());
    }
    @Test void staleVersionNeverWrites() {
        when(annotations.byRelation(11L)).thenReturn(existing());
        assertEquals(StudentServiceAnnotationConstants.CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.save(11L,request())).getCode());
        verify(annotations,never()).replace(any(),anyInt());verifyNoInteractions(dictionaries);
    }
    @Test void missingAgentNeverWrites() {
        when(dictionaries.getDictDataList(StudentServiceAnnotationConstants.DICT)).thenReturn(List.of(dict("true","是",0),dict("false","否",0)));
        var req=request();req.setAgentName("  ");
        assertEquals(StudentServiceAnnotationConstants.AGENT_REQUIRED.getCode(),assertThrows(ServiceException.class,()->service.save(11L,req)).getCode());
        verify(annotations,never()).insert(any(StudentServiceAnnotationDO.class));
    }
    @Test void reassignmentDuringLockWaitIsRejectedBeforeReadingAnnotation() {
        doThrow(new ServiceException(403,"权限已变化")).when(permissions).check(11L,"update",7L);
        assertThrows(ServiceException.class,()->service.save(11L,request()));verifyNoInteractions(annotations);
    }
    @Test void samePersonDifferentServicesReadSeparateRows() {
        when(annotations.byRelation(11L)).thenReturn(existing());
        assertEquals("旧代理人",service.detail(11L).getAgentName());assertNull(service.detail(12L).getAgentName());
    }
}
