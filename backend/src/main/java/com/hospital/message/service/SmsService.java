package com.hospital.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.api.PageQuery;
import com.hospital.common.api.PageResult;
import com.hospital.common.security.CurrentUser;
import com.hospital.common.util.MaskUtil;
import com.hospital.message.dto.SmsSendRequest;
import com.hospital.message.dto.SmsVO;
import com.hospital.message.entity.SysSmsRecord;
import com.hospital.message.mapper.SysSmsRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 短信服务。
 *
 * <p>一期<b>未接入短信服务商</b>，发送固定失败。但接口形态、落库行为、
 * 权限控制都按真实接入写好，接入时只需替换 {@link #doSend} 的实现 ——
 * 调用方（复诊提醒、危急值通知）现在就能按最终形态联调。</p>
 *
 * <p><b>无论成败都要落一条记录</b>：这条记录回答的是
 * 「谁在什么时候给哪个手机号发了什么」，是合规凭证，不是调试日志。
 * 只记成功的话，一旦出现投诉或纠纷，根本无从自证。</p>
 *
 * <p><b>发送失败不抛异常</b>：短信是主业务的副产品，
 * 患者不能因为短信服务商抖动就挂不上号。失败原因写进 {@code failReason}，
 * 由返回值的 {@code sendStatus} 告知调用方。</p>
 */
@Slf4j
@Service
public class SmsService {

    /** 0 失败 / 1 成功 */
    public static final int STATUS_FAIL = 0;
    public static final int STATUS_SUCCESS = 1;

    private static final String NOT_CONNECTED = "未接入短信服务商";
    private static final int CONTENT_MAX = 500;

    private final SysSmsRecordMapper smsMapper;

    public SmsService(SysSmsRecordMapper smsMapper) {
        this.smsMapper = smsMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public SmsVO send(SmsSendRequest request) {
        CurrentUser.require();
        SendResult result = doSend(request);

        SysSmsRecord record = new SysSmsRecord();
        record.setPhone(request.getPhone());
        record.setTemplateCode(request.getTemplateCode());
        record.setContent(renderContent(request));
        record.setSendStatus(result.success() ? STATUS_SUCCESS : STATUS_FAIL);
        record.setFailReason(result.failReason());
        record.setSendTime(LocalDateTime.now());
        smsMapper.insert(record);

        if (!result.success()) {
            log.warn("[短信] 发送失败 id={}, template={}, reason={}",
                    record.getId(), request.getTemplateCode(), result.failReason());
        }
        return toVO(record);
    }

    public PageResult<SmsVO> page(PageQuery query) {
        // ⚠️ 必须先取值再判空：wrapper.like(condition, column, value) 的 value 是
        // 普通方法入参，Java 会在调用前就求值 —— condition 为 false 也照样执行
        // query.getKeyword().trim()，关键字没传时直接 NPE。
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        LambdaQueryWrapper<SysSmsRecord> wrapper = Wrappers.<SysSmsRecord>lambdaQuery()
                .like(keyword != null, SysSmsRecord::getPhone, keyword)
                .orderByDesc(SysSmsRecord::getSendTime);
        Page<SysSmsRecord> page = smsMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    /**
     * 真实发送。
     *
     * <p>接入服务商时只改这里：调服务商 SDK 拿到回执，
     * 成功返回 {@code SendResult.ok()}，失败返回具体原因。
     * 落库、脱敏、分页都不用动。</p>
     */
    private SendResult doSend(SmsSendRequest request) {
        return SendResult.fail(NOT_CONNECTED);
    }

    /**
     * 一期不做真实模板渲染，只把「模板 + 参数」记下来。
     *
     * <p>真实渲染要放在接入服务商时做：模板文案由服务商侧审核，
     * 本地渲染出来的内容和服务商实际发出的可能对不上，记下来反而误导排查。</p>
     */
    private String renderContent(SmsSendRequest request) {
        String params = StringUtils.hasText(request.getParams()) ? request.getParams() : "{}";
        String content = "模板[" + request.getTemplateCode() + "] 参数" + params;
        return content.length() > CONTENT_MAX ? content.substring(0, CONTENT_MAX) : content;
    }

    private SmsVO toVO(SysSmsRecord entity) {
        return SmsVO.builder()
                .id(String.valueOf(entity.getId()))
                // 列表一律脱敏：短信记录本质是一份可直接导出的患者联系方式
                .phone(MaskUtil.phone(entity.getPhone()))
                .templateCode(entity.getTemplateCode())
                .content(entity.getContent())
                .sendStatus(entity.getSendStatus())
                .failReason(entity.getFailReason())
                .sendTime(entity.getSendTime())
                .build();
    }

    private record SendResult(boolean success, String failReason) {

        static SendResult ok() {
            return new SendResult(true, null);
        }

        static SendResult fail(String reason) {
            return new SendResult(false, reason);
        }
    }
}
