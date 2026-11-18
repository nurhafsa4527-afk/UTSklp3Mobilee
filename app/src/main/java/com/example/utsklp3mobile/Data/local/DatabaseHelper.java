package com.example.utsklp3mobile.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.utsklp3mobile.data.model.BattleHistory;
import com.example.utsklp3mobile.data.model.Ranking;
import com.example.utsklp3mobile.data.model.User;
import com.example.utsklp3mobile.utils.PasswordUtils;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "QuizBattleArena.db";
    private static final int DATABASE_VERSION = 3;

    // Tabel Users
    private static final String TABLE_USERS = "users";
    private static final String COL_USER_ID = "id";
    private static final String COL_USER_NAMA = "nama";
    private static final String COL_USER_POIN = "total_poin";
    private static final String COL_USER_MENANG = "jumlah_menang";
    private static final String COL_USER_KALAH = "jumlah_kalah";
    private static final String COL_USER_MAIN = "jumlah_main";

    private static final String COL_USER_EMAIL = "email";
    private static final String COL_USER_PHONE = "phone";
    private static final String COL_USER_HASH = "password_hash";
    private static final String COL_USER_SALT = "password_salt";

    // Tabel Room (paket soal battle yang dibagi ke kedua pemain)
    private static final String TABLE_ROOMS = "battle_rooms";

    // Tabel History
    private static final String TABLE_HISTORY = "battle_history";
    private static final String COL_HISTORY_ID = "id";
    private static final String COL_HISTORY_USER_NAME = "user_name";
    private static final String COL_HISTORY_LAWAN = "lawan";
    private static final String COL_HISTORY_MATKUL = "mata_kuliah";
    private static final String COL_HISTORY_HASIL = "hasil";
    private static final String COL_HISTORY_POIN = "poin_didapat";
    private static final String COL_HISTORY_TANGGAL = "tanggal";

    // Tabel Friends
    private static final String TABLE_FRIENDS = "friends";
    private static final String COL_FRIEND_ID = "id";
    private static final String COL_FRIEND_USER_NAME = "user_name";
    private static final String COL_FRIEND_NAME = "friend_name";

    private static DatabaseHelper instance;

    /** Satu instance untuk seluruh aplikasi agar tidak ada koneksi ganda ke file database. */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static final int AUTH_OK = 0;
    public static final int AUTH_NOT_FOUND = 1;
    public static final int AUTH_WRONG_PASSWORD = 2;

    public static class AuthResult {
        public final int status;
        public final User user;
        AuthResult(int status, User user) {
            this.status = status;
            this.user = user;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsers = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USER_NAMA + " TEXT, " +
                COL_USER_POIN + " INTEGER DEFAULT 0, " +
                COL_USER_MENANG + " INTEGER DEFAULT 0, " +
                COL_USER_KALAH + " INTEGER DEFAULT 0, " +
                COL_USER_MAIN + " INTEGER DEFAULT 0, " +
                COL_USER_EMAIL + " TEXT, " +
                COL_USER_PHONE + " TEXT, " +
                COL_USER_HASH + " TEXT, " +
                COL_USER_SALT + " TEXT)";
        db.execSQL(createUsers);

        String createHistory = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
                COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_HISTORY_USER_NAME + " TEXT, " +
                COL_HISTORY_LAWAN + " TEXT, " +
                COL_HISTORY_MATKUL + " TEXT, " +
                COL_HISTORY_HASIL + " TEXT, " +
                COL_HISTORY_POIN + " INTEGER, " +
                COL_HISTORY_TANGGAL + " TEXT)";
        db.execSQL(createHistory);

        String createFriends = "CREATE TABLE IF NOT EXISTS " + TABLE_FRIENDS + " (" +
                COL_FRIEND_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FRIEND_USER_NAME + " TEXT, " +
                COL_FRIEND_NAME + " TEXT)";
        db.execSQL(createFriends);

        createRoomsTable(db);
    }

    private void createRoomsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ROOMS + " (" +
                "room_id TEXT PRIMARY KEY, " +
                "creator TEXT, " +
                "mata_kuliah TEXT, " +
                "questions_json TEXT, " +
                "created_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade bersifat aditif: tidak ada tabel yang di-drop, sehingga data akun lama tetap aman.
        addColumnIfMissing(db, TABLE_HISTORY, COL_HISTORY_USER_NAME, "TEXT");
        addColumnIfMissing(db, TABLE_USERS, COL_USER_EMAIL, "TEXT");
        addColumnIfMissing(db, TABLE_USERS, COL_USER_PHONE, "TEXT");
        addColumnIfMissing(db, TABLE_USERS, COL_USER_HASH, "TEXT");
        addColumnIfMissing(db, TABLE_USERS, COL_USER_SALT, "TEXT");
        createRoomsTable(db);
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;
        try {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(nameIdx))) {
                    exists = true;
                    break;
                }
            }
        } finally {
            c.close();
        }
        if (!exists) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    // ========== AUTH ==========
    /** Daftarkan akun baru. Mengembalikan -1 jika nama atau email sudah dipakai. */
    public synchronized long registerUser(String nama, String email, String phone, String password) {
        if (getUserByName(nama) != null) return -1;
        if (email != null && !email.isEmpty() && findUserRow(email) != null) return -1;

        String salt = PasswordUtils.newSalt();
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USER_NAMA, nama);
        values.put(COL_USER_EMAIL, email);
        values.put(COL_USER_PHONE, phone);
        values.put(COL_USER_SALT, salt);
        values.put(COL_USER_HASH, PasswordUtils.hash(password, salt));
        values.put(COL_USER_POIN, 0);
        values.put(COL_USER_MENANG, 0);
        values.put(COL_USER_KALAH, 0);
        values.put(COL_USER_MAIN, 0);
        return db.insert(TABLE_USERS, null, values);
    }

    private String[] findUserRow(String identifier) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_USERS,
                new String[]{COL_USER_NAMA, COL_USER_HASH, COL_USER_SALT},
                "LOWER(" + COL_USER_NAMA + ") = LOWER(?) OR LOWER(" + COL_USER_EMAIL + ") = LOWER(?)",
                new String[]{identifier, identifier}, null, null, null);
        try {
            if (c.moveToFirst()) {
                return new String[]{c.getString(0), c.getString(1), c.getString(2)};
            }
            return null;
        } finally {
            c.close();
        }
    }

    /** Validasi kredensial: identifier boleh nama pengguna atau email. */
    public synchronized AuthResult authenticate(String identifier, String password) {
        String[] row = findUserRow(identifier);
        if (row == null) return new AuthResult(AUTH_NOT_FOUND, null);

        String nama = row[0], storedHash = row[1], storedSalt = row[2];
        SQLiteDatabase db = this.getWritableDatabase();

        if (storedHash == null || storedHash.isEmpty() || storedSalt == null || storedSalt.isEmpty()) {
            // Akun lama (dibuat sebelum ada password): login pertama menetapkan password-nya.
            String salt = PasswordUtils.newSalt();
            ContentValues cv = new ContentValues();
            cv.put(COL_USER_SALT, salt);
            cv.put(COL_USER_HASH, PasswordUtils.hash(password, salt));
            db.update(TABLE_USERS, cv, "LOWER(" + COL_USER_NAMA + ") = LOWER(?)", new String[]{nama});
            return new AuthResult(AUTH_OK, getUserByName(nama));
        }

        if (!PasswordUtils.hash(password, storedSalt).equals(storedHash)) {
            return new AuthResult(AUTH_WRONG_PASSWORD, null);
        }
        return new AuthResult(AUTH_OK, getUserByName(nama));
    }

    // ========== ROOM (paket soal battle) ==========
    public synchronized void saveRoom(String roomId, String creator, String mataKuliah, String questionsJson) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("room_id", roomId);
        cv.put("creator", creator);
        cv.put("mata_kuliah", mataKuliah);
        cv.put("questions_json", questionsJson);
        cv.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_ROOMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String getRoomQuestionsJson(String roomId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_ROOMS, new String[]{"questions_json"}, "room_id = ?",
                new String[]{roomId}, null, null, null);
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    // ========== USER ==========
    public long insertUser(String nama) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USER_NAMA, nama);
        values.put(COL_USER_POIN, 0);
        values.put(COL_USER_MENANG, 0);
        values.put(COL_USER_KALAH, 0);
        values.put(COL_USER_MAIN, 0);
        return db.insert(TABLE_USERS, null, values);
    }

    public User getUserByName(String nama) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, "LOWER(" + COL_USER_NAMA + ") = LOWER(?)", new String[]{nama}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            User user = new User(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAMA)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_POIN)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_MENANG)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_KALAH)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_MAIN))
            );
            cursor.close();
            return user;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public void updateUserAfterBattle(String nama, boolean isWin, int poinTambahan) {
        SQLiteDatabase db = this.getWritableDatabase();
        User user = getUserByName(nama);
        if (user != null) {
            ContentValues values = new ContentValues();
            values.put(COL_USER_POIN, user.getTotalPoin() + poinTambahan);
            values.put(COL_USER_MAIN, user.getJumlahMain() + 1);
            if (isWin) {
                values.put(COL_USER_MENANG, user.getJumlahMenang() + 1);
            } else {
                values.put(COL_USER_KALAH, user.getJumlahKalah() + 1);
            }
            db.update(TABLE_USERS, values, "LOWER(" + COL_USER_NAMA + ") = LOWER(?)", new String[]{nama});
        }
    }

    // ========== RANKING ==========
    public List<Ranking> getAllRanking() {
        List<Ranking> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS + " ORDER BY " + COL_USER_POIN + " DESC", null);
        if (cursor.moveToFirst()) {
            do {
                Ranking ranking = new Ranking(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAMA)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_POIN)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_MENANG))
                );
                list.add(ranking);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // ========== HISTORY ==========
    public void insertHistory(String userName, String lawan, String mataKuliah, String hasil, int poin, String tanggal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_HISTORY_USER_NAME, userName);
        values.put(COL_HISTORY_LAWAN, lawan);
        values.put(COL_HISTORY_MATKUL, mataKuliah);
        values.put(COL_HISTORY_HASIL, hasil);
        values.put(COL_HISTORY_POIN, poin);
        values.put(COL_HISTORY_TANGGAL, tanggal);
        db.insert(TABLE_HISTORY, null, values);
    }

    public List<BattleHistory> getHistoryByUser(String userName) {
        List<BattleHistory> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        if (userName == null || userName.trim().isEmpty()) {
            return getAllHistory();
        }

        // Migrasi otomatis untuk riwayat pertandingan lama yang nilai user_name nya masih NULL
        try {
            SQLiteDatabase wdb = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(COL_HISTORY_USER_NAME, userName);
            wdb.update(TABLE_HISTORY, cv, COL_HISTORY_USER_NAME + " IS NULL OR " + COL_HISTORY_USER_NAME + " = ''", null);
        } catch (Exception ignored) {}

        String selection = "LOWER(" + COL_HISTORY_USER_NAME + ") = LOWER(?) OR " + COL_HISTORY_USER_NAME + " IS NULL";
        String[] selectionArgs = new String[]{userName.trim()};

        Cursor cursor = db.query(TABLE_HISTORY, null, selection, selectionArgs, null, null, COL_HISTORY_ID + " DESC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                BattleHistory history = new BattleHistory(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_HISTORY_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_LAWAN)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_MATKUL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_HASIL)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_HISTORY_POIN)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_TANGGAL))
                );
                list.add(history);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public List<BattleHistory> getAllHistory() {
        List<BattleHistory> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + COL_HISTORY_ID + " DESC", null);
        if (cursor.moveToFirst()) {
            do {
                BattleHistory history = new BattleHistory(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_HISTORY_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_LAWAN)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_MATKUL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_HASIL)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_HISTORY_POIN)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_TANGGAL))
                );
                list.add(history);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // ========== FRIENDS & SEARCH ==========
    public List<User> searchUsersByNickname(String query, String currentUserName) {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = COL_USER_NAMA + " LIKE ? AND LOWER(" + COL_USER_NAMA + ") != LOWER(?)";
        String[] selectionArgs = new String[]{"%" + query + "%", currentUserName};

        Cursor cursor = db.query(TABLE_USERS, null, selection, selectionArgs, null, null, COL_USER_NAMA + " ASC");
        if (cursor != null && cursor.moveToFirst()) {
            do {
                User user = new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAMA)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_POIN)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_MENANG)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_KALAH)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_MAIN))
                );
                list.add(user);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public boolean addFriend(String currentUserName, String friendName) {
        if (isFriend(currentUserName, friendName)) {
            return false;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FRIEND_USER_NAME, currentUserName);
        values.put(COL_FRIEND_NAME, friendName);
        long res = db.insert(TABLE_FRIENDS, null, values);
        return res != -1;
    }

    public boolean isFriend(String currentUserName, String friendName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FRIENDS, null,
                "LOWER(" + COL_FRIEND_USER_NAME + ") = LOWER(?) AND LOWER(" + COL_FRIEND_NAME + ") = LOWER(?)",
                new String[]{currentUserName, friendName}, null, null, null);
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) cursor.close();
        return exists;
    }
}