package com.example.utsklp3mobile.data.remote;

import com.example.utsklp3mobile.data.model.Question;
import com.example.utsklp3mobile.utils.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Membuat soal kuis dari teks materi lewat Groq (API gratis, format kompatibel OpenAI). */
public final class GroqQuizClient {

    public static final int QUESTION_COUNT = 10;
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] RETRY_DELAYS_MS = {3000, 8000, 15000};

    private GroqQuizClient() {}

    public static class QuizException extends Exception {
        /** true untuk gangguan sementara (429/500/502/503/504, timeout). */
        public final boolean retryable;

        public QuizException(String message) {
            this(message, false);
        }

        public QuizException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    public static List<Question> generate(String materialText, String mataKuliah) throws QuizException {
        String apiKey = Constants.GROQ_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("ISI_API_KEY")) {
            throw new QuizException("API Key Groq belum diisi di Constants.java (GROQ_API_KEY).");
        }

        String body = buildRequest(materialText, mataKuliah);
        String response = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                response = call(body, apiKey);
                break;
            } catch (QuizException e) {
                if (!e.retryable || attempt == MAX_ATTEMPTS - 1) throw e;
                try {
                    Thread.sleep(RETRY_DELAYS_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new QuizException("Pembuatan soal dibatalkan.");
                }
            }
        }

        List<Question> questions = parseQuestions(response, mataKuliah);
        if (questions.size() < 3) {
            throw new QuizException("AI hanya menghasilkan " + questions.size()
                    + " soal yang valid. Coba lagi atau pilih materi yang lebih panjang.");
        }
        return questions;
    }

    // ------------------------------------------------------------------ request

    private static String buildRequest(String materialText, String mataKuliah) {
        String system = "Kamu pembuat soal kuis untuk mahasiswa. Keluaranmu SELALU berupa satu objek JSON murni "
                + "tanpa markdown dan tanpa teks lain.";

        String user = "Mata kuliah: " + mataKuliah + ".\n"
                + "Buat tepat " + QUESTION_COUNT + " soal pilihan ganda berbahasa Indonesia, "
                + "HANYA berdasarkan materi di bawah.\n"
                + "Aturan:\n"
                + "- Tiap soal punya 4 pilihan dengan tepat satu jawaban benar.\n"
                + "- Pilihan singkat dan tidak diawali huruf 'A.' / 'B.' dst.\n"
                + "- Variasikan posisi jawaban benar.\n"
                + "- Format: {\"questions\":[{\"soal\":\"...\",\"pilihanA\":\"...\",\"pilihanB\":\"...\","
                + "\"pilihanC\":\"...\",\"pilihanD\":\"...\",\"jawabanBenar\":\"A\"}]}\n"
                + "- jawabanBenar hanya salah satu huruf: A, B, C, atau D.\n\n"
                + "MATERI:\n" + materialText;

        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_object");

        JsonObject root = new JsonObject();
        root.addProperty("model", Constants.GROQ_MODEL);
        root.add("messages", messages);
        root.addProperty("temperature", 0.6);
        root.addProperty("max_completion_tokens", 3000);
        root.addProperty("reasoning_effort", "low");
        root.add("response_format", format);
        return root.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    // ------------------------------------------------------------------ HTTP

    private static String call(String body, String apiKey) throws QuizException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(Constants.GROQ_ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = stream != null ? readFully(stream) : "";

            if (code < 200 || code >= 300) {
                boolean transientError = code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
                throw new QuizException(describeHttpError(code, response), transientError);
            }
            return response;
        } catch (QuizException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new QuizException("AI terlalu lama merespons. Coba lagi sebentar lagi.", true);
        } catch (java.net.UnknownHostException e) {
            throw new QuizException("Tidak bisa terhubung ke server AI. Periksa koneksi internet lalu coba lagi.");
        } catch (IOException e) {
            throw new QuizException("Gagal menghubungi server AI: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static String describeHttpError(int code, String body) {
        String detail = "";
        try {
            JsonObject err = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (err != null && err.has("message")) detail = err.get("message").getAsString();
        } catch (Exception ignored) {
        }
        switch (code) {
            case 400:
                return "Permintaan ditolak (400). " + detail;
            case 401:
            case 403:
                return "API Key Groq tidak valid atau tidak punya akses (" + code + "). " + detail;
            case 404:
                return "Model AI tidak ditemukan (404). Periksa GROQ_MODEL di Constants.java. " + detail;
            case 413:
                return "Materi terlalu panjang untuk batas gratis (413). " + detail;
            case 429:
                return "Batas pemakaian gratis Groq tercapai (429). Tunggu sekitar 1 menit lalu coba lagi. " + detail;
            case 503:
                return "Server AI sedang sibuk (503). Coba lagi dalam beberapa menit.";
            default:
                return "Error AI " + code + ". " + detail;
        }
    }

    // ------------------------------------------------------------------ parsing

    private static List<Question> parseQuestions(String response, String mataKuliah) throws QuizException {
        String content;
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            throw new QuizException("Format respons AI tidak dikenali.");
        }

        content = content.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?", "").replaceFirst("```\\s*$", "").trim();
        }

        List<Question> raw;
        try {
            JsonElement parsed = JsonParser.parseString(content);
            JsonElement arr = parsed.isJsonArray() ? parsed : parsed.getAsJsonObject().get("questions");
            raw = new Gson().fromJson(arr, new TypeToken<List<Question>>() {}.getType());
        } catch (Exception e) {
            throw new QuizException("JSON soal dari AI tidak valid. Coba lagi.");
        }
        if (raw == null) raw = new ArrayList<>();

        List<Question> valid = new ArrayList<>();
        for (Question q : raw) {
            if (q == null || isBlank(q.getSoal()) || isBlank(q.getPilihanA()) || isBlank(q.getPilihanB())
                    || isBlank(q.getPilihanC()) || isBlank(q.getPilihanD())) continue;
            String key = normalizeAnswer(q);
            if (key == null) continue;
            q.setJawabanBenar(key);
            q.setMataKuliah(mataKuliah);
            q.setId(valid.size() + 1);
            valid.add(q);
        }
        return valid;
    }

    /** Ubah jawabanBenar jadi salah satu "A".."D", atau null jika tidak bisa dipastikan. */
    private static String normalizeAnswer(Question q) {
        String ans = q.getJawabanBenar();
        if (isBlank(ans)) return null;
        String t = ans.trim();
        String up = t.toUpperCase(Locale.ROOT);
        if ("ABCD".indexOf(up.charAt(0)) >= 0
                && (up.length() == 1 || !Character.isLetterOrDigit(up.charAt(1)))) {
            return String.valueOf(up.charAt(0));
        }
        if (t.equalsIgnoreCase(q.getPilihanA().trim())) return "A";
        if (t.equalsIgnoreCase(q.getPilihanB().trim())) return "B";
        if (t.equalsIgnoreCase(q.getPilihanC().trim())) return "C";
        if (t.equalsIgnoreCase(q.getPilihanD().trim())) return "D";
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
