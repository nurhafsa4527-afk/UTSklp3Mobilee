package com.example.utsklp3mobile.ui.battle;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.utsklp3mobile.R;
import com.example.utsklp3mobile.data.model.Question;
import com.example.utsklp3mobile.data.model.User;
import com.example.utsklp3mobile.data.remote.GroqQuizClient;
import com.example.utsklp3mobile.data.repository.BattleRepository;
import com.example.utsklp3mobile.data.repository.UserRepository;
import com.example.utsklp3mobile.utils.Constants;
import com.example.utsklp3mobile.utils.MaterialExtractor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;

import java.io.IOException;
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
    private String selectedEnemy = null;

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

        tvSelectedMatkul.setText("Mata Kuliah: " + selectedMatkul + " (Ketuk untuk ubah)");
        tvSelectedMatkul.setOnClickListener(v -> showMatkulDropdownDialog());
        btnBack.setOnClickListener(v -> finish());

        Runnable ranked = () -> promptChooseFriendAndPickFile(Constants.MODE_RANKED);
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

    // ------------------------------------------------------------ Pilih Teman (Quick Match & Dropdown Teman)

    private void promptChooseFriendAndPickFile(String mode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_choose_ranked_opponent, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        View cardQuickMatch = dialogView.findViewById(R.id.cardQuickMatch);
        Spinner spinnerFriends = dialogView.findViewById(R.id.spinnerFriends);
        MaterialButton btnBattleFriend = dialogView.findViewById(R.id.btnBattleFriend);

        // 1. QUICK MATCH: Langsung bertanding tanpa pilih PDF (pakai soal default sesuai matkul)
        cardQuickMatch.setOnClickListener(v -> {
            String[] randomQuickNames = {"Andi_Fast", "Rizki_Pro", "Dimas_Rank", "Fajar_Arena", "Bayu_Blitz"};
            selectedEnemy = randomQuickNames[(int) (Math.random() * randomQuickNames.length)];
            Toast.makeText(this, "Quick Match! Lawan: " + selectedEnemy, Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            Intent intent = new Intent(this, BattleActivity.class);
            intent.putExtra(Constants.EXTRA_USER_NAME, currentUserName);
            intent.putExtra(Constants.EXTRA_MATKUL, selectedMatkul);
            intent.putExtra(Constants.EXTRA_BATTLE_MODE, mode);
            intent.putExtra(Constants.EXTRA_ENEMY_NAME, selectedEnemy);
            startActivity(intent);
        });

        UserRepository userRepo = new UserRepository(this);
        List<String> friends = userRepo.getFriendNames(currentUserName);
        if (friends.isEmpty()) {
            List<User> allUsers = userRepo.searchUsersByNickname("", currentUserName);
            for (User u : allUsers) {
                if (u.getNama() != null && !u.getNama().equalsIgnoreCase(currentUserName)) {
                    friends.add(u.getNama());
                }
            }
        }
        if (friends.isEmpty()) {
            friends.add("Rina");
            friends.add("Budi");
            friends.add("Andi");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, friends);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFriends.setAdapter(adapter);

        // 2. PILIH TEMAN: Wajib pilih file PDF/PPTX untuk generate soal melawan teman
        btnBattleFriend.setOnClickListener(v -> {
            Object selected = spinnerFriends.getSelectedItem();
            if (selected != null) {
                selectedEnemy = selected.toString();
                Toast.makeText(this, "Lawan dipilih: " + selectedEnemy + ". Silakan pilih file materi.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                openFilePicker(mode);
            } else {
                Toast.makeText(this, "Pilih teman terlebih dahulu", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
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
        String fileName = MaterialExtractor.getFileName(getContentResolver(), uri);
        setFileStatus(mode, "📄 " + fileName);
        showLoading("Membaca materi...");

        // Untuk PDF / PPT, set mata kuliah menjadi CUSTOM (atau biarkan pilihan user dari dropdown)
        mainHandler.post(() -> {
            if ("Pemrograman Mobile".equalsIgnoreCase(selectedMatkul)) {
                selectedMatkul = "CUSTOM";
                tvSelectedMatkul.setText("Mata Kuliah: " + selectedMatkul + " (Ketuk untuk ubah)");
            }
        });

        executor.execute(() -> {
            try {
                MaterialExtractor.Material material = MaterialExtractor.read(getApplicationContext(), uri);

                BattleRepository repo = new BattleRepository(getApplicationContext());
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
                    roomId = UUID.randomUUID().toString();
                    repo.saveRoom(roomId, currentUserName, selectedMatkul, json);
                }

                final String finalJson = json;
                final String finalRoomId = roomId;
                mainHandler.post(() -> onGenerationSuccess(mode, finalJson, finalRoomId));
            } catch (GroqQuizClient.QuizException e) {
                mainHandler.post(() -> onGenerationFailed(mode, e.getMessage()));
            } catch (IOException e) {
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
        if (selectedEnemy != null) {
            intent.putExtra(Constants.EXTRA_ENEMY_NAME, selectedEnemy);
        }
        if (roomId != null) intent.putExtra(Constants.EXTRA_ROOM_ID, roomId);
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
                    Intent intent = new Intent(this, BattleActivity.class);
                    intent.putExtra(Constants.EXTRA_USER_NAME, currentUserName);
                    intent.putExtra(Constants.EXTRA_MATKUL, selectedMatkul);
                    intent.putExtra(Constants.EXTRA_BATTLE_MODE, mode);
                    if (selectedEnemy != null) {
                        intent.putExtra(Constants.EXTRA_ENEMY_NAME, selectedEnemy);
                    }
                    startActivity(intent);
                })
                .show();
    }

    private void setFileStatus(String mode, String text) {
        TextView target = Constants.MODE_RANKED.equals(mode) ? tvRankedFileStatus : tvAiFileStatus;
        if (target != null) target.setText(text);
    }

    // ------------------------------------------------------------ Dropdown Pilihan Mata Kuliah

    private void showMatkulDropdownDialog() {
        String[] matkulList = new String[]{
                "Pemrograman Mobile",
                "Jaringan Komputer",
                "Sistem Operasi",
                "Basis Data"
        };

        int currentIndex = 0;
        for (int i = 0; i < matkulList.length; i++) {
            if (matkulList[i].equalsIgnoreCase(selectedMatkul)) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Mata Kuliah")
                .setSingleChoiceItems(matkulList, currentIndex, (dialog, which) -> {
                    selectedMatkul = matkulList[which];
                    tvSelectedMatkul.setText("Mata Kuliah: " + selectedMatkul + " (Ketuk untuk ubah)");
                    dialog.dismiss();
                    Toast.makeText(this, "Mata Kuliah diset ke: " + selectedMatkul, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
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
