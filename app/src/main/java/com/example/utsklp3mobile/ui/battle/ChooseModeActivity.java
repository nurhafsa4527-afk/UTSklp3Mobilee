package com.example.utsklp3mobile.ui.battle;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.utsklp3mobile.R;
import com.example.utsklp3mobile.data.model.Question;
import com.example.utsklp3mobile.data.remote.GroqQuizClient;
import com.example.utsklp3mobile.data.repository.BattleRepository;
import com.example.utsklp3mobile.utils.Constants;
import com.example.utsklp3mobile.utils.MaterialExtractor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChooseModeActivity extends AppCompatActivity {

    private static final String STATE_PENDING_MODE = "pending_mode";

    private ImageButton btnBack;
    private TextView tvSelectedMatkul, tvRankedFileStatus, tvAiFileStatus;
    private MaterialCardView cardRankedMode, cardAiMode;
    private MaterialButton btnPickFileRanked, btnPickFileAi;

    private String currentUserName = "Pemain";
    private String selectedMatkul = "Pemrograman Mobile";

    /** Mode yang sedang menunggu hasil File Picker. */
    private String pendingMode = null;
    private boolean isGenerating = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog loadingDialog;
    private TextView loadingText;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null
                        && pendingMode != null) {
                    startGeneration(pendingMode, data.getData());
                } else {
                    pendingMode = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_mode);

        if (savedInstanceState != null) {
            pendingMode = savedInstanceState.getString(STATE_PENDING_MODE);
        }

        Intent in = getIntent();
        if (in != null) {
            String name = in.getStringExtra(Constants.EXTRA_USER_NAME);
            if (name != null && !name.isEmpty()) currentUserName = name;
            String matkul = in.getStringExtra(Constants.EXTRA_MATKUL);
            if (matkul != null && !matkul.isEmpty()) selectedMatkul = matkul;
        }

        btnBack = findViewById(R.id.btnBack);
        tvSelectedMatkul = findViewById(R.id.tvSelectedMatkul);
        tvRankedFileStatus = findViewById(R.id.tvRankedFileStatus);
        tvAiFileStatus = findViewById(R.id.tvAiFileStatus);
        cardRankedMode = findViewById(R.id.cardRankedMode);
        cardAiMode = findViewById(R.id.cardAiMode);
        btnPickFileRanked = findViewById(R.id.btnPickFileRanked);
        btnPickFileAi = findViewById(R.id.btnPickFileAi);

        tvSelectedMatkul.setText("Mata Kuliah: " + selectedMatkul);
        btnBack.setOnClickListener(v -> finish());

        Runnable ranked = () -> openFilePicker(Constants.MODE_RANKED);
        Runnable ai = () -> openFilePicker(Constants.MODE_AI);

        btnPickFileRanked.setOnClickListener(v -> ranked.run());
        cardRankedMode.setOnClickListener(v -> ranked.run());
        btnPickFileAi.setOnClickListener(v -> ai.run());
        cardAiMode.setOnClickListener(v -> ai.run());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_MODE, pendingMode);
    }

    // ------------------------------------------------------------ File Picker

    private void openFilePicker(String mode) {
        if (isGenerating) return;
        pendingMode = mode;

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        });

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Pilih materi (PDF / PPTX)"));
        } catch (Exception e) {
            pendingMode = null;
            Toast.makeText(this, "Tidak ada aplikasi pemilih file di perangkat ini.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ Generate soal

    private void startGeneration(String mode, Uri uri) {
        isGenerating = true;
        setFileStatus(mode, "📄 " + MaterialExtractor.getFileName(getContentResolver(), uri));
        showLoading("Membaca materi...");

        executor.execute(() -> {
            try {
                MaterialExtractor.Material material = MaterialExtractor.read(getApplicationContext(), uri);

                BattleRepository repo = new BattleRepository(getApplicationContext());
                // Materi + mata kuliah yang sama memakai soal yang sudah pernah dibuat (hemat kuota AI)
                String cacheKey = "cache_" + sha256(selectedMatkul + "|" + material.text);
                String json = repo.getRoomQuestionsJson(cacheKey);

                if (json == null) {
                    mainHandler.post(() -> updateLoading("AI sedang membuat soal dari \"" + material.fileName + "\"..."));
                    List<Question> questions = GroqQuizClient.generate(material.text, selectedMatkul);
                    json = new Gson().toJson(questions);
                    repo.saveRoom(cacheKey, currentUserName, selectedMatkul, json);
                }

                String roomId = null;
                if (Constants.MODE_RANKED.equals(mode)) {
                    // Satu paket soal disimpan di "room" supaya kedua pemain memakai soal yang persis sama
                    roomId = UUID.randomUUID().toString();
                    repo.saveRoom(roomId, currentUserName, selectedMatkul, json);
                }

                final String finalJson = json;
                final String finalRoomId = roomId;
                mainHandler.post(() -> onGenerationSuccess(mode, finalJson, finalRoomId));
            } catch (GroqQuizClient.QuizException e) {
                mainHandler.post(() -> onGenerationFailed(mode, e.getMessage()));
            } catch (java.io.IOException e) {
                mainHandler.post(() -> onGenerationFailed(mode, e.getMessage()));
            } catch (Exception e) {
                mainHandler.post(() -> onGenerationFailed(mode, "Terjadi kesalahan: " + e.getMessage()));
            }
        });
    }

    private static String sha256(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private void onGenerationSuccess(String mode, String questionsJson, String roomId) {
        isGenerating = false;
        pendingMode = null;
        dismissLoading();
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, BattleActivity.class);
        intent.putExtra(Constants.EXTRA_USER_NAME, currentUserName);
        intent.putExtra(Constants.EXTRA_MATKUL, selectedMatkul);
        intent.putExtra(Constants.EXTRA_BATTLE_MODE, mode);
        intent.putExtra(Constants.EXTRA_QUESTIONS_JSON, questionsJson);
        if (roomId != null) intent.putExtra(Constants.EXTRA_ROOM_ID, roomId);
        // Activity ini sengaja tidak di-finish: jika battle ditutup, pengguna kembali ke pilihan mode.
        startActivity(intent);
    }

    private void onGenerationFailed(String mode, String message) {
        isGenerating = false;
        pendingMode = null;
        dismissLoading();
        setFileStatus(mode, "📄 Belum ada file dipilih");
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Gagal membuat soal")
                .setMessage(message != null ? message : "Terjadi kesalahan.")
                .setPositiveButton("OK", null)
                .setNegativeButton("Main soal bawaan", (d, w) -> {
                    // Tanpa QUESTIONS_JSON, BattleActivity memakai soal bawaan mata kuliah
                    Intent intent = new Intent(this, BattleActivity.class);
                    intent.putExtra(Constants.EXTRA_USER_NAME, currentUserName);
                    intent.putExtra(Constants.EXTRA_MATKUL, selectedMatkul);
                    intent.putExtra(Constants.EXTRA_BATTLE_MODE, mode);
                    startActivity(intent);
                })
                .show();
    }

    private void setFileStatus(String mode, String text) {
        TextView target = Constants.MODE_RANKED.equals(mode) ? tvRankedFileStatus : tvAiFileStatus;
        if (target != null) target.setText(text);
    }

    // ------------------------------------------------------------ Loading dialog

    private void showLoading(String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        ProgressBar bar = new ProgressBar(this);
        loadingText = new TextView(this);
        loadingText.setText(message);
        loadingText.setPadding(pad / 2, 0, 0, 0);
        loadingText.setTextSize(14);
        box.addView(bar);
        box.addView(loadingText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        loadingDialog = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        loadingDialog.show();
    }

    private void updateLoading(String message) {
        if (loadingText != null) loadingText.setText(message);
    }

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        loadingDialog = null;
    }

    @Override
    protected void onDestroy() {
        dismissLoading();
        executor.shutdownNow();
        super.onDestroy();
    }
}
