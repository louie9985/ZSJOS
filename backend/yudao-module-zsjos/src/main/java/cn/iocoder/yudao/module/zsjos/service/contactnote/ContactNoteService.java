package cn.iocoder.yudao.module.zsjos.service.contactnote;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.contactnote.vo.ContactNoteVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contactnote.ContactNoteDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contactnote.ContactNoteMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class ContactNoteService {
    @Resource private ContactNoteMapper mapper;
    @Resource private SecurityFrameworkService permissions;

    private LambdaQueryWrapperX<ContactNoteDO> subject(String type, Long id) {
        return new LambdaQueryWrapperX<ContactNoteDO>().eq(ContactNoteDO::getSubjectType, type)
                .eq(ContactNoteDO::getSubjectId, id);
    }

    public Page page(String type, Long id, PageParam page, String appendPermission) {
        var result = mapper.selectPage(page, subject(type, id).orderByDesc(ContactNoteDO::getId));
        var entries = result.getList().stream().map(row -> new Entry(row.getId(), row.getAuthorName(),
                row.getCreateTime(), row.getContent())).toList();
        return new Page(entries, result.getTotal(), permissions.hasPermission(appendPermission));
    }

    public Long append(String type, Long id, Append request) {
        ContactNoteDO row = new ContactNoteDO();
        row.setSubjectType(type);
        row.setSubjectId(id);
        row.setAuthorId(getLoginUserId());
        row.setAuthorName(getLoginUserNickname());
        row.setContent(request.content().strip());
        row.setRequestKey(request.requestKey());
        try {
            mapper.insert(row);
            return row.getId();
        } catch (DuplicateKeyException ex) {
            var existing = mapper.selectOne(subject(type, id).eq(ContactNoteDO::getAuthorId, row.getAuthorId())
                    .eq(ContactNoteDO::getRequestKey, row.getRequestKey()));
            if (existing == null) throw ex;
            if (!existing.getContent().equals(row.getContent())) throw invalidParamException("请求已用于其他沟通内容");
            return existing.getId();
        }
    }
}
