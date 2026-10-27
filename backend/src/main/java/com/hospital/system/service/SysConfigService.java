package com.hospital.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.system.dto.ConfigQuery;
import com.hospital.system.dto.ConfigRequest;
import com.hospital.system.dto.ConfigVO;
import com.hospital.system.entity.SysConfig;
import com.hospital.system.mapper.SysConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 系统参数服务。
 *
 * <p><b>参数值不缓存</b>：改完立刻生效是参数配置页的基本预期，
 * 一旦加缓存就要处理「改了但没生效」，而这件事在排障时极难定位
 * —— 使用者看到的是旧值，日志里却已经写成功了。</p>
 *
 * <p>系统内置参数（{@code configType = 1}）不允许删除：
 * 它们是启动期就被读取的配置，删掉等于让系统下次起不来。</p>
 */
@Slf4j
@Service
public class SysConfigService {

    /** 1 系统内置 */
    public static final int TYPE_BUILTIN = 1;

    private final SysConfigMapper configMapper;

    public SysConfigService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public List<ConfigVO> list(ConfigQuery query) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(query.getConfigName()), SysConfig::getConfigName, query.getConfigName())
                .like(StringUtils.hasText(query.getConfigKey()), SysConfig::getConfigKey, query.getConfigKey())
                .eq(query.getConfigType() != null, SysConfig::getConfigType, query.getConfigType())
                .orderByAsc(SysConfig::getConfigKey);
        return configMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public ConfigVO detail(Long id) {
        return toVO(requireConfig(id));
    }

    /** 按键取值，供业务代码调用；未配置时返回 null */
    public String value(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return null;
        }
        SysConfig config = configMapper.selectByKey(configKey.trim().toLowerCase(Locale.ROOT));
        return config == null ? null : config.getConfigValue();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ConfigRequest request) {
        String key = normalizeKey(request.getConfigKey());
        if (configMapper.selectByKey(key) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "参数键已存在：" + key);
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigName(request.getConfigName().trim());
        config.setConfigValue(request.getConfigValue());
        config.setConfigType(request.getConfigType() == null ? 2 : request.getConfigType());
        config.setRemark(request.getRemark());
        configMapper.insert(config);
        log.info("系统参数已创建 key={}", key);
        return config.getId();
    }

    /**
     * 修改参数。
     *
     * <p><b>不接收 {@code configKey}</b>：与字典类型同理，
     * 参数键是程序引用的名字，改了就是静默断链。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ConfigRequest request) {
        SysConfig config = requireConfig(id);
        config.setConfigName(request.getConfigName().trim());
        config.setConfigValue(request.getConfigValue());
        config.setRemark(request.getRemark());
        configMapper.updateById(config);
        log.info("系统参数已修改 key={}", config.getConfigKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysConfig config = requireConfig(id);
        if (Integer.valueOf(TYPE_BUILTIN).equals(config.getConfigType())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "系统内置参数不允许删除");
        }
        configMapper.deleteById(id);
        log.info("系统参数已删除 key={}", config.getConfigKey());
    }

    /* ---------------- 内部方法 ---------------- */

    private String normalizeKey(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "参数键不能为空");
        }
        return key;
    }

    private SysConfig requireConfig(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "参数ID不能为空");
        }
        SysConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "参数不存在或已删除");
        }
        return config;
    }

    private ConfigVO toVO(SysConfig config) {
        ConfigVO vo = new ConfigVO();
        vo.setId(String.valueOf(config.getId()));
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(config.getConfigValue());
        vo.setConfigName(config.getConfigName());
        vo.setConfigType(config.getConfigType());
        vo.setRemark(config.getRemark());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }
}
