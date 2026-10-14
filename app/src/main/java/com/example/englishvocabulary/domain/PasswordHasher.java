package com.example.englishvocabulary.domain;

import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
  public static final int ITERATIONS = 210_000;

  private PasswordHasher() {}

  public static byte[] salt() {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return salt;
  }

  public static String algorithm() {
    try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return "PBKDF2WithHmacSHA256";
    } catch (GeneralSecurityException e) {
      return "PBKDF2WithHmacSHA1";
    } // API 24/25 provider fallback, persisted per user.
  }

  public static byte[] hash(String password, byte[] salt, String algorithm, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password derivation unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }

  public static boolean verify(
      String password, byte[] salt, byte[] expected, String algorithm, int iterations) {
    return MessageDigest.isEqual(expected, hash(password, salt, algorithm, iterations));
  }
}
