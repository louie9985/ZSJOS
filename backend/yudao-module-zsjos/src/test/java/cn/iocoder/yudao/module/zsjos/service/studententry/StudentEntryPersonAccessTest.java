package cn.iocoder.yudao.module.zsjos.service.studententry;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StudentEntryPersonAccessTest {
    @InjectMocks StudentEntryPersonAccess access;
    @Mock PermissionApi permissions;
    @Mock ServiceRelationMapper relations;
    @Mock StudentServiceObjectPermissionProvider reader;
    @Mock cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassObjectPermissionProvider classReader;
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper leads;
    @Mock cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService leadReader;
    @Test void creationPermissionDoesNotGrantExistingIdentityReads() {
        when(permissions.hasAnyPermissions(9L,StudentEntryService.PERMISSION)).thenReturn(true);
        assertFalse(access.hasPermission(11L,"reuse",9L));verifyNoInteractions(relations,reader);
    }
    @Test void reusedPersonNeedsFeatureAndCurrentObjectVisibility() {
        when(permissions.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        var relation=new ServiceRelationDO();relation.setId(44L);
        when(relations.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(relation));
        assertFalse(access.hasPermission(11L,"reuse",9L));
        when(reader.hasPermission(44L,"read",9L)).thenReturn(true);
        assertTrue(access.hasPermission(11L,"reuse",9L));
    }
    @Test void classOnlyPermissionDoesNotReadAnUnrelatedMediaService() {
        var relation=new ServiceRelationDO();relation.setId(44L);relation.setClassId(8L);
        lenient().when(permissions.hasAnyPermissions(9L,"zsjos:delivery-class:query","zsjos:delivery-class:query-managed","zsjos:delivery-class:query-my")).thenReturn(true);
        assertFalse(access.canReadService(relation,9L));verifyNoInteractions(reader);
        when(classReader.hasPermission(8L,"read",9L)).thenReturn(true);
        when(reader.hasPermission(44L,"read",9L)).thenReturn(true);
        assertTrue(access.canReadService(relation,9L));
    }
    @Test void leadOnlyPersonNeedsExistingUnmaskedLeadReadBeforeReuse() {
        when(permissions.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        var lead=new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO();lead.setId(3L);lead.setPersonId(11L);
        when(leads.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(lead));
        when(leadReader.canReadDetail(lead,9L)).thenReturn(true);
        assertFalse(access.hasPermission(11L,"reuse",9L));
        when(leadReader.canViewUnmaskedIdentity(9L,lead)).thenReturn(true);
        assertTrue(access.hasPermission(11L,"reuse",9L));
    }
}
