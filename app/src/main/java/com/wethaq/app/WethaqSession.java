package com.wethaq.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted storage for session and administrative tokens. */
public final class WethaqSession {
    private static final String PREFS = "wethaq_secure_session";
    private static final String KEY_ALIAS = "wethaq_session_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String SEP = ":";
    private WethaqSession() {}

    public static String get(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encrypted = prefs.getString(key, "");
        if (!encrypted.isEmpty()) {
            try {
                String[] parts = encrypted.split(SEP, 2);
                byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
                byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                prefs.edit().remove(key).apply();
            }
        }
        // One-time migration for installations created before encrypted storage.
        String legacy = context.getSharedPreferences("wethaq", Context.MODE_PRIVATE).getString(key, "");
        if (!legacy.isEmpty()) {
            put(context, key, legacy);
            context.getSharedPreferences("wethaq", Context.MODE_PRIVATE).edit().remove(key).apply();
        }
        return legacy;
    }

    public static void put(Context context, String key, String value) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.encodeToString(iv, Base64.NO_WRAP) + SEP + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, encoded).apply();
        } catch (Exception ignored) {
            throw new IllegalStateException("Unable to secure Wethaq session", ignored);
        }
    }

    public static void remove(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
        context.getSharedPreferences("wethaq", Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
