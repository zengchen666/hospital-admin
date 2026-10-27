package com.hospital.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.system.dto.DictItemRequest;
import com.hospital.system.dto.DictItemVO;
import com.hospital.system.dto.DictQuery;
import com.hospital.system.dto.DictRequest;
import com.hospital.system.dto.DictVO;
import com.hospital.system.entity.SysDict;
import com.hospital.system.entity.SysDictItem;
import com.hospital.system.mapper.SysDictItemMapper;
import com.hospital.system.mapper.SysDictMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 数据字典服务。
 *
 * <p>三条硬规则：</p>
 * <ol>
 *   <li><b>字典类型不能改</b>。业务代码按 {@code dictType} 取字典，
 *       改了等于把所有引用一次性弄断，而编译期不会有任何提示；</li>
 *   <li><b>字典项取值在同一字典内唯一</b>。重复取值会让下拉框出现两个选项，
 *       存进库里却是同一个东西；</li>
 *   <li><b>删字典必须连带删字典项</b>。只删字典会留下一堆孤儿字典项，
 *       下次新建一个同名 type 的字典，会莫名其妙继承上一次的选项。</li>
 * </ol>
 */
@Slf4j
@Service
public class SysDictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper itemMapper;

    public SysDictService(SysDictMapper dictMapper, SysDictItemMapper itemMapper) {
        this.dictMapper = dictMapper;
        this.itemMapper = itemMapper;
    }

    public List<DictVO> list(DictQuery query) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<SysDict>()
                .like(StringUtils.hasText(query.getDictName()), SysDict::getDictName, query.getDictName())
                .like(StringUtils.hasText(query.getDictType()), SysDict::getDictType, query.getDictType())
                .eq(query.getStatus() != null, SysDict::getStatus, query.getStatus())
                .orderByAsc(SysDict::getDictType);
        return dictMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public DictVO detail(Long id) {
        return toVO(requireDict(id));
    }

    /** 按类型取字典项，供下拉框使用 */
    public List<DictItemVO> items(String dictType, boolean onlyEnabled) {
        if (!StringUtils.hasText(dictType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典类型不能为空");
        }
        List<SysDictItem> items = itemMapper.selectByType(dictType.trim().toLowerCase(Locale.ROOT));
        return items.stream()
                .filter(item -> !onlyEnabled || Integer.valueOf(1).equals(item.getStatus()))
                .map(this::toItemVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(DictRequest request) {
        String type = normalizeType(request.getDictType());
        if (dictMapper.selectByType(type) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "字典类型已存在：" + type);
        }
        SysDict dict = new SysDict();
        dict.setDictType(type);
        dict.setDictName(request.getDictName().trim());
        dict.setRemark(request.getRemark());
        dict.setStatus(request.getStatus());
        dictMapper.insert(dict);
        log.info("字典已创建 type={}", type);
        return dict.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DictRequest request) {
        SysDict dict = requireDict(id);
        // 只允许改名称、备注、状态：dictType 是业务代码的引用键，改了就是全站断链
        dict.setDictName(request.getDictName().trim());
        dict.setRemark(request.getRemark());
        dict.setStatus(request.getStatus());
        dictMapper.updateById(dict);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDict dict = requireDict(id);
        dictMapper.deleteById(id);
        // 连带清掉字典项：留下孤儿会污染将来同名 type 的新字典
        itemMapper.delete(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, dict.getDictType()));
        log.info("字典已删除 type={}", dict.getDictType());
    }

    /* ---------------- 字典项 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public Long createItem(Long dictId, DictItemRequest request) {
        SysDict dict = requireDict(dictId);
        checkDuplicate(dict.getDictType(), request.getItemValue(), null);

        SysDictItem item = new SysDictItem();
        item.setDictType(dict.getDictType());
        item.setItemLabel(request.getItemLabel().trim());
        item.setItemValue(request.getItemValue().trim());
        item.setSort(request.getSort() == null ? nextSort(dict.getDictType()) : request.getSort());
        item.setCssClass(request.getCssClass());
        item.setStatus(request.getStatus());
        itemMapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long dictId, Long itemId, DictItemRequest request) {
        SysDict dict = requireDict(dictId);
        SysDictItem item = requireItem(itemId);
        if (!dict.getDictType().equals(item.getDictType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典项不属于该字典");
        }
        checkDuplicate(dict.getDictType(), request.getItemValue(), itemId);

        item.setItemLabel(request.getItemLabel().trim());
        item.setItemValue(request.getItemValue().trim());
        item.setSort(request.getSort() == null ? item.getSort() : request.getSort());
        item.setCssClass(request.getCssClass());
        item.setStatus(request.getStatus());
        itemMapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long dictId, Long itemId) {
        SysDict dict = requireDict(dictId);
        SysDictItem item = requireItem(itemId);
        if (!dict.getDictType().equals(item.getDictType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典项不属于该字典");
        }
        itemMapper.deleteById(itemId);
    }

    /* ---------------- 内部方法 ---------------- */

    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (type.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典类型不能为空");
        }
        return type;
    }

    private void checkDuplicate(String dictType, String itemValue, Long excludeId) {
        if (itemMapper.countDuplicate(dictType, itemValue == null ? "" : itemValue.trim(), excludeId) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA,
                    "该字典下已存在取值：" + itemValue);
        }
    }

    private int nextSort(String dictType) {
        List<SysDictItem> items = itemMapper.selectByType(dictType);
        int max = items.stream()
                .map(SysDictItem::getSort)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        return max + 1;
    }

    private SysDict requireDict(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典ID不能为空");
        }
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "字典不存在或已删除");
        }
        return dict;
    }

    private SysDictItem requireItem(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "字典项ID不能为空");
        }
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "字典项不存在或已删除");
        }
        return item;
    }

    private DictVO toVO(SysDict dict) {
        DictVO vo = new DictVO();
        vo.setId(String.valueOf(dict.getId()));
        vo.setDictType(dict.getDictType());
        vo.setDictName(dict.getDictName());
        vo.setRemark(dict.getRemark());
        vo.setStatus(dict.getStatus());
        vo.setItems(itemMapper.selectByType(dict.getDictType()).stream().map(this::toItemVO).toList());
        vo.setCreateTime(dict.getCreateTime());
        return vo;
    }

    private DictItemVO toItemVO(SysDictItem item) {
        DictItemVO vo = new DictItemVO();
        vo.setId(String.valueOf(item.getId()));
        vo.setDictType(item.getDictType());
        vo.setItemLabel(item.getItemLabel());
        vo.setItemValue(item.getItemValue());
        vo.setSort(item.getSort());
        vo.setCssClass(item.getCssClass());
        vo.setStatus(item.getStatus());
        return vo;
    }
}
