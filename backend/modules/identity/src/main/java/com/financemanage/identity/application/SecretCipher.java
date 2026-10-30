package com.financemanage.identity.application;

/** Outbound port: encrypts secrets stored in the database (two-factor secrets). */
public interface SecretCipher {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
