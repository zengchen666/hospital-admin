package com.hospital.common.util;

/**
 * 敏感信息脱敏。
 *
 * <p>列表与详情默认返回脱敏后的值，只有显式调用「解密查看」接口（需
 * {@code patient:decrypt} 权限）才返回明文，且该操作会进审计日志。</p>
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 手机号：13800001111 → 138****1111 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证：440300199005011234 → 4403**********1234 */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        int keepHead = 4;
        int keepTail = 4;
        int maskLen = idCard.length() - keepHead - keepTail;
        return idCard.substring(0, keepHead)
                + "*".repeat(Math.max(0, maskLen))
                + idCard.substring(idCard.length() - keepTail);
    }

    /** 姓名：张三 → 张*；欧阳修 → 欧*修 */
    public static String name(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** 地址：只保留到区/县级别，其余打码 */
    public static String address(String address) {
        if (address == null || address.length() <= 6) {
            return address;
        }
        return address.substring(0, 6) + "****";
    }
}
