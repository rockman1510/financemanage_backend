package com.financemanage.notification.domain;

/** Who delivers pushes to a device token. */
public enum PushProvider {
    /** {@code ExponentPushToken[…]} of an Expo / React Native app, sent through the Expo Push API */
    EXPO,
    /** Firebase Cloud Messaging registration token (Android, and iOS through APNs), e.g. of a KMP app */
    FCM
}
