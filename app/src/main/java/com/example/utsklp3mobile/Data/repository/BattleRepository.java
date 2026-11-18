package com.example.utsklp3mobile.data.repository;

import android.content.Context;
import com.example.utsklp3mobile.data.local.DatabaseHelper;
import com.example.utsklp3mobile.data.model.BattleHistory;
import java.util.List;

public class BattleRepository {

    private final DatabaseHelper dbHelper;

    public BattleRepository(Context context) {
        dbHelper = DatabaseHelper.getInstance(context);
    }

    public void insertHistory(String userName, String lawan, String mataKuliah, String hasil, int poin, String tanggal) {
        dbHelper.insertHistory(userName, lawan, mataKuliah, hasil, poin, tanggal);
    }

    public List<BattleHistory> getHistoryByUser(String userName) {
        return dbHelper.getHistoryByUser(userName);
    }

    public List<BattleHistory> getAllHistory() {
        return dbHelper.getAllHistory();
    }

    public void updateUserAfterBattle(String nama, boolean isWin, int poinTambahan) {
        dbHelper.updateUserAfterBattle(nama, isWin, poinTambahan);
    }

    public void saveRoom(String roomId, String creator, String mataKuliah, String questionsJson) {
        dbHelper.saveRoom(roomId, creator, mataKuliah, questionsJson);
    }

    public String getRoomQuestionsJson(String roomId) {
        return dbHelper.getRoomQuestionsJson(roomId);
    }
}
