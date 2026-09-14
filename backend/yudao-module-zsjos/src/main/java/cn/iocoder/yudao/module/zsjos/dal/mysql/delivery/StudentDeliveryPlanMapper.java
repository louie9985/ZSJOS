package cn.iocoder.yudao.module.zsjos.dal.mysql.delivery;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
@Mapper public interface StudentDeliveryPlanMapper extends BaseMapperX<StudentDeliveryPlanDO> {
 default StudentDeliveryPlanDO selectActiveByAccountId(Long accountId) { return selectOne(new LambdaQueryWrapperX<StudentDeliveryPlanDO>().eq(StudentDeliveryPlanDO::getAccountId, accountId).eq(StudentDeliveryPlanDO::getStatus, "ACTIVE").last("LIMIT 1")); }
}
