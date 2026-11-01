package com.financemanage.notification.application;

import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import com.financemanage.notification.domain.PushProvider;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phones registered for push notifications: Expo push tokens (Expo app) or FCM registration tokens (KMP app; FCM also
 * delivers to iOS through APNs). The provider is given or recognised from the token.
 */
@Service
@Transactional
public class PushDeviceService {

    private static final Pattern EXPO_TOKEN = Pattern.compile("Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{10,}]");
    /** FCM registration tokens are ~150-200 URL-safe characters, often with a ':' after the instance id. */
    private static final Pattern FCM_TOKEN = Pattern.compile("[A-Za-z0-9_:\\-]{32,500}");

    private final PushDeviceRepository devices;
    private final Clock clock;

    public PushDeviceService(PushDeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    /** Registers the phone for the user; a token known for another account (shared phone) moves to this one. */
    public PushDevice register(UUID ownerId, String token, PushProvider provider, String platform,
                               String deviceName) {
        PushProvider kind = validProvider(token, provider);
        PushDevice device = devices.findById(token).orElse(null);
        if (device == null) {
            device = new PushDevice(token, ownerId, kind, platform, deviceName, clock.instant());
        } else {
            device.reassign(ownerId, kind, platform, deviceName, clock.instant());
        }
        return devices.save(device);
    }

    /** Without a provider, an {@code ExponentPushToken[…]} is Expo and anything else must be an FCM token. */
    static PushProvider validProvider(String token, PushProvider provider) {
        if (token == null) {
            throw new DomainException("INVALID_PUSH_TOKEN", "Push token is required");
        }
        PushProvider kind = provider != null ? provider
                : EXPO_TOKEN.matcher(token).matches() ? PushProvider.EXPO : PushProvider.FCM;
        boolean valid = kind == PushProvider.EXPO ? EXPO_TOKEN.matcher(token).matches()
                : FCM_TOKEN.matcher(token).matches();
        if (!valid) {
            throw new DomainException("INVALID_PUSH_TOKEN", kind == PushProvider.EXPO
                    ? "Expected an Expo push token, e.g. ExponentPushToken[…]"
                    : "Expected an FCM registration token");
        }
        return kind;
    }

    @Transactional(readOnly = true)
    public List<PushDevice> list(UUID ownerId) {
        return devices.findByOwnerId(ownerId);
    }

    /** Stops push to this phone (e.g. on sign-out). */
    public void unregister(UUID ownerId, String token) {
        PushDevice device = devices.findById(token).filter(d -> Objects.equals(d.getOwnerId(), ownerId))
                .orElseThrow(() -> new NotFoundException("Push device", token));
        devices.delete(device);
    }
}
