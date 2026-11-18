package com.example.utsklp3mobile.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.utsklp3mobile.R;
import com.example.utsklp3mobile.data.local.DatabaseHelper;
import com.example.utsklp3mobile.data.local.SessionManager;
import com.example.utsklp3mobile.data.model.User;
import com.example.utsklp3mobile.data.repository.UserRepository;
import com.example.utsklp3mobile.ui.main.MainActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etPassword;
    private Button btnLogin, btnGoogleLogin;
    private TextView tvRegister;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Jika sudah login, langsung alihkan ke MainActivity
        if (sessionManager.isLoggedIn()) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("USER_NAME", sessionManager.getUsername());
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        userRepository = new UserRepository(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> performLogin());

        btnGoogleLogin.setOnClickListener(v ->
                Toast.makeText(this, "Sign in with Google clicked", Toast.LENGTH_SHORT).show()
        );

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Nama Pengguna / Email wajib diisi");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Kata sandi wajib diisi");
            etPassword.requestFocus();
            return;
        }

        // Validasi ke database: nama pengguna atau email + kata sandi
        DatabaseHelper.AuthResult result = userRepository.authenticate(username, password);
        if (result.status == DatabaseHelper.AUTH_NOT_FOUND && username.contains("@")) {
            // Kompatibilitas akun lama yang masuk memakai bagian depan email
            result = userRepository.authenticate(username.split("@")[0], password);
        }

        if (result.status == DatabaseHelper.AUTH_NOT_FOUND) {
            etUsername.setError("Akun tidak ditemukan. Silakan daftar dulu.");
            etUsername.requestFocus();
            return;
        }

        if (result.status == DatabaseHelper.AUTH_WRONG_PASSWORD || result.user == null) {
            etPassword.setError("Kata sandi salah");
            etPassword.requestFocus();
            return;
        }

        User existingUser = result.user;

        // Simpan sesi login ke SharedPreferences
        sessionManager.createLoginSession(existingUser.getNama());

        Toast.makeText(this, "Login berhasil! Selamat datang " + existingUser.getNama(), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_NAME", existingUser.getNama());
        startActivity(intent);
        finish();
    }
}