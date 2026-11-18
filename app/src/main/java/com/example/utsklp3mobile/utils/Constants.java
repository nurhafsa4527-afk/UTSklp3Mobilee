package com.example.utsklp3mobile.utils;

public class Constants {
    public static final String KEY_MATA_KULIAH = "mata_kuliah";

    // ===== Groq (AI pembuat soal) =====
    // Buat API key gratis di https://console.groq.com lalu tempel di sini.
    public static final String GROQ_API_KEY = "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";
    public static final String GROQ_MODEL = "openai/gpt-oss-120b";
    public static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    // Kunci Intent antar-Activity
    public static final String EXTRA_USER_NAME = "USER_NAME";
    public static final String EXTRA_MATKUL = "MATKUL";
    public static final String EXTRA_BATTLE_MODE = "BATTLE_MODE";
    public static final String EXTRA_QUESTIONS_JSON = "QUESTIONS_JSON";
    public static final String EXTRA_ROOM_ID = "ROOM_ID";

    public static final String MODE_RANKED = "RANKED";
    public static final String MODE_AI = "AI_CHALLENGE";
}
