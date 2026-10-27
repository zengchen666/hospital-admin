package com.hospital.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageResult;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.LoginUser;
import com.hospital.message.dto.MessageQuery;
import com.hospital.message.dto.MessageVO;
import com.hospital.message.entity.SysMessage;
import com.hospital.message.mapper.SysMessageMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内消息。
 *
 * <p>要锁死的是越权防线：接收人只能从登录态取、
 * 标记已读的 SQL 必须带 receiver_id。这两条一旦破，
 * 改个 id 就能读/改别人的通知。</p>
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final Long USER_ID = 9001L;
    private static final Long OTHER_ID = 9002L;
    private static final Long MSG_ID = 7701L;

    @Mock
    private SysMessageMapper messageMapper;

    private MessageService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysMessage.class);
    }

    @BeforeEach
    void setUp() {
        service = new MessageService(messageMapper);
        loginAs(USER_ID);
        lenient().doAnswer(inv -> {
            SysMessage entity = inv.getArgument(0);
            entity.setId(MSG_ID);
            return 1;
        }).when(messageMapper).insert(any(SysMessage.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("接收人为空直接拒绝（40001）")
    void receiverRequired() {
        assertCode(() -> service.sendTo(null, "标题", "内容", 1, "stock_warn", 1L),
                ErrorCode.PARAM_INVALID);
        verify(messageMapper, never()).insert(any(SysMessage.class));
    }

    @Test
    @DisplayName("标题为空直接拒绝（40001）")
    void titleRequired() {
        assertCode(() -> service.sendTo(USER_ID, "  ", "内容", 1, null, null),
                ErrorCode.PARAM_INVALID);
        verify(messageMapper, never()).insert(any(SysMessage.class));
    }

    @Test
    @DisplayName("未指定类型时按系统消息落库，初始未读")
    void sendDefaults() {
        Long id = service.sendTo(USER_ID, " 库存不足 ", "布洛芬剩余 0", null, "stock_warn", 55L);

        assertThat(id).isEqualTo(MSG_ID);
        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(messageMapper).insert(captor.capture());
        SysMessage saved = captor.getValue();
        assertThat(saved.getMsgType()).isEqualTo(MessageService.TYPE_SYSTEM);
        assertThat(saved.getIsRead()).isZero();
        assertThat(saved.getTitle()).isEqualTo("库存不足");
        assertThat(saved.getReceiverId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("分页查询强制带上 receiver_id（只能看自己的）")
    void pageScopedToCurrentUser() {
        when(messageMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage());

        service.page(new MessageQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("receiver_id");
    }

    @Test
    @DisplayName("VO 里 ID 转字符串返回（雪花 ID 超 JS 安全整数）")
    void idAsStringInVO() {
        when(messageMapper.selectPage(any(Page.class), any())).thenReturn(pageWith(message(MSG_ID)));

        PageResult<MessageVO> result = service.page(new MessageQuery());

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getId()).isEqualTo(String.valueOf(MSG_ID));
    }

    @Test
    @DisplayName("标记别人的消息已读 → 40400，且不执行 UPDATE")
    void markReadOtherRejected() {
        when(messageMapper.selectById(MSG_ID)).thenReturn(message(OTHER_ID));

        assertCode(() -> service.markRead(MSG_ID), ErrorCode.NO_RESOURCE);
        verify(messageMapper, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("已读的消息重复标记 → 幂等成功，不重复 UPDATE")
    void markReadIdempotent() {
        SysMessage read = message(USER_ID);
        read.setIsRead(1);
        when(messageMapper.selectById(MSG_ID)).thenReturn(read);

        service.markRead(MSG_ID);

        verify(messageMapper, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("标记已读的 UPDATE 必须带 receiver_id（防越权）")
    void markReadCarriesReceiver() {
        when(messageMapper.selectById(MSG_ID)).thenReturn(message(USER_ID));
        when(messageMapper.markRead(MSG_ID, USER_ID)).thenReturn(1);

        service.markRead(MSG_ID);

        verify(messageMapper).markRead(eq(MSG_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("并发下 UPDATE 影响 0 行 → 40900")
    void markReadConflict() {
        when(messageMapper.selectById(MSG_ID)).thenReturn(message(USER_ID));
        when(messageMapper.markRead(MSG_ID, USER_ID)).thenReturn(0);

        assertCode(() -> service.markRead(MSG_ID), ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("全部已读只作用于当前用户")
    void markAllReadScoped() {
        when(messageMapper.markAllRead(USER_ID)).thenReturn(7);

        assertThat(service.markAllRead()).isEqualTo(7);
        verify(messageMapper).markAllRead(USER_ID);
    }

    /* ---------------- 辅助 ---------------- */

    private SysMessage message(Long receiverId) {
        SysMessage entity = new SysMessage();
        entity.setId(MSG_ID);
        entity.setReceiverId(receiverId);
        entity.setTitle("系统维护");
        entity.setContent("今晚 22:00 停机");
        entity.setMsgType(MessageService.TYPE_SYSTEM);
        entity.setIsRead(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private Page<SysMessage> emptyPage() {
        return (Page<SysMessage>) new Page<SysMessage>(1, 10);
    }

    @SuppressWarnings("unchecked")
    private Page<SysMessage> pageWith(SysMessage row) {
        Page<SysMessage> page = (Page<SysMessage>) new Page<SysMessage>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        return page;
    }

    private void assertCode(Runnable runnable, ErrorCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).as("期望抛出 %s", expected).isNotNull();
        assertThat(ex.getCode()).isEqualTo(expected.getCode());
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("u" + userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
