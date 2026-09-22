package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterAssistRequestDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

@Mapper
public interface LeadSubmitterAssistRequestMapper extends BaseMapperX<LeadSubmitterAssistRequestDO> {

    default LeadSubmitterAssistRequestDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(LeadSubmitterAssistRequestDO::getIdempotencyKey, idempotencyKey);
    }
    default LeadSubmitterAssistRequestDO selectPendingByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapper<LeadSubmitterAssistRequestDO>()
                .eq(LeadSubmitterAssistRequestDO::getLeadId, leadId)
                .eq(LeadSubmitterAssistRequestDO::getStatus, "pending")
                .orderByDesc(LeadSubmitterAssistRequestDO::getId).last("LIMIT 1"));
    }
    default PageResult<LeadSubmitterAssistRequestDO> selectPageByLeadId(Long leadId, PageParam page) {
        Page<LeadSubmitterAssistRequestDO> result = selectPage(new Page<>(page.getPageNo(), page.getPageSize()),
                new LambdaQueryWrapper<LeadSubmitterAssistRequestDO>().eq(LeadSubmitterAssistRequestDO::getLeadId, leadId)
                        .orderByDesc(LeadSubmitterAssistRequestDO::getId));
        return new PageResult<>(result.getRecords(), result.getTotal());
    }
}
