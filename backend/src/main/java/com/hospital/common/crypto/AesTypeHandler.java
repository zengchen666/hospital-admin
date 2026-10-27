package com.hospital.common.crypto;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段自动加解密 TypeHandler。
 *
 * <p>挂在实体字段上（{@code @TableField(typeHandler = AesTypeHandler.class)}），
 * 写入时加密、读出时解密 —— 业务代码全程拿到明文，不需要感知加密的存在。
 * 这是加密能真正落地的前提：<b>靠每个开发者记得手动调用加解密，一定会漏</b>。</p>
 *
 * <p><b>为什么实现 {@link TypeHandler} 而不是继承 {@code BaseTypeHandler<String>}</b>：
 * {@code BaseTypeHandler} 实现了 {@code TypeReference}，MyBatis 在
 * {@code type-handlers-package} 扫描注册时会按泛型把它登记为
 * <b>所有 String 字段的默认处理器</b> —— 于是 {@code sys_user.password}、
 * 各种名称字段全被拿去加密，登录直接校验失败。实现裸 {@code TypeHandler}
 * 则不会被绑到任何 java 类型，只在 {@code @TableField} 显式声明处生效。</p>
 *
 * <p><b>两个必须知道的限制</b>：</p>
 * <ol>
 *   <li>实体必须同时标 {@code @TableName(autoResultMap = true)}，
 *       否则查询走默认映射，TypeHandler 不会生效 —— 写进去了却读不出来明文。</li>
 *   <li><b>密文列不能用于 SQL 的 WHERE / ORDER BY / LIKE</b>。数据库里存的是密文，
 *       每次加密的 IV 都不同，同样的明文两次加密结果也不一样。
 *       检索必须走 {@code *_hash} 列（SHA-256 定值摘要，可精确匹配）。</li>
 * </ol>
 */
public class AesTypeHandler implements TypeHandler<String> {

    private static volatile AesCrypto crypto;

    public static void register(AesCrypto instance) {
        crypto = instance;
    }

    private AesCrypto crypto() {
        AesCrypto current = crypto;
        if (current == null) {
            throw new IllegalStateException("AesCrypto 尚未初始化，请检查 hospital.crypto.aes-key 配置");
        }
        return current;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter == null) {
            ps.setNull(i, java.sql.Types.VARCHAR);
        } else {
            ps.setString(i, crypto().encrypt(parameter));
        }
    }

    @Override
    public String getResult(ResultSet rs, String columnName) throws SQLException {
        return crypto().decrypt(rs.getString(columnName));
    }

    @Override
    public String getResult(ResultSet rs, int columnIndex) throws SQLException {
        return crypto().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return crypto().decrypt(cs.getString(columnIndex));
    }
}
