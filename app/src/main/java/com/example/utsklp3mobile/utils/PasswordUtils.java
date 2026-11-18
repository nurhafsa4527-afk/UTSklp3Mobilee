package com.example.utsklp3mobile.utils;

import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hash kata sandi (PBKDF2) supaya password tidak disimpan sebagai teks biasa. */
public final class PasswordUtils {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 160;

    private PasswordUtils() {}

    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hash(String password, String saltBase64) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return Base64.encodeToString(f.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal meng-hash kata sandi", e);
        }
    }
}
