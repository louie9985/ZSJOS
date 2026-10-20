package cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface EducationTeamOrganizationMapper {
 @Select("SELECT dept_id FROM zsjos_performance_org WHERE deleted=0 AND business_domain='delivery'")
 List<Long> deliveryDepartments();
}
