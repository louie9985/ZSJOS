package cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.ForcedFormRecipientDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;
@Mapper public interface ForcedFormRecipientMapper extends BaseMapperX<ForcedFormRecipientDO> {
 @Select("SELECT * FROM zsjos_forced_form_recipient WHERE user_id=#{userId} AND status='PENDING' AND deleted=0 ORDER BY id") List<ForcedFormRecipientDO> selectPending(Long userId);
}
