package com.hospital.system.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.system.dto.DictItemRequest;
import com.hospital.system.dto.DictQuery;
import com.hospital.system.dto.DictRequest;
import com.hospital.system.dto.DictVO;
import com.hospital.system.entity.SysDict;
import com.hospital.system.entity.SysDictItem;
import com.hospital.system.mapper.SysDictItemMapper;
import com.hospital.system.mapper.SysDictMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据字典：类型不可改、取值不可重复、删字典不留孤儿。
 *
 * <p>这三条是字典最容易坏的地方，而且坏法都很隐蔽 ——
 * 改类型不会报错只会静默失效，重复取值会让下拉框出现两个一样的选项，
 * 删字典留下的孤儿会被将来同名的新字典「继承」。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysDictServiceTest {

    @Mock
    private SysDictMapper dictMapper;
    @Mock
    private SysDictItemMapper itemMapper;

    private SysDictService service;

    @BeforeEach
    void setUp() {
        service = new SysDictService(dictMapper, itemMapper);
    }

    private SysDict dict(Long id, String type) {
        SysDict dict = new SysDict();
        dict.setId(id);
        dict.setDictType(type);
        dict.setDictName("性别");
        dict.setStatus(1);
        return dict;
    }

    private DictRequest request(String type, String name) {
        DictRequest request = new DictRequest();
        request.setDictType(type);
        request.setDictName(name);
        request.setStatus(1);
        return request;
    }

    private DictItemRequest item(String label, String value) {
        DictItemRequest request = new DictItemRequest();
        request.setItemLabel(label);
        request.setItemValue(value);
        request.setStatus(1);
        return request;
    }

    @Test
    @DisplayName("新建字典时类型归一化为小写")
    void createNormalizesType() {
        when(dictMapper.selectByType("gender")).thenReturn(null);

        service.create(request("Gender", "性别"));

        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(dictMapper).insert(captor.capture());
        assertThat(captor.getValue().getDictType()).isEqualTo("gender");
    }

    @Test
    @DisplayName("字典类型重复时 40904，不落库")
    void duplicateTypeIsRejected() {
        when(dictMapper.selectByType("gender")).thenReturn(dict(6001L, "gender"));

        assertThatThrownBy(() -> service.create(request("gender", "性别")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典类型已存在");

        verify(dictMapper, never()).insert(any(SysDict.class));
    }

    @Test
    @DisplayName("字典类型为空时 40001")
    void blankTypeIsRejected() {
        assertThatThrownBy(() -> service.create(request("   ", "性别")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典类型不能为空");
    }

    @Test
    @DisplayName("修改字典不允许改类型，只改名称与状态")
    void updateKeepsTypeUnchanged() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));

        DictRequest request = request("pay_type", "性别（改）");
        service.update(6001L, request);

        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(dictMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDictType()).isEqualTo("gender");
        assertThat(captor.getValue().getDictName()).isEqualTo("性别（改）");
    }

    @Test
    @DisplayName("删除字典连带删除字典项，不留孤儿")
    void deleteCascadesItems() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));

        service.delete(6001L);

        verify(dictMapper).deleteById(6001L);
        verify(itemMapper).delete(any());
    }

    @Test
    @DisplayName("字典详情带出该类型的全部字典项")
    void detailLoadsItems() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        SysDictItem item = new SysDictItem();
        item.setId(6101L);
        item.setDictType("gender");
        item.setItemLabel("男");
        item.setItemValue("1");
        when(itemMapper.selectByType("gender")).thenReturn(List.of(item));

        DictVO vo = service.detail(6001L);

        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getItemValue()).isEqualTo("1");
    }

    @Test
    @DisplayName("同一字典下取值重复时 40904")
    void duplicateItemValueIsRejected() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        when(itemMapper.countDuplicate("gender", "1", null)).thenReturn(1);

        assertThatThrownBy(() -> service.createItem(6001L, item("男", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在取值");

        verify(itemMapper, never()).insert(any(SysDictItem.class));
    }

    @Test
    @DisplayName("修改字典项时排除自身，不算重复")
    void updateItemExcludesSelf() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        SysDictItem existing = new SysDictItem();
        existing.setId(6101L);
        existing.setDictType("gender");
        existing.setItemValue("1");
        when(itemMapper.selectById(6101L)).thenReturn(existing);
        when(itemMapper.countDuplicate("gender", "1", 6101L)).thenReturn(0);

        service.updateItem(6001L, 6101L, item("男性", "1"));

        verify(itemMapper).updateById(any(SysDictItem.class));
    }

    @Test
    @DisplayName("字典项不属于该字典时拒绝修改（防越权改别的字典）")
    void itemFromOtherDictIsRejected() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        SysDictItem foreign = new SysDictItem();
        foreign.setId(6101L);
        foreign.setDictType("pay_type");
        when(itemMapper.selectById(6101L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.updateItem(6001L, 6101L, item("男", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典项不属于该字典");

        verify(itemMapper, never()).updateById(any(SysDictItem.class));
    }

    @Test
    @DisplayName("下拉框只返回启用的字典项")
    void onlyEnabledItemsForDropdown() {
        SysDictItem enabled = new SysDictItem();
        enabled.setItemLabel("男");
        enabled.setItemValue("1");
        enabled.setStatus(1);
        SysDictItem disabled = new SysDictItem();
        disabled.setItemLabel("未知");
        disabled.setItemValue("0");
        disabled.setStatus(0);
        when(itemMapper.selectByType("gender")).thenReturn(List.of(enabled, disabled));

        assertThat(service.items("gender", true)).hasSize(1);
        assertThat(service.items("gender", false)).hasSize(2);
    }

    @Test
    @DisplayName("字典类型为空时取字典项直接 40001")
    void blankTypeCannotLoadItems() {
        assertThatThrownBy(() -> service.items("  ", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典类型不能为空");
    }

    @Test
    @DisplayName("列表按名称模糊过滤")
    void listFiltersByName() {
        when(dictMapper.selectList(any())).thenReturn(List.of(dict(6001L, "gender")));

        DictQuery query = new DictQuery();
        query.setDictName("性");
        List<DictVO> result = service.list(query);

        assertThat(result).hasSize(1);
        verify(dictMapper).selectList(any());
    }

    @Test
    @DisplayName("新增字典项未指定排序时排到末尾")
    void itemSortAppendsByDefault() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        when(itemMapper.countDuplicate(eq("gender"), any(), isNull())).thenReturn(0);
        SysDictItem last = new SysDictItem();
        last.setSort(3);
        when(itemMapper.selectByType("gender")).thenReturn(List.of(last));

        service.createItem(6001L, item("未知", "0"));

        ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(4);
    }

    @Test
    @DisplayName("删除不存在的字典报 40400 而不是空指针")
    void missingDictIsNotFound() {
        when(dictMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典不存在或已删除");
    }

    @Test
    @DisplayName("删除不存在的字典项报 40400")
    void missingItemIsNotFound() {
        when(dictMapper.selectById(6001L)).thenReturn(dict(6001L, "gender"));
        when(itemMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteItem(6001L, 9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典项不存在或已删除");

        verify(itemMapper, never()).deleteById(anyLong());
    }
}
