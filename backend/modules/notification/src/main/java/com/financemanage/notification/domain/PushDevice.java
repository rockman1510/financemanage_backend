package com.financemanage.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A phone that receives push notifications, identified by its Expo push token. */
@Entity
@Table(name = "push_device", schema = "notification")
public class PushDevice {

    @Id
    private String token;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    private String platform;
    @Column(name = "device_name")
    private String deviceName;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected PushDevice() {
    }

    public PushDevice(String token, UUID ownerId, String platform, String deviceName, Instant registeredAt) {
        this.token = token;
        this.ownerId = ownerId;
        this.platform = platform;
        this.deviceName = deviceName;
        this.registeredAt = registeredAt;
    }

    /** The same phone signed in again, maybe with another account: the token now belongs to it. */
    public void reassign(UUID newOwnerId, String newPlatform, String newDeviceName, Instant now) {
        ownerId = newOwnerId;
        platform = newPlatform;
        deviceName = newDeviceName;
        registeredAt = now;
    }

    public String getToken() { return token; }
    public UUID getOwnerId() { return ownerId; }
    public String getPlatform() { return platform; }
    public String getDeviceName() { return deviceName; }
    public Instant getRegisteredAt() { return registeredAt; }
}
