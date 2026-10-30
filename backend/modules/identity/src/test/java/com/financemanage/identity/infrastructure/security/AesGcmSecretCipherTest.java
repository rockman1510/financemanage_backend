package com.financemanage.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher("0123456789abcdef0123456789abcdef");

    @Test
    void encryptsWithAFreshIvAndDecryptsBack() {
        String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).isNotEqualTo(second).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void rejectsTamperingAnotherKeyAndShortKeys() {
        String encrypted = cipher.encrypt("secret");
        char[] chars = encrypted.toCharArray();
        chars[20] = chars[20] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> cipher.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretCipher("another-key-another-key-another-key").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretCipher("short")).isInstanceOf(IllegalArgumentException.class);
    }
}
