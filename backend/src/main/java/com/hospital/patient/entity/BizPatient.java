package com.hospital.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hospital.common.crypto.AesTypeHandler;
import com.hospital.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 患者档案 biz_patient。
 *
 * <p><b>敏感字段处理</b>：{@code idCard} / {@code phone} 在库中是 AES-256-GCM 密文，
 * 由 {@link com.hospital.common.crypto.AesTypeHandler} 自动加解密，
 * 业务代码全程拿到明文，不需要感知加密。</p>
 *
 * <p>配套 {@code *_hash}（SHA-256 定值摘要）做查重与精确检索 ——
 * <b>密文列不能用于 WHERE</b>：GCM 每次加密的 IV 都随机，同样的明文两次加密
 * 结果不同，没法在 SQL 里比。</p>
 *
 * <p>{@code autoResultMap = true} 是必需的：不加这个，查询结果走默认映射，
 * TypeHandler 不会生效，就会出现「写进去了却读回来一串密文」。</p>
 */
@Getter
@Setter
@TableName(value = "biz_patient", autoResultMap = true)
public class BizPatient extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 患者编号，如 P0001，由序列生成，唯一 */
    private String patientNo;

    private String name;

    /** 0 女 / 1 男 / 2 未知 */
    private Integer gender;

    private LocalDate birthDate;

    /** 身份证号（库中为 AES-256-GCM 密文，检索走 idCardHash） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String idCard;

    /** 身份证 SHA-256，用于查重 */
    private String idCardHash;

    /** 手机号（库中为 AES-256-GCM 密文，检索走 phoneHash） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 */
    private String phoneHash;

    private String address;

    /** A/B/AB/O/未知 */
    private String bloodType;

    private String allergyHistory;

    private String medicalHistory;

    private String familyHistory;

    /** 当前就诊卡号（冗余快照） */
    private String cardNo;

    /** 就诊卡余额（冗余快照） */
    private BigDecimal cardBalance;

    /** 标签摘要，逗号分隔 */
    private String tags;
}
