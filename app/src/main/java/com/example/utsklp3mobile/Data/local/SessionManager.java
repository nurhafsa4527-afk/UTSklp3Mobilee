package com.example.utsklp3mobile.data.local;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "QuizBattleSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences pref;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void createLoginSession(String username) {
        pref.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public boolean isLoggedIn() {
        String name = pref.getString(KEY_USERNAME, null);
        return pref.getBoolean(KEY_IS_LOGGED_IN, false) && name != null && !name.isEmpty();
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, "Pemain");
    }

    public void logoutUser() {
        // Hanya hapus status sesi. Data akun/riwayat ada di SQLite dan tidak disentuh.
        pref.edit()
                .remove(KEY_IS_LOGGED_IN)
                .remove(KEY_USERNAME)
                .apply();
    }
}