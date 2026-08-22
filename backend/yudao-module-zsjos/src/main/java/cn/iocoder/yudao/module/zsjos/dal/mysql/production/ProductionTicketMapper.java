package cn.iocoder.yudao.module.zsjos.dal.mysql.production;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductionTicketMapper extends BaseMapperX<ProductionTicketDO> {
    default List<ProductionTicketDO> selectByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProductionTicketDO>()
                .in(ProductionTicketDO::getAccountId, accountIds)
                .orderByDesc(ProductionTicketDO::getUpdateTime).orderByDesc(ProductionTicketDO::getId));
    }
    default PageResult<ProductionTicketDO> selectPage(ProductionTicketPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<ProductionTicketDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(ProductionTicketDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(x -> x.like(ProductionTicketDO::getTicketNo, req.getKeyword())
                    .or().like(ProductionTicketDO::getScriptText, req.getKeyword()));
        }
        if (!all) query.and(x -> x.in(ProductionTicketDO::getOwnerOperatorUserId, userIds).or()
                .in(ProductionTicketDO::getAssigneeFilmingEditorUserId, userIds).or()
                .in(ProductionTicketDO::getReviewerUserId, userIds));
        return selectPage(req, query.orderByDesc(ProductionTicketDO::getUpdateTime).orderByDesc(ProductionTicketDO::getId));
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>().eq(ProductionTicketDO::getId, id)
                .eq(ProductionTicketDO::getVersion, version).eq(ProductionTicketDO::getStatus, from)
                .set(ProductionTicketDO::getStatus, to).set(ProductionTicketDO::getVersion, version + 1));
    }
    default int rejectForRevision(Long id, Integer version, String reason) {
        return update(null, new LambdaUpdateWrapper<ProductionTicketDO>().eq(ProductionTicketDO::getId,id)
                .eq(ProductionTicketDO::getVersion,version).eq(ProductionTicketDO::getStatus,"checking")
                .set(ProductionTicketDO::getStatus,"rejected").set(ProductionTicketDO::getReworkReasonType,reason)
                .setSql("revision_count = revision_count + 1").set(ProductionTicketDO::getVersion,version+1));
    }
}
