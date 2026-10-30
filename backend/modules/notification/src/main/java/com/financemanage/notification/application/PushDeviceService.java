package com.financemanage.notification.application;

import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phones registered for push notifications (Expo push tokens). */
@Service
@Transactional
public class PushDeviceService {

    private static final Pattern EXPO_TOKEN = Pattern.compile("Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{10,}]");

    private final PushDeviceRepository devices;
    private final Clock clock;

    public PushDeviceService(PushDeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    /** Registers the phone for the user; a token known for another account (shared phone) moves to this one. */
    public PushDevice register(UUID ownerId, String token, String platform, String deviceName) {
        if (token == null || !EXPO_TOKEN.matcher(token).matches()) {
            throw new DomainException("INVALID_PUSH_TOKEN", "Expected an Expo push token, e.g. ExponentPushToken[…]");
        }
        PushDevice device = devices.findById(token).orElse(null);
        if (device == null) {
            device = new PushDevice(token, ownerId, platform, deviceName, clock.instant());
        } else {
            device.reassign(ownerId, platform, deviceName, clock.instant());
        }
        return devices.save(device);
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
