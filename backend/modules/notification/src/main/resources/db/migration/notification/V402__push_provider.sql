-- devices of a KMP app register FCM tokens (longer than Expo ones) next to the Expo tokens of the Expo app
ALTER TABLE notification.push_device ALTER COLUMN token SET DATA TYPE VARCHAR(500);
ALTER TABLE notification.push_device ADD COLUMN provider VARCHAR(10) NOT NULL DEFAULT 'EXPO';
