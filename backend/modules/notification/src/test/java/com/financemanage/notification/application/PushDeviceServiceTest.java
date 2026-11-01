package com.financemanage.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.notification.domain.PushProvider;
import com.financemanage.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

class PushDeviceServiceTest {

    private static final String EXPO = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]";
    private static final String FCM = "dGVzdC1pbnN0YW5jZQ:APA91bH" + "x".repeat(140);

    @Test
    void recognisesTheProviderFromTheToken() {
        assertThat(PushDeviceService.validProvider(EXPO, null)).isEqualTo(PushProvider.EXPO);
        assertThat(PushDeviceService.validProvider(FCM, null)).isEqualTo(PushProvider.FCM);
        assertThat(PushDeviceService.validProvider(FCM, PushProvider.FCM)).isEqualTo(PushProvider.FCM);
    }

    @Test
    void rejectsTokensThatDoNotFitTheProvider() {
        assertThatThrownBy(() -> PushDeviceService.validProvider(FCM, PushProvider.EXPO))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PUSH_TOKEN");
        assertThatThrownBy(() -> PushDeviceService.validProvider(EXPO, PushProvider.FCM))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PUSH_TOKEN");
        assertThatThrownBy(() -> PushDeviceService.validProvider("short", null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PUSH_TOKEN");
        assertThatThrownBy(() -> PushDeviceService.validProvider("bad token with spaces " + "x".repeat(40), null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PUSH_TOKEN");
    }
}
