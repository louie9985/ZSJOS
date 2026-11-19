package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.EnrollmentAgreementMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.util.Map;
import java.time.LocalDate;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class EnrollmentAgreementService {
    public static final String CONSENT = "我已核对报名信息，阅读并同意《职业证书培训服务协议》";
    private static final Pattern VARIABLE = Pattern.compile("[{][{]([^{}]+)[}][}]");
    @Resource private EnrollmentAgreementMapper templates;
    @Resource private SalesOrderItemMapper items;
    @Resource private SalesOrderMapper orders;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper categories;

    public record Template(long revision, long publishedVersion, String draftBody) {}
    public record Change(@NotNull @Positive Long categoryId, @NotNull Long revision, @NotBlank @Size(max=100000) String body) {}

    private String categoryName(Long categoryId) {
        var category = categories.selectById(categoryId);
        if (category == null || categories.selectCountByParentId(categoryId) != 0)
            throw exception(FIELD_INVALID, "请选择产品的末级分类");
        return category.getName();
    }

    public Template detail(Long categoryId) {
        categoryName(categoryId);
        var row = templates.selectById(categoryId);
        return row == null ? new Template(0, 0, templates.starterBody())
                : new Template(row.getRevision(), row.getPublishedVersion(), row.getDraftBody());
    }

    public void save(Change change, boolean publish) {
        categoryName(change.categoryId());
        var existing = templates.selectById(change.categoryId());
        if (!change.revision().equals(existing == null ? 0L : existing.getRevision())) throw exception(VERSION_CONFLICT);
        if (publish && existing == null) throw exception(FIELD_INVALID, "请先保存该分类的协议草稿");
        var source = publish ? existing.getDraftBody() : change.body();
        var html = Jsoup.clean(source, new Safelist().addTags("p","br","h1","h2","h3","h4","strong","b","em","i","u","ol","ul","li","blockquote","span","div"));
        var remaining = html;
        for (var key : new String[]{"学员姓名","联系电话","课程名称","实际费用"}) {
            var token = "{{" + key + "}}";
            if (!html.contains(token)) throw exception(FIELD_INVALID, "协议缺少变量 " + token);
            remaining = remaining.replace(token, "");
        }
        if (remaining.contains("{{") || remaining.contains("}}")) throw exception(FIELD_INVALID, "协议含未知变量");
        if (existing == null) {
            var draft = new EnrollmentAgreementDO();
            draft.setId(change.categoryId()); draft.setRevision(1L); draft.setPublishedVersion(0L);
            draft.setDraftBody(html); draft.setPublishedBody("");
            try { templates.insert(draft); }
            catch (org.springframework.dao.DuplicateKeyException conflict) { throw exception(VERSION_CONFLICT); }
            return;
        }
        var update = new LambdaUpdateWrapper<EnrollmentAgreementDO>()
                .eq(EnrollmentAgreementDO::getId, change.categoryId()).eq(EnrollmentAgreementDO::getRevision, change.revision())
                .set(EnrollmentAgreementDO::getDraftBody, html).set(EnrollmentAgreementDO::getRevision, change.revision()+1);
        if (publish) update.set(EnrollmentAgreementDO::getPublishedBody, html)
                .set(EnrollmentAgreementDO::getPublishedVersion, change.revision()+1);
        if (templates.update(null, update) != 1) throw exception(VERSION_CONFLICT);
    }

    public void bind(StudentInfoFormDO form) {
        var path = JsonUtils.parseArray(form.getCategoryPathSnapshot(), Long.class);
        if (path == null || path.isEmpty()) throw exception(FIELD_INVALID, "报名产品缺少末级分类");
        var categoryId = path.getFirst();
        var name = categoryName(categoryId);
        var row = templates.selectById(categoryId);
        if (row == null || row.getPublishedVersion() == 0) throw exception(FIELD_INVALID, name + "尚未发布报名协议");
        form.setAgreementCategoryName(name);
        form.setAgreementVersion(row.getPublishedVersion());
        form.setAgreementTemplate(row.getPublishedBody());
    }

    static String fillSigningDates(String template, LocalDate date) {
        return template.replaceAll("(<p>\\s*签订日期[：:]\\s*)(</p>)", "$1" + date + "$2");
    }

    public String render(StudentInfoFormDO form, LocalDate date) {
        var item = items.selectById(form.getOrderItemId());
        if (item == null || item.getPayableAmount() == null) throw exception(FIELD_INVALID,"订单费用缺失");
        var order = orders.selectById(item.getOrderId());
        if (order == null || !java.util.Objects.equals(form.getPersonId(), order.getPersonId()))
            throw exception(LINK_INVALID);
        String course = form.getAgreementCategoryName();
        if (course == null && item.getProductSnapshot() != null) {
            var snapshot = JsonUtils.parseTree(item.getProductSnapshot());
            course = snapshot == null ? null : snapshot.path("categoryName").asText(null);
        }
        if (course == null || course.isBlank() || order.getStudentName() == null || order.getStudentMobile() == null)
            throw exception(FIELD_INVALID,"请补全订单学员、电话和课程信息");
        var variables = Map.of("学员姓名", order.getStudentName(),"联系电话", order.getStudentMobile(),
                "课程名称", course,"实际费用",item.getPayableAmount().toPlainString());
        return VARIABLE.matcher(fillSigningDates(form.getAgreementTemplate(), date)).replaceAll(match ->
                java.util.regex.Matcher.quoteReplacement(HtmlUtils.htmlEscape(variables.get(match.group(1)))));
    }
}
