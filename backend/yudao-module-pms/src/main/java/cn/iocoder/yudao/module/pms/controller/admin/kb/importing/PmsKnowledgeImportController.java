package cn.iocoder.yudao.module.pms.controller.admin.kb.importing;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.controller.admin.kb.importing.vo.PmsKnowledgeImportVO.*;
import cn.iocoder.yudao.module.pms.service.kb.importing.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/pms/kb/import")
@Validated
public class PmsKnowledgeImportController {
    @Resource private PmsKnowledgeImportService service;
    @GetMapping("/policy")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<PmsKnowledgeImportPolicy> policy() { return success(service.policy()); }
    @PostMapping("/prepare")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<List<Item>> prepare(@Valid @RequestBody Request req) { return success(service.prepare(req,getLoginUserId())); }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<Task> create(@Valid @RequestBody Request req) { return success(service.create(req,getLoginUserId())); }
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<PageResult<Task>> page(@Valid PageParam page) { return success(service.page(page,getLoginUserId())); }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<Task> get(@RequestParam Long id) { return success(service.get(id,getLoginUserId())); }
    @GetMapping("/item-page")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<PageResult<Item>> item_page(@RequestParam Long id, @Valid PageParam page) { return success(service.itemPage(id,page,getLoginUserId())); }
    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<Task> upload(@RequestParam Long taskId, @RequestParam Long itemId, @RequestParam MultipartFile file) { return success(service.upload(taskId,itemId,file,getLoginUserId())); }
    @PostMapping("/resume")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<Task> resume(@Valid @RequestBody Resume req) { return success(service.resume(req,getLoginUserId())); }
    @PostMapping("/finish")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<Task> finish(@RequestParam Long id) { return success(service.finish(id,getLoginUserId())); }
}
