package com.yuanyu.safety.common;

/**
 * 隐私脱敏工具。
 *
 * <p>工地详细地址属于敏感信息：列表、导出等“浏览态”只展示到区/县一级，
 * 完整地址仅在授权角色编辑时通过详情接口返回。</p>
 */
public final class PrivacyUtil {

    public static final String MASK = "****";

    private PrivacyUtil() {
    }

    /**
     * 地址脱敏：
     * <ul>
     *   <li>江西省示例市示例区示例路 1 号 → 江西省示例市示例区****</li>
     *   <li>滨江大道 88 号 → 滨江大道****</li>
     * </ul>
     */
    public static String maskAddress(String address) {
        if (address == null) {
            return null;
        }
        if (address.isBlank()) {
            return "";
        }
        String value = address.trim();
        if (value.contains(MASK)) {
            return value;
        }
        int cut = -1;
        for (String suffix : new String[]{"区", "县", "旗", "市"}) {
            int index = value.lastIndexOf(suffix);
            if (index > cut) {
                cut = index;
            }
        }
        if (cut >= 2 && cut < value.length() - 1) {
            return value.substring(0, cut + 1) + MASK;
        }
        if (value.length() <= 4) {
            return value.charAt(0) + MASK;
        }
        return value.substring(0, Math.min(4, value.length())) + MASK;
    }

    public static boolean isMasked(String value) {
        return value != null && value.contains(MASK);
    }
}
