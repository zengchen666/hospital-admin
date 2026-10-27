package com.hospital.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.message.dto.NoticeQuery;
import com.hospital.message.dto.NoticeRequest;
import com.hospital.message.dto.NoticeVO;
import com.hospital.message.entity.SysNotice;
import com.hospital.message.mapper.SysNoticeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 公告服务。
 *
 * <p>状态机：<b>0 草稿 → 1 已发布</b>，单向不可逆。两条硬规则：</p>
 * <ol>
 *   <li><b>已发布不可修改</b> —— 公告是给全院/科室看的通知，发出去后悄悄改内容，
 *       看过旧版的人和新版对不上，出了问题扯不清。要改只能删掉重发一条；</li>
 *   <li><b>已发布不可删除</b> —— 同上，公告一旦发布就成了「当时确实通知过」的凭据。</li>
 * </ol>
 *
 * <p>可见范围：{@code notice_type=1} 院内（所有人可见）、
 * {@code notice_type=2} 科室（仅该科室可见）。查询侧一律强制 {@code status=1}，
 * 草稿只在管理端列表里出现。</p>
 */
@Slf4j
@Service
public class NoticeService {

    /** 1 院内 / 2 科室 */
    public static final int TYPE_HOSPITAL = 1;
    public static final int TYPE_DEPT = 2;

    /** 0 草稿 / 1 已发布 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;

    private static final int VISIBLE_LIMIT = 20;

    private final SysNoticeMapper noticeMapper;

    public NoticeService(SysNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * 新建公告（草稿）。
     *
     * <p>刻意不提供「一步发布」：公告内容要过一遍眼睛再发出去，
     * 建完直接发布等于把草稿和发布两个动作合成一个，误操作的代价是全院都收到。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(NoticeRequest request) {
        validate(request);
        SysNotice entity = new SysNotice();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        entity.setNoticeType(request.getNoticeType());
        entity.setDeptId(Integer.valueOf(TYPE_DEPT).equals(request.getNoticeType())
                ? request.getDeptId() : null);
        entity.setStatus(STATUS_DRAFT);
        entity.setCreateBy(CurrentUser.idOrNull());
        noticeMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NoticeRequest request) {
        validate(request);
        SysNotice entity = getExisting(id);
        if (!Integer.valueOf(STATUS_DRAFT).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已发布的公告不可修改，请删除后重新发布");
        }
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        entity.setNoticeType(request.getNoticeType());
        entity.setDeptId(Integer.valueOf(TYPE_DEPT).equals(request.getNoticeType())
                ? request.getDeptId() : null);
        entity.setUpdateBy(CurrentUser.idOrNull());
        noticeMapper.updateById(entity);
    }

    /**
     * 发布：0 草稿 → 1 已发布。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Long userId = CurrentUser.requireId();
        SysNotice entity = getExisting(id);
        if (Integer.valueOf(STATUS_PUBLISHED).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该公告已发布，请勿重复操作");
        }
        if (noticeMapper.publish(id, userId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "公告状态已变更，请刷新后重试");
        }
        log.info("[公告] 发布成功 id={}, publisher={}", id, userId);
    }

    /**
     * 删除（仅草稿）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysNotice entity = getExisting(id);
        if (Integer.valueOf(STATUS_PUBLISHED).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.STATUS_NOT_ALLOWED, "已发布的公告不可删除");
        }
        if (noticeMapper.deleteById(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "公告状态已变更，请刷新后重试");
        }
    }

    /** 管理端分页：含草稿，需要 message:notice:edit */
    public PageResult<NoticeVO> page(NoticeQuery query) {
        long total = noticeMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<NoticeVO> rows = noticeMapper.selectByQuery(query, offset, query.getPageSize());
        Page<NoticeVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        page.setTotal(total);
        page.setRecords(rows);
        return PageResult.of(page);
    }

    /**
     * 当前用户可见的公告（首页/消息中心用）。
     *
     * <p>科室公告按<b>当前用户所属科室</b>匹配，不接收前端传 deptId ——
     * 传了就能翻看别的科室的内部通知。</p>
     */
    public List<NoticeVO> visible() {
        return noticeMapper.selectVisible(CurrentUser.require().getDeptId(), VISIBLE_LIMIT);
    }

    /* ---------------- 内部 ---------------- */

    private void validate(NoticeRequest request) {
        if (Integer.valueOf(TYPE_DEPT).equals(request.getNoticeType())) {
            if (request.getDeptId() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "科室公告必须指定科室");
            }
            if (noticeMapper.countDept(request.getDeptId()) == 0) {
                throw new BusinessException(ErrorCode.NO_RESOURCE, "指定的科室不存在或已停用");
            }
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "公告内容不能为空");
        }
    }

    private SysNotice getExisting(Long id) {
        SysNotice entity = noticeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "公告不存在");
        }
        return entity;
    }
}
