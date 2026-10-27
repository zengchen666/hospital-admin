package com.hospital.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.system.dto.OperationLogQuery;
import com.hospital.system.dto.OperationLogVO;
import com.hospital.system.entity.SysOperationLog;
import com.hospital.system.mapper.SysOperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志查询。
 *
 * <p>审计表只追加：<b>没有删除接口，也没有修改接口</b>。
 * 一条能被删掉的审计日志等于没有审计 —— 出事之后第一件事就是看
 * 「是谁删了日志」，而这个问题本身已经没有答案了。</p>
 *
 * <p>导出单独做，不复用分页：审计导出一次可能几万行，
 * 走分页接口要翻上百次页，中间还会有数据漂移。</p>
 */
@Slf4j
@Service
public class OperationLogService {

    /** 单次导出上限：审计导出不是数据迁移，超了说明该换查询条件而不是放宽上限 */
    public static final int EXPORT_MAX = 5000;

    private final SysOperationLogMapper logMapper;

    public OperationLogService(SysOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public PageResult<OperationLogVO> page(OperationLogQuery query) {
        IPage<SysOperationLog> page = logMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query));
        return PageResult.of(page, OperationLogService::toVO);
    }

    /** 导出用：按条件取最近 N 条 */
    public List<OperationLogVO> export(OperationLogQuery query) {
        Page<SysOperationLog> page = new Page<>(1, EXPORT_MAX);
        IPage<SysOperationLog> result = logMapper.selectPage(page, buildWrapper(query));
        return result.getRecords().stream().map(OperationLogService::toVO).toList();
    }

    /* ---------------- 内部方法 ---------------- */

    private LambdaQueryWrapper<SysOperationLog> buildWrapper(OperationLogQuery query) {
        return new LambdaQueryWrapper<SysOperationLog>()
                .eq(StringUtils.hasText(query.getUsername()), SysOperationLog::getUsername, query.getUsername())
                .eq(StringUtils.hasText(query.getModule()), SysOperationLog::getModule, query.getModule())
                .like(StringUtils.hasText(query.getOperation()), SysOperationLog::getOperation, query.getOperation())
                .like(StringUtils.hasText(query.getRequestUri()), SysOperationLog::getRequestUri, query.getRequestUri())
                .eq(query.getStatus() != null, SysOperationLog::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, SysOperationLog::getCreateTime,
                        query.getStartTime() == null ? null : query.getStartTime().atStartOfDay())
                .lt(query.getEndTime() != null, SysOperationLog::getCreateTime,
                        query.getEndTime() == null ? null : query.getEndTime().plusDays(1).atStartOfDay())
                // 审计查询永远按时间倒序：出问题的人关心的是「最近谁动了」
                .orderByDesc(SysOperationLog::getCreateTime)
                .orderByDesc(SysOperationLog::getId);
    }

    private static OperationLogVO toVO(SysOperationLog entity) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setUserId(entity.getUserId() == null ? null : String.valueOf(entity.getUserId()));
        vo.setUsername(entity.getUsername());
        vo.setModule(entity.getModule());
        vo.setOperation(entity.getOperation());
        vo.setMethod(entity.getMethod());
        vo.setRequestUri(entity.getRequestUri());
        vo.setParams(entity.getParams());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCostTime(entity.getCostTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
