package com.hospital.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.message.dto.NoticeQuery;
import com.hospital.message.dto.NoticeRequest;
import com.hospital.message.dto.NoticeVO;
import com.hospital.message.entity.SysNotice;
import com.hospital.message.mapper.SysNoticeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告。
 *
 * <p>要锁死的是那条单向状态机：<b>草稿可改可删，已发布既不能改也不能删</b>。
 * 发出去的公告再悄悄改内容，看过旧版的人和新版对不上，出了问题扯不清。</p>
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final Long NOTICE_ID = 8801L;
    private static final Long DEPT_ID = 3001L;
    private static final Long USER_ID = 9001L;
    private static final Long USER_DEPT_ID = 3002L;

    @Mock
    private SysNoticeMapper noticeMapper;

    private NoticeService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysNotice.class);
    }

    @BeforeEach
    void setUp() {
        service = new NoticeService(noticeMapper);
        loginAs(USER_ID, USER_DEPT_ID);
        lenient().doAnswer(inv -> {
            SysNotice entity = inv.getArgument(0);
            entity.setId(NOTICE_ID);
            return 1;
        }).when(noticeMapper).insert(any(SysNotice.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("科室公告必须指定科室（40001）")
    void deptRequired() {
        NoticeRequest request = request(NoticeService.TYPE_DEPT, null, "内科排班调整");

        assertCode(() -> service.create(request), ErrorCode.PARAM_INVALID);
        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("科室公告指向不存在的科室 → 40400（否则静默无人可见）")
    void deptMustExist() {
        when(noticeMapper.countDept(DEPT_ID)).thenReturn(0);

        assertCode(() -> service.create(request(NoticeService.TYPE_DEPT, DEPT_ID, "内科排班调整")),
                ErrorCode.NO_RESOURCE);
        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("院内公告不校验科室，且 dept_id 一律清空")
    void hospitalNoticeClearsDept() {
        Long id = service.create(request(NoticeService.TYPE_HOSPITAL, DEPT_ID, "春节放假通知"));

        assertThat(id).isEqualTo(NOTICE_ID);
        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        SysNotice saved = captor.getValue();
        assertThat(saved.getDeptId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(NoticeService.STATUS_DRAFT);
        // 院内公告不该去查科室存在性
        verify(noticeMapper, never()).countDept(any());
    }

    @Test
    @DisplayName("公告内容不能为空（40001）")
    void contentRequired() {
        NoticeRequest request = request(NoticeService.TYPE_HOSPITAL, null, "标题");
        request.setContent("  ");

        assertCode(() -> service.create(request), ErrorCode.PARAM_INVALID);
        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("新建一律是草稿，不能直接发出去")
    void createAlwaysDraft() {
        when(noticeMapper.countDept(DEPT_ID)).thenReturn(1);

        service.create(request(NoticeService.TYPE_DEPT, DEPT_ID, "内科排班调整"));

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NoticeService.STATUS_DRAFT);
        assertThat(captor.getValue().getPublishTime()).isNull();
    }

    @Test
    @DisplayName("已发布的公告不可修改（40902）")
    void publishedCannotUpdate() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(published());

        assertCode(() -> service.update(NOTICE_ID, request(NoticeService.TYPE_HOSPITAL, null, "改后的标题")),
                ErrorCode.STATUS_NOT_ALLOWED);
        verify(noticeMapper, never()).updateById(any(SysNotice.class));
    }

    @Test
    @DisplayName("草稿可以修改")
    void draftCanUpdate() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(draft());

        service.update(NOTICE_ID, request(NoticeService.TYPE_HOSPITAL, null, "改后的标题"));

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("改后的标题");
    }

    @Test
    @DisplayName("已发布的公告不可删除（40902）")
    void publishedCannotDelete() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(published());

        assertCode(() -> service.remove(NOTICE_ID), ErrorCode.STATUS_NOT_ALLOWED);
        verify(noticeMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("草稿可以删除")
    void draftCanDelete() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(draft());
        when(noticeMapper.deleteById(NOTICE_ID)).thenReturn(1);

        service.remove(NOTICE_ID);

        verify(noticeMapper).deleteById(NOTICE_ID);
    }

    @Test
    @DisplayName("重复发布 → 40900，SQL 不带原状态条件就会被覆盖")
    void republishRejected() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(published());

        assertCode(() -> service.publish(NOTICE_ID), ErrorCode.CONFLICT);
        verify(noticeMapper, never()).publish(any(), any());
    }

    @Test
    @DisplayName("发布时 UPDATE 影响 0 行（并发）→ 40900")
    void publishConflict() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(draft());
        when(noticeMapper.publish(NOTICE_ID, USER_ID)).thenReturn(0);

        assertCode(() -> service.publish(NOTICE_ID), ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("发布成功时记下发布人")
    void publishOk() {
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(draft());
        when(noticeMapper.publish(NOTICE_ID, USER_ID)).thenReturn(1);

        service.publish(NOTICE_ID);

        verify(noticeMapper).publish(NOTICE_ID, USER_ID);
    }

    @Test
    @DisplayName("可见公告按当前用户科室匹配，不接受前端传值")
    void visibleScopedByUserDept() {
        NoticeVO vo = NoticeVO.builder().id("1").title("公告").build();
        when(noticeMapper.selectVisible(USER_DEPT_ID, 20)).thenReturn(java.util.List.of(vo));

        assertThat(service.visible()).hasSize(1);
        verify(noticeMapper).selectVisible(USER_DEPT_ID, 20);
    }

    @Test
    @DisplayName("分页无数据时直接返回空页，不再查列表")
    void pageEmptyShortCircuit() {
        NoticeQuery query = new NoticeQuery();
        when(noticeMapper.countByQuery(query)).thenReturn(0L);

        PageResult<NoticeVO> result = service.page(query);

        assertThat(result.getTotal()).isZero();
        verify(noticeMapper, never()).selectByQuery(any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    /* ---------------- 辅助 ---------------- */

    private NoticeRequest request(Integer noticeType, Long deptId, String title) {
        NoticeRequest request = new NoticeRequest();
        request.setTitle(title);
        request.setContent("正文内容");
        request.setNoticeType(noticeType);
        request.setDeptId(deptId);
        return request;
    }

    private SysNotice draft() {
        SysNotice entity = new SysNotice();
        entity.setId(NOTICE_ID);
        entity.setTitle("草稿公告");
        entity.setStatus(NoticeService.STATUS_DRAFT);
        return entity;
    }

    private SysNotice published() {
        SysNotice entity = draft();
        entity.setStatus(NoticeService.STATUS_PUBLISHED);
        return entity;
    }

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId, Long deptId) {
        LoginUser user = LoginUser.builder().userId(userId).username("admin").deptId(deptId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
