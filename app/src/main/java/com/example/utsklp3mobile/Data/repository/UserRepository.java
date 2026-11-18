package com.example.utsklp3mobile.data.repository;

import android.content.Context;
import com.example.utsklp3mobile.data.local.DatabaseHelper;
import com.example.utsklp3mobile.data.model.User;

import java.util.List;

public class UserRepository {

    private final DatabaseHelper dbHelper;

    public UserRepository(Context context) {
        dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insertUser(String nama) {
        return dbHelper.insertUser(nama);
    }

    public long registerUser(String nama, String email, String phone, String password) {
        return dbHelper.registerUser(nama, email, phone, password);
    }

    public DatabaseHelper.AuthResult authenticate(String identifier, String password) {
        return dbHelper.authenticate(identifier, password);
    }

    public User getUserByName(String nama) {
        return dbHelper.getUserByName(nama);
    }

    public void updateUserAfterBattle(String nama, boolean isWin, int poinTambahan) {
        dbHelper.updateUserAfterBattle(nama, isWin, poinTambahan);
    }

    public List<User> searchUsersByNickname(String query, String currentUserName) {
        return dbHelper.searchUsersByNickname(query, currentUserName);
    }

    public boolean addFriend(String currentUserName, String friendName) {
        return dbHelper.addFriend(currentUserName, friendName);
    }

    public boolean isFriend(String currentUserName, String friendName) {
        return dbHelper.isFriend(currentUserName, friendName);
    }
}