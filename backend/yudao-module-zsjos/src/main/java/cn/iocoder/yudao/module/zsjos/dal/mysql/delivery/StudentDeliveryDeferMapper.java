package cn.iocoder.yudao.module.zsjos.dal.mysql.delivery;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX; import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO; import org.apache.ibatis.annotations.Mapper;
@Mapper public interface StudentDeliveryDeferMapper extends BaseMapperX<StudentDeliveryDeferDO> { default StudentDeliveryDeferDO selectByProcessInstanceId(String id){ return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<StudentDeliveryDeferDO>().eq(StudentDeliveryDeferDO::getBpmProcessInstanceId,id)); } }
