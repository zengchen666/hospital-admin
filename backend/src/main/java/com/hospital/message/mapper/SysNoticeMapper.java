package com.hospital.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.message.dto.NoticeQuery;
import com.hospital.message.dto.NoticeVO;
import com.hospital.message.entity.SysNotice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 发布：0 草稿 → 1 已发布，带原状态条件防重复发布。
     *
     * @return 1 成功；0 表示已发布过
     */
    @Update("""
            UPDATE sys_notice
               SET status = 1,
                   publisher_id = COALESCE(#{publisherId}, publisher_id),
                   publish_time = NOW(),
                   update_by = COALESCE(#{publisherId}, update_by),
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 0
               AND deleted = 0
            """)
    int publish(@Param("id") Long id, @Param("publisherId") Long publisherId);

    /**
     * 可见公告：院内公告（notice_type=1）+ 本科室公告（notice_type=2 且 dept_id 匹配）。
     *
     * <p>只返回<b>已发布</b>的：草稿不该被别人看到 ——
     * 写了一半的放假通知被提前看到，比没有通知更麻烦。</p>
     */
    @Select("""
            SELECT CAST(n.id AS CHAR)          AS id,
                   n.title                     AS title,
                   n.content                   AS content,
                   n.notice_type               AS notice_type,
                   CASE n.notice_type WHEN 1 THEN '院内' WHEN 2 THEN '科室' ELSE '未知' END
                                                AS notice_type_name,
                   CAST(n.dept_id AS CHAR)     AS dept_id,
                   d.dept_name                 AS dept_name,
                   CAST(n.publisher_id AS CHAR) AS publisher_id,
                   u.real_name                 AS publisher_name,
                   n.publish_time              AS publish_time,
                   n.status                    AS status,
                   n.create_time               AS create_time
              FROM sys_notice n
              LEFT JOIN sys_dept d ON d.id = n.dept_id AND d.deleted = 0
              LEFT JOIN sys_user u ON u.id = n.publisher_id AND u.deleted = 0
             WHERE n.deleted = 0
               AND n.status = 1
               AND (n.notice_type = 1 OR (n.notice_type = 2 AND n.dept_id = #{deptId}))
             ORDER BY n.publish_time DESC, n.id DESC
             LIMIT #{limit}
            """)
    List<NoticeVO> selectVisible(@Param("deptId") Long deptId, @Param("limit") int limit);

    /**
     * 管理端分页统计（含草稿）。
     *
     * <p>与 {@link #selectByQuery} 的条件必须逐条对齐 ——
     * 两处口径不一致会出现「总数 12 但列表只有 8 条」，极难排查。</p>
     */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM sys_notice n
             WHERE n.deleted = 0
            <if test="q.noticeType != null"> AND n.notice_type = #{q.noticeType}</if>
            <if test="q.status != null"> AND n.status = #{q.status}</if>
            <if test="q.keyword != null and q.keyword != ''">
                AND n.title LIKE CONCAT('%', #{q.keyword}, '%')
            </if>
            </script>
            """)
    long countByQuery(@Param("q") NoticeQuery query);

    /**
     * 管理端分页列表（含草稿，联查科室名与发布人）。
     *
     * <p>排序用 {@code COALESCE(publish_time, create_time)}：草稿没有发布时间，
     * 直接按 publish_time 排会被 MySQL 排到末尾（NULL 最小），
     * 结果刚建的草稿沉在最下面，写完一转身就找不着。</p>
     */
    @Select("""
            <script>
            SELECT CAST(n.id AS CHAR)          AS id,
                   n.title                     AS title,
                   n.content                   AS content,
                   n.notice_type               AS notice_type,
                   CASE n.notice_type WHEN 1 THEN '院内' WHEN 2 THEN '科室' ELSE '未知' END
                                                AS notice_type_name,
                   CAST(n.dept_id AS CHAR)     AS dept_id,
                   d.dept_name                 AS dept_name,
                   CAST(n.publisher_id AS CHAR) AS publisher_id,
                   u.real_name                 AS publisher_name,
                   n.publish_time              AS publish_time,
                   n.status                    AS status,
                   n.create_time               AS create_time
              FROM sys_notice n
              LEFT JOIN sys_dept d ON d.id = n.dept_id AND d.deleted = 0
              LEFT JOIN sys_user u ON u.id = n.publisher_id AND u.deleted = 0
             WHERE n.deleted = 0
            <if test="q.noticeType != null"> AND n.notice_type = #{q.noticeType}</if>
            <if test="q.status != null"> AND n.status = #{q.status}</if>
            <if test="q.keyword != null and q.keyword != ''">
                AND n.title LIKE CONCAT('%', #{q.keyword}, '%')
            </if>
             ORDER BY COALESCE(n.publish_time, n.create_time) DESC, n.id DESC
             LIMIT #{offset}, #{size}
            </script>
            """)
    List<NoticeVO> selectByQuery(@Param("q") NoticeQuery query,
                                 @Param("offset") long offset,
                                 @Param("size") long size);

    /**
     * 科室是否存在。
     *
     * <p>科室公告必须校验科室真实存在：指向一个不存在的科室，
     * 公告能发成功、列表里也看得见，但全院没有一个人会收到 ——
     * 这种「静默失效」最难被发现。</p>
     */
    @Select("""
            SELECT COUNT(1) FROM sys_dept
             WHERE id = #{deptId} AND deleted = 0 AND status = 1
            """)
    int countDept(@Param("deptId") Long deptId);
}
