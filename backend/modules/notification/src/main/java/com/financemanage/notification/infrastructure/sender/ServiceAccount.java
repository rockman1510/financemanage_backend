package com.financemanage.notification.infrastructure.sender;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.json.JsonParserFactory;

/**
 * The parts of a Google service-account key file (Firebase console → Project settings → Service accounts →
 * Generate new private key) needed to call FCM.
 */
record ServiceAccount(String projectId, String clientEmail, String privateKeyId, PrivateKey privateKey,
                      String tokenUri) {

    static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    static ServiceAccount parse(String json) {
        Map<String, Object> fields = JsonParserFactory.getJsonParser().parseMap(json);
        if (!"service_account".equals(fields.get("type"))) {
            throw new IllegalArgumentException("Not a Google service-account key file (type must be service_account)");
        }
        Object tokenUri = fields.get("token_uri");
        return new ServiceAccount(required(fields, "project_id"), required(fields, "client_email"),
                (String) fields.get("private_key_id"), privateKey(required(fields, "private_key")),
                tokenUri == null ? DEFAULT_TOKEN_URI : tokenUri.toString());
    }

    /** PKCS#8 PEM ("-----BEGIN PRIVATE KEY-----"), as Google writes it. */
    private static PrivateKey privateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
                    .decode(base64)));
        } catch (Exception e) {
            throw new IllegalArgumentException("private_key of the service account is not a PKCS#8 RSA key", e);
        }
    }

    private static String required(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Service-account key file has no " + name);
        }
        return value.toString();
    }

    @Override
    public String toString() { // never print the key
        return "ServiceAccount[" + clientEmail + ", project " + projectId + "]";
    }
}
