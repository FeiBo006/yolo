package com.yuanyu.safety.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyUtilTest {

    @Test
    void shouldKeepAdministrativeRegionAndMaskStreet() {
        assertEquals("江西省示例市示例区****",
                PrivacyUtil.maskAddress("江西省示例市示例区示例路 1 号"));
    }

    @Test
    void shouldMaskAddressWithoutDistrictSuffix() {
        assertEquals("滨江大道****", PrivacyUtil.maskAddress("滨江大道 88 号"));
    }

    @Test
    void shouldNotDoubleMask() {
        String masked = "江西省示例市示例区****";
        assertEquals(masked, PrivacyUtil.maskAddress(masked));
        assertTrue(PrivacyUtil.isMasked(masked));
    }

    @Test
    void shouldHandleBlank() {
        assertEquals(null, PrivacyUtil.maskAddress(null));
        assertEquals("", PrivacyUtil.maskAddress("  "));
    }
}
