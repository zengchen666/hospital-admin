package com.hospital.system.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.system.dto.ConfigQuery;
import com.hospital.system.dto.ConfigRequest;
import com.hospital.system.entity.SysConfig;
import com.hospital.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统参数：键不可改、内置不可删、改完立即生效（不缓存）。
 *
 * <p>「改完立即生效」是这条测试里唯一一个<b>没有断言</b>的用例 ——
 * 它验证的是「没有缓存层」这件事本身，能做的只有确认每次取值都真的走了
 * 一次数据库查询。缓存一旦被加回来，这个用例会立刻变红。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysConfigServiceTest {

    @Mock
    private SysConfigMapper configMapper;

    private SysConfigService service;

    @BeforeEach
    void setUp() {
        service = new SysConfigService(configMapper);
    }

    private SysConfig config(Long id, String key, String value, int type) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigName("登录失败上限");
        config.setConfigType(type);
        return config;
    }

    private ConfigRequest request(String key, String value) {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey(key);
        request.setConfigName("登录失败上限");
        request.setConfigValue(value);
        request.setConfigType(2);
        return request;
    }

    @Test
    @DisplayName("新建参数时键归一化为小写")
    void createNormalizesKey() {
        when(configMapper.selectByKey("login.max_attempts")).thenReturn(null);

        service.create(request("Login.Max_Attempts", "5"));

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo("login.max_attempts");
    }

    @Test
    @DisplayName("参数键重复时 40904，不落库")
    void duplicateKeyIsRejected() {
        when(configMapper.selectByKey("login.max_attempts"))
                .thenReturn(config(7001L, "login.max_attempts", "5", 1));

        assertThatThrownBy(() -> service.create(request("login.max_attempts", "6")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数键已存在");

        verify(configMapper, never()).insert(any(SysConfig.class));
    }

    @Test
    @DisplayName("参数键为空时 40001")
    void blankKeyIsRejected() {
        assertThatThrownBy(() -> service.create(request("  ", "5")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数键不能为空");
    }

    @Test
    @DisplayName("修改参数不允许改键与类型，只改值")
    void updateKeepsKeyAndType() {
        when(configMapper.selectById(7001L)).thenReturn(config(7001L, "login.max_attempts", "5", 1));

        ConfigRequest request = request("another.key", "8");
        request.setConfigType(2);
        service.update(7001L, request);

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo("login.max_attempts");
        assertThat(captor.getValue().getConfigType()).isEqualTo(1);
        assertThat(captor.getValue().getConfigValue()).isEqualTo("8");
    }

    @Test
    @DisplayName("系统内置参数不允许删除（删了系统下次起不来）")
    void builtinConfigCannotBeDeleted() {
        when(configMapper.selectById(7001L)).thenReturn(config(7001L, "login.max_attempts", "5", 1));

        assertThatThrownBy(() -> service.delete(7001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统内置参数不允许删除");

        verify(configMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("业务参数可以删除")
    void businessConfigCanBeDeleted() {
        when(configMapper.selectById(7002L)).thenReturn(config(7002L, "work.start_time", "08:00", 2));

        service.delete(7002L);

        verify(configMapper).deleteById(7002L);
    }

    @Test
    @DisplayName("取值不缓存：两次查询都真的落到数据库")
    void valueIsNotCached() {
        when(configMapper.selectByKey("login.max_attempts"))
                .thenReturn(config(7001L, "login.max_attempts", "5", 1));

        assertThat(service.value("login.max_attempts")).isEqualTo("5");
        assertThat(service.value("login.max_attempts")).isEqualTo("5");

        // 若将来加了缓存，这里会变成 1 次 —— 用例随即失败并提示「改完不生效」的回归
        verify(configMapper, org.mockito.Mockito.times(2)).selectByKey("login.max_attempts");
    }

    @Test
    @DisplayName("未配置的参数返回 null，而不是抛异常")
    void missingValueIsNull() {
        when(configMapper.selectByKey("nosuch.key")).thenReturn(null);

        assertThat(service.value("nosuch.key")).isNull();
    }

    @Test
    @DisplayName("列表可按类型过滤出安全类参数")
    void listFiltersByType() {
        when(configMapper.selectList(any()))
                .thenReturn(List.of(config(7001L, "login.max_attempts", "5", 1)));

        ConfigQuery query = new ConfigQuery();
        query.setConfigType(1);
        assertThat(service.list(query)).hasSize(1);
        verify(configMapper).selectList(any());
    }

    @Test
    @DisplayName("删除不存在的参数报 40400")
    void missingConfigIsNotFound() {
        when(configMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不存在或已删除");
    }
}
