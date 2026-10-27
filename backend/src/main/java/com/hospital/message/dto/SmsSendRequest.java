package com.hospital.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 短信发送入参。
 *
 * <p>{@code templateCode} 必填：短信内容不能由调用方随意拼 ——
 * 模板编码决定内容结构，既能防止被拿去发广告，也让发送记录可审计。</p>
 */
@Getter
@Setter
public class SmsSendRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /** 模板变量，如 {"code":"123456"}；一期仅落库记录，不做真实渲染 */
    @Size(max = 200, message = "模板参数过长")
    private String params;
}
