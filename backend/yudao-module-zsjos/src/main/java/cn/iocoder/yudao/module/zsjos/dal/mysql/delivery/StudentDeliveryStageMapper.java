package cn.iocoder.yudao.module.zsjos.dal.mysql.delivery;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.time.LocalDateTime;
import java.util.List;
@Mapper public interface StudentDeliveryStageMapper extends BaseMapperX<StudentDeliveryStageDO> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM zsjos_student_delivery_stage WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    StudentDeliveryStageDO selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id,
                                                @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);
    default List<StudentDeliveryStageDO> selectDueWaiting(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getStatus, "WAITING")
                .le(StudentDeliveryStageDO::getTriggerAt, now)
                .orderByAsc(StudentDeliveryStageDO::getTriggerAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }
    default boolean claim(Long id, LocalDateTime dueAt) {
        return update(null, new LambdaUpdateWrapper<StudentDeliveryStageDO>()
                .eq(StudentDeliveryStageDO::getId, id).eq(StudentDeliveryStageDO::getStatus, "WAITING")
                .set(StudentDeliveryStageDO::getStatus, "PENDING").set(StudentDeliveryStageDO::getDueAt, dueAt)
                .setSql("version = version + 1")) == 1;
    }
}
