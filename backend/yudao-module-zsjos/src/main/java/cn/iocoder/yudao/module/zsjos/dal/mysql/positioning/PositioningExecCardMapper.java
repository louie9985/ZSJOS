package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningExecCardDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface PositioningExecCardMapper extends BaseMapperX<PositioningExecCardDO> {
    default PositioningExecCardDO selectByCard(Long cardId) {
        return selectOne(new LambdaQueryWrapper<PositioningExecCardDO>().eq(PositioningExecCardDO::getPositioningCardId, cardId));
    }
    default int signDirector(Long id, Integer version, java.time.LocalDateTime now, String snapshot, boolean effective) {
        return update(null, new LambdaUpdateWrapper<PositioningExecCardDO>().eq(PositioningExecCardDO::getId,id)
                .eq(PositioningExecCardDO::getVersion,version).isNull(PositioningExecCardDO::getDirectorConfirmedAt)
                .eq(PositioningExecCardDO::getStatus,"pending_signatures").set(PositioningExecCardDO::getDirectorConfirmedAt,now)
                .set(PositioningExecCardDO::getSignatureSnapshotJson,snapshot).set(PositioningExecCardDO::getVersion,version+1));
    }
    default int signOperator(Long id, Integer version, java.time.LocalDateTime now, String snapshot) {
        return update(null, new LambdaUpdateWrapper<PositioningExecCardDO>().eq(PositioningExecCardDO::getId,id)
                .eq(PositioningExecCardDO::getVersion,version).isNull(PositioningExecCardDO::getOperatorConfirmedAt)
                .eq(PositioningExecCardDO::getStatus,"pending_signatures").set(PositioningExecCardDO::getOperatorConfirmedAt,now)
                .set(PositioningExecCardDO::getSignatureSnapshotJson,snapshot).set(PositioningExecCardDO::getVersion,version+1));
    }
}
