package cn.iocoder.yudao.module.zsjos.service.personnel;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PartnerStudentLinkServiceTest {
    @Test void eitherExistingBindingRejectsWithoutInsert() {
        for (boolean partnerBound : new boolean[]{true, false}) {
            var service = new PartnerStudentLinkService(); var mapper = mock(PartnerStudentLinkMapper.class);
            var partners = mock(PartnerMapper.class); var people = mock(PersonMapper.class);
            ReflectionTestUtils.setField(service,"mapper",mapper); ReflectionTestUtils.setField(service,"partnerMapper",partners); ReflectionTestUtils.setField(service,"personMapper",people);
            when(partners.selectById(3L)).thenReturn(new PartnerDO()); when(people.selectById(2L)).thenReturn(new PersonDO());
            if(partnerBound) when(mapper.selectActiveByPartner(3L)).thenReturn(new PartnerStudentLinkDO());
            else when(mapper.selectActiveByStudent(2L)).thenReturn(new PartnerStudentLinkDO());
            assertThrows(RuntimeException.class, () -> service.bind(3L,2L,null,7L));
            verify(mapper, never()).insert(any(PartnerStudentLinkDO.class));
        }
    }
}
