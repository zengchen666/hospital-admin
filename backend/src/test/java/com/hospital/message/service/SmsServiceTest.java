package com.hospital.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageQuery;
import com.hospital.common.api.PageResult;
import com.hospital.common.security.LoginUser;
import com.hospital.message.dto.SmsSendRequest;
import com.hospital.message.dto.SmsVO;
import com.hospital.message.entity.SysSmsRecord;
import com.hospital.message.mapper.SysSmsRecordMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信。
 *
 * <p>一期没接服务商，所以这里真正要验的是<b>落库行为</b>：
 * 发送失败也必须留一条记录 —— 只记成功的话，
 * 「谁在什么时候给谁发了什么」就断了，出纠纷无从自证。</p>
 */
@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    private static final Long RECORD_ID = 9901L;
    private static final Long USER_ID = 9001L;
    private static final String PHONE = "13800001111";

    @Mock
    private SysSmsRecordMapper smsMapper;

    private SmsService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysSmsRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new SmsService(smsMapper);
        loginAs(USER_ID);
        lenient().doAnswer(inv -> {
            SysSmsRecord entity = inv.getArgument(0);
            entity.setId(RECORD_ID);
            return 1;
        }).when(smsMapper).insert(any(SysSmsRecord.class));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("未接入服务商时按失败落库，但不抛异常（短信不该拖垮主业务）")
    void failStillRecorded() {
        SmsVO vo = service.send(request(PHONE, "SMS_001", "{\"code\":\"123456\"}"));

        ArgumentCaptor<SysSmsRecord> captor = ArgumentCaptor.forClass(SysSmsRecord.class);
        verify(smsMapper).insert(captor.capture());
        SysSmsRecord saved = captor.getValue();
        assertThat(saved.getSendStatus()).isEqualTo(SmsService.STATUS_FAIL);
        assertThat(saved.getFailReason()).isNotBlank();
        assertThat(saved.getPhone()).isEqualTo(PHONE);

        // 调用方通过返回值得知失败，而不是靠异常
        assertThat(vo.getSendStatus()).isEqualTo(SmsService.STATUS_FAIL);
    }

    @Test
    @DisplayName("返回的手机号必须脱敏（短信记录=可直接导出的联系方式）")
    void phoneMasked() {
        SmsVO vo = service.send(request(PHONE, "SMS_001", null));

        assertThat(vo.getPhone()).isEqualTo("138****1111");
    }

    @Test
    @DisplayName("未传模板参数时按 {} 记，不留空")
    void paramsDefault() {
        service.send(request(PHONE, "SMS_001", null));

        ArgumentCaptor<SysSmsRecord> captor = ArgumentCaptor.forClass(SysSmsRecord.class);
        verify(smsMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).contains("{}");
    }

    @Test
    @DisplayName("超长内容截断到 500，避免超列宽报错")
    void contentTruncated() {
        service.send(request(PHONE, "SMS_001", "x".repeat(600)));

        ArgumentCaptor<SysSmsRecord> captor = ArgumentCaptor.forClass(SysSmsRecord.class);
        verify(smsMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).hasSize(500);
    }

    @Test
    @DisplayName("列表按手机号关键字检索且不回传明文")
    void pageMasked() {
        when(smsMapper.selectPage(any(Page.class), any())).thenReturn(pageWith(record()));
        PageQuery query = new PageQuery();
        query.setKeyword("1380000");

        PageResult<SmsVO> result = service.page(query);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getPhone()).isEqualTo("138****1111");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysSmsRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(smsMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("phone");
    }

    /* ---------------- 辅助 ---------------- */

    private SmsSendRequest request(String phone, String templateCode, String params) {
        SmsSendRequest request = new SmsSendRequest();
        request.setPhone(phone);
        request.setTemplateCode(templateCode);
        request.setParams(params);
        return request;
    }

    private SysSmsRecord record() {
        SysSmsRecord entity = new SysSmsRecord();
        entity.setId(RECORD_ID);
        entity.setPhone(PHONE);
        entity.setTemplateCode("SMS_001");
        entity.setSendStatus(SmsService.STATUS_FAIL);
        entity.setFailReason("未接入短信服务商");
        return entity;
    }

    private Page<SysSmsRecord> pageWith(SysSmsRecord row) {
        Page<SysSmsRecord> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        return page;
    }

    private void loginAs(Long userId) {
        LoginUser user = LoginUser.builder().userId(userId).username("admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
