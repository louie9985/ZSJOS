package cn.iocoder.yudao.module.infra.service.config;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.config.vo.ConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 参数配置 Service 接口
 *
 * @author 芋道源码
 */
public interface ConfigService {

    String DEFAULT_USER_AVATAR_KEY = "zsjos.user.default-avatar";

    /**
     * 创建参数配置
     *
     * @param createReqVO 创建信息
     * @return 配置编号
     */
    Long createConfig(@Valid ConfigSaveReqVO createReqVO);

    /**
     * 更新参数配置
     *
     * @param updateReqVO 更新信息
     */
    void updateConfig(@Valid ConfigSaveReqVO updateReqVO);

    /**
     * 删除参数配置
     *
     * @param id 配置编号
     */
    void deleteConfig(Long id);

    /**
     * 批量删除参数配置
     *
     * @param ids 配置编号列表
     */
    void deleteConfigList(List<Long> ids);

    /**
     * 获得参数配置
     *
     * @param id 配置编号
     * @return 参数配置
     */
    ConfigDO getConfig(Long id);

    /**
     * 根据参数键，获得参数配置
     *
     * @param key 配置键
     * @return 参数配置
     */
    ConfigDO getConfigByKey(String key);

    /**
     * 获得全平台默认员工头像。
     *
     * @return 头像地址；未配置或已清空时返回 {@code null}
     */
    String getDefaultUserAvatar();

    /**
     * 更新全平台默认员工头像。
     *
     * @param avatar 头像地址；{@code null} 或空白表示清空
     */
    void updateDefaultUserAvatar(String avatar);

    /** Updates an existing system-owned configuration without changing its metadata. */
    void updateSystemConfigValue(String key, String value);

    /**
     * 获得参数配置分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    PageResult<ConfigDO> getConfigPage(ConfigPageReqVO reqVO);

}
