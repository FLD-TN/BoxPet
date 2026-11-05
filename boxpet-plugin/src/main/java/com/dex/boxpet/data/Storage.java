package com.dex.boxpet.data;

import com.dex.boxpet.config.Settings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * SQLite (default) or MySQL through plain JDBC; Paper ships both drivers. Every query runs on one dedicated thread,
 * in submission order. Callers hand over copies ({@link Batch}) so the main thread keeps its objects.
 */
public final class Storage {

    /** What one save writes. All objects are copies owned by the storage thread. */
    public record Batch(UUID player, @Nullable UUID activePet, boolean hideOthers, boolean petChat,
                        List<PetData> pets, @Nullable List<IncubatorSlot> incubator,
                        @Nullable Map<String, Integer> collection, long xpDay, @Nullable Map<String, Double> dailyXp) {
    }

    private final Logger log;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String password;
    private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BoxPet-Storage");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public Storage(Settings s, File dataFolder, Logger log) {
        this.log = log;
        this.mysql = s.storageType.equals("mysql");
        if (mysql) {
            url = "jdbc:mysql://" + s.mysqlHost + ":" + s.mysqlPort + "/" + s.mysqlDatabase
                    + (s.mysqlParams.isEmpty() ? "" : "?" + s.mysqlParams);
            user = s.mysqlUser;
            password = s.mysqlPassword;
        } else {
            url = "jdbc:sqlite:" + new File(dataFolder, s.sqliteFile).getAbsolutePath();
            user = null;
            password = null;
        }
    }

    /** Opens the database and creates the tables (blocking, on the storage thread). */
    public void init() throws SQLException {
        try {
            thread.submit(() -> {
                Connection c = connection();
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS boxpet_pets (pet_uuid VARCHAR(36) PRIMARY KEY,"
                            + " owner_uuid VARCHAR(36) NOT NULL, type_id VARCHAR(64) NOT NULL, rarity VARCHAR(32) NOT NULL,"
                            + " level INT NOT NULL, xp DOUBLE NOT NULL, stage INT NOT NULL, custom_name VARCHAR(128),"
                            + " bond INT NOT NULL, fullness INT NOT NULL, bond_gain_at BIGINT NOT NULL,"
                            + " neglect_ms BIGINT NOT NULL, neglect_steps INT NOT NULL, active_since BIGINT NOT NULL,"
                            + " drain_ms BIGINT NOT NULL, food_bond_day BIGINT NOT NULL, food_bond INT NOT NULL,"
                            + " hatched_at BIGINT NOT NULL, state VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL,"
                            + " origin VARCHAR(32) NOT NULL)");
                    st.executeUpdate("CREATE INDEX " + (mysql ? "" : "IF NOT EXISTS ") + "boxpet_pets_owner ON boxpet_pets (owner_uuid)");
                } catch (SQLException ex) {
                    if (!mysql || !ex.getMessage().toLowerCase().contains("duplicate")) throw ex;
                }
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS boxpet_players (uuid VARCHAR(36) PRIMARY KEY,"
                            + " active_pet VARCHAR(36), hide_others INT NOT NULL, pet_chat INT NOT NULL,"
                            + " xp_day BIGINT NOT NULL)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS boxpet_incubator (uuid VARCHAR(36) NOT NULL, slot INT NOT NULL,"
                            + " random_egg INT NOT NULL, pet_type VARCHAR(64), rarity VARCHAR(32) NOT NULL,"
                            + " started_at BIGINT NOT NULL, finish_at BIGINT NOT NULL, PRIMARY KEY (uuid, slot))");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS boxpet_collection (uuid VARCHAR(36) NOT NULL,"
                            + " type_id VARCHAR(64) NOT NULL, best_rarity INT NOT NULL, PRIMARY KEY (uuid, type_id))");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS boxpet_daily_xp (uuid VARCHAR(36) NOT NULL,"
                            + " source VARCHAR(32) NOT NULL, amount DOUBLE NOT NULL, PRIMARY KEY (uuid, source))");
                }
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new SQLException("BoxPet database init failed: " + ex.getMessage(), ex);
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed() && (!mysql || connection.isValid(2))) return connection;
            } catch (SQLException ignored) {
                // reconnect below
            }
        }
        connection = mysql ? DriverManager.getConnection(url, user, password) : DriverManager.getConnection(url);
        if (!mysql) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
        }
        return connection;
    }

    // ------------------------------------------------------------------ load

    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return readPlayer(uuid);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }, thread);
    }

    private PlayerData readPlayer(UUID uuid) throws SQLException {
        Connection c = connection();
        PlayerData data = new PlayerData(uuid);
        String id = uuid.toString();
        try (PreparedStatement ps = c.prepareStatement("SELECT active_pet, hide_others, pet_chat, xp_day FROM boxpet_players WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String active = rs.getString(1);
                    data.activePet = active == null || active.isEmpty() ? null : UUID.fromString(active);
                    data.hideOthers = rs.getInt(2) != 0;
                    data.petChat = rs.getInt(3) != 0;
                    data.xpDay = rs.getLong(4);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM boxpet_pets WHERE owner_uuid = ? AND state = ? ORDER BY created_at")) {
            ps.setString(1, id);
            ps.setString(2, PetData.State.COLLECTION.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PetData p = readPet(rs);
                    data.pets.put(p.id, p);
                }
            }
        }
        if (data.activePet != null && !data.pets.containsKey(data.activePet)) data.activePet = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT slot, random_egg, pet_type, rarity, started_at, finish_at FROM boxpet_incubator WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    IncubatorSlot s = new IncubatorSlot(rs.getInt(1), rs.getInt(2) != 0, rs.getString(3) == null ? "" : rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getLong(6));
                    data.incubator.put(s.slot(), s);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT type_id, best_rarity FROM boxpet_collection WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.collection.put(rs.getString(1), rs.getInt(2));
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT source, amount FROM boxpet_daily_xp WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.dailyXp.put(rs.getString(1), rs.getDouble(2));
            }
        }
        return data;
    }

    private static PetData readPet(ResultSet rs) throws SQLException {
        PetData p = new PetData(UUID.fromString(rs.getString("pet_uuid")));
        p.owner = UUID.fromString(rs.getString("owner_uuid"));
        p.type = rs.getString("type_id");
        p.rarity = rs.getString("rarity");
        p.level = rs.getInt("level");
        p.xp = rs.getDouble("xp");
        p.stage = rs.getInt("stage");
        String name = rs.getString("custom_name");
        p.customName = name == null ? "" : name;
        p.bond = rs.getInt("bond");
        p.fullness = rs.getInt("fullness");
        p.bondGainAt = rs.getLong("bond_gain_at");
        p.neglectMs = rs.getLong("neglect_ms");
        p.neglectSteps = rs.getInt("neglect_steps");
        p.activeSince = rs.getLong("active_since");
        p.drainMs = rs.getLong("drain_ms");
        p.foodBondDay = rs.getLong("food_bond_day");
        p.foodBond = rs.getInt("food_bond");
        p.hatchedAt = rs.getLong("hatched_at");
        try {
            p.state = PetData.State.valueOf(rs.getString("state"));
        } catch (IllegalArgumentException ex) {
            p.state = PetData.State.COLLECTION;
        }
        p.createdAt = rs.getLong("created_at");
        p.origin = rs.getString("origin");
        return p;
    }

    /** Pets whose id starts with {@code prefix} (any state, any owner), at most 10. */
    public CompletableFuture<List<PetData>> findPets(String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            List<PetData> out = new ArrayList<>();
            try (PreparedStatement ps = connection().prepareStatement("SELECT * FROM boxpet_pets WHERE pet_uuid LIKE ? LIMIT 10")) {
                ps.setString(1, prefix.toLowerCase().replace("%", "").replace("_", "") + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(readPet(rs));
                }
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
            return out;
        }, thread);
    }

    /** Deleted pets of an owner, newest first (admin restore). */
    public CompletableFuture<List<PetData>> deletedPets(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            List<PetData> out = new ArrayList<>();
            try (PreparedStatement ps = connection().prepareStatement(
                    "SELECT * FROM boxpet_pets WHERE owner_uuid = ? AND state = ? ORDER BY created_at DESC LIMIT 45")) {
                ps.setString(1, owner.toString());
                ps.setString(2, PetData.State.DELETED.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(readPet(rs));
                }
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
            return out;
        }, thread);
    }

    // ------------------------------------------------------------------ save

    public CompletableFuture<Void> save(Batch batch) {
        return CompletableFuture.runAsync(() -> {
            try {
                write(batch);
            } catch (SQLException ex) {
                log.log(Level.SEVERE, "Could not save BoxPet data of " + batch.player(), ex);
            }
        }, thread);
    }

    /** Saves pets only (offline edits by staff). */
    public CompletableFuture<Void> savePets(List<PetData> pets) {
        return save(new Batch(null, null, false, true, pets, null, null, 0, null));
    }

    /** Sets a player's active pet row without loading the player (staff on an offline owner). */
    public CompletableFuture<Void> clearActiveIf(UUID player, UUID pet) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection().prepareStatement("UPDATE boxpet_players SET active_pet = NULL WHERE uuid = ? AND active_pet = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, pet.toString());
                ps.executeUpdate();
            } catch (SQLException ex) {
                log.log(Level.SEVERE, "Could not update active pet of " + player, ex);
            }
        }, thread);
    }

    private void write(Batch b) throws SQLException {
        Connection c = connection();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            if (!b.pets().isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO boxpet_pets (pet_uuid, owner_uuid, type_id, rarity,"
                        + " level, xp, stage, custom_name, bond, fullness, bond_gain_at, neglect_ms, neglect_steps, active_since,"
                        + " drain_ms, food_bond_day, food_bond, hatched_at, state, created_at, origin)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (PetData p : b.pets()) {
                        ps.setString(1, p.id.toString());
                        ps.setString(2, p.owner.toString());
                        ps.setString(3, p.type);
                        ps.setString(4, p.rarity);
                        ps.setInt(5, p.level);
                        ps.setDouble(6, p.xp);
                        ps.setInt(7, p.stage);
                        ps.setString(8, p.customName);
                        ps.setInt(9, p.bond);
                        ps.setInt(10, p.fullness);
                        ps.setLong(11, p.bondGainAt);
                        ps.setLong(12, p.neglectMs);
                        ps.setInt(13, p.neglectSteps);
                        ps.setLong(14, p.activeSince);
                        ps.setLong(15, p.drainMs);
                        ps.setLong(16, p.foodBondDay);
                        ps.setInt(17, p.foodBond);
                        ps.setLong(18, p.hatchedAt);
                        ps.setString(19, p.state.name());
                        ps.setLong(20, p.createdAt);
                        ps.setString(21, p.origin);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (b.player() != null) {
                String id = b.player().toString();
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO boxpet_players (uuid, active_pet, hide_others, pet_chat, xp_day) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, b.activePet() == null ? null : b.activePet().toString());
                    ps.setInt(3, b.hideOthers() ? 1 : 0);
                    ps.setInt(4, b.petChat() ? 1 : 0);
                    ps.setLong(5, b.xpDay());
                    ps.executeUpdate();
                }
                if (b.incubator() != null) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM boxpet_incubator WHERE uuid = ?")) {
                        del.setString(1, id);
                        del.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO boxpet_incubator (uuid, slot, random_egg, pet_type, rarity, started_at, finish_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        for (IncubatorSlot s : b.incubator()) {
                            ps.setString(1, id);
                            ps.setInt(2, s.slot());
                            ps.setInt(3, s.random() ? 1 : 0);
                            ps.setString(4, s.petType());
                            ps.setString(5, s.rarity());
                            ps.setLong(6, s.startedAt());
                            ps.setLong(7, s.finishAt());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (b.collection() != null && !b.collection().isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement("REPLACE INTO boxpet_collection (uuid, type_id, best_rarity) VALUES (?, ?, ?)")) {
                        for (Map.Entry<String, Integer> e : b.collection().entrySet()) {
                            ps.setString(1, id);
                            ps.setString(2, e.getKey());
                            ps.setInt(3, e.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (b.dailyXp() != null) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM boxpet_daily_xp WHERE uuid = ?")) {
                        del.setString(1, id);
                        del.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO boxpet_daily_xp (uuid, source, amount) VALUES (?, ?, ?)")) {
                        for (Map.Entry<String, Double> e : b.dailyXp().entrySet()) {
                            ps.setString(1, id);
                            ps.setString(2, e.getKey());
                            ps.setDouble(3, e.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** Waits for pending writes, then closes (plugin disable). */
    public void close() {
        thread.shutdown();
        try {
            if (!thread.awaitTermination(20, TimeUnit.SECONDS)) log.warning("BoxPet storage did not finish in 20 s");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
            // closing anyway
        }
    }
}
