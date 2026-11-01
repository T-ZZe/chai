package fr.chainsawrpg.data;

import fr.chainsawrpg.ChainsawPlugin;
import fr.chainsawrpg.Settings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite (défaut) ou MySQL. Une seule connexion, utilisée uniquement par un thread dédié :
 * aucune requête sur le thread principal (sauf attente explicite lors du chargement/arrêt).
 * Les pilotes JDBC SQLite/MySQL sont fournis par le serveur Paper.
 */
public final class DatabaseManager {
    public record HeadRecord(UUID id, UUID victim, String victimName, UUID killer, long createdAt) {}
    public record HeadLog(UUID victim, UUID killer, long ts) {}

    @FunctionalInterface public interface SqlTask { void run(Connection c) throws Exception; }
    @FunctionalInterface public interface SqlQuery<T> { T run(Connection c) throws Exception; }

    private final ChainsawPlugin pl;
    private ExecutorService ex;
    private Connection conn;
    private boolean mysql;

    public DatabaseManager(ChainsawPlugin pl) { this.pl = pl; }

    public void init() throws Exception {
        mysql = pl.settings.dbType.equalsIgnoreCase("mysql");
        ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChainsawRPG-DB");
            t.setDaemon(true);
            return t;
        });
        ex.submit((Callable<Void>) () -> {
            connect();
            createTables();
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    private void connect() throws Exception {
        Settings s = pl.settings;
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://" + s.myHost + ":" + s.myPort + "/" + s.myDb + "?" + s.myParams, s.myUser, s.myPass);
        } else {
            Class.forName("org.sqlite.JDBC");
            File f = new File(pl.getDataFolder(), s.sqliteFile);
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
        }
    }

    private Connection c() throws SQLException {
        try {
            if (conn == null || conn.isClosed() || (mysql && !conn.isValid(2))) connect();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Connexion DB impossible", e);
        }
        return conn;
    }

    private void createTables() throws SQLException {
        try (Statement st = c().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS cs_players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32), active_demon VARCHAR(48), "
                    + "blood INT, playtime_ms BIGINT, first_seen BIGINT, mob_kills INT, demon_kills INT, pvp_wins INT, boss_kills INT, skills_used INT)");
            st.execute("CREATE TABLE IF NOT EXISTS cs_contracts (uuid VARCHAR(36), demon_id VARCHAR(48), level INT, xp BIGINT, awakened INT, "
                    + "acquired_at BIGINT, PRIMARY KEY (uuid, demon_id))");
            st.execute("CREATE TABLE IF NOT EXISTS cs_heads (id VARCHAR(36) PRIMARY KEY, victim_uuid VARCHAR(36), victim_name VARCHAR(32), "
                    + "killer_uuid VARCHAR(36), created_at BIGINT, status VARCHAR(12), consumed_by VARCHAR(36), consumed_at BIGINT)");
            st.execute("CREATE TABLE IF NOT EXISTS cs_head_log (victim_uuid VARCHAR(36), killer_uuid VARCHAR(36), ts BIGINT)");
        }
    }

    // ---------- infra ----------
    public void async(SqlTask t) {
        if (ex == null || ex.isShutdown()) return;
        ex.execute(() -> {
            try {
                t.run(c());
            } catch (Exception e) {
                pl.getLogger().log(Level.SEVERE, "Erreur base de donnees", e);
            }
        });
    }

    public <T> T query(SqlQuery<T> q) throws Exception {
        return ex.submit(() -> q.run(c())).get(20, TimeUnit.SECONDS);
    }

    public void close() {
        if (ex == null) return;
        ex.shutdown();
        try {
            if (!ex.awaitTermination(15, TimeUnit.SECONDS)) pl.getLogger().warning("Ecritures DB non terminees a l'arret.");
            if (conn != null) conn.close();
        } catch (Exception e) {
            pl.getLogger().log(Level.WARNING, "Fermeture DB", e);
        }
    }

    // ---------- joueurs ----------
    public PlayerData loadPlayer(UUID uuid, String name) throws Exception {
        return query(c -> {
            PlayerData d = new PlayerData(uuid, name);
            try (PreparedStatement ps = c.prepareStatement("SELECT active_demon,blood,playtime_ms,first_seen,mob_kills,demon_kills,pvp_wins,boss_kills,skills_used FROM cs_players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        d.activeDemon = rs.getString(1);
                        d.blood = rs.getInt(2);
                        d.playtimeMs = rs.getLong(3);
                        d.firstSeen = rs.getLong(4);
                        d.mobKills = rs.getInt(5);
                        d.demonKills = rs.getInt(6);
                        d.pvpWins = rs.getInt(7);
                        d.bossKills = rs.getInt(8);
                        d.skillsUsed = rs.getInt(9);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT demon_id,level,xp,awakened,acquired_at FROM cs_contracts WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PlayerData.Contract ct = new PlayerData.Contract(rs.getString(1));
                        ct.level = rs.getInt(2);
                        ct.xp = rs.getLong(3);
                        ct.awakened = rs.getInt(4) != 0;
                        ct.acquiredAt = rs.getLong(5);
                        d.contracts.put(ct.demonId, ct);
                    }
                }
            }
            if (d.activeDemon != null && !d.contracts.containsKey(d.activeDemon)) d.activeDemon = null;
            return d;
        });
    }

    /** d doit être une copie (PlayerData.copy()) : elle est lue depuis le thread DB. */
    public void savePlayer(PlayerData d) {
        async(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO cs_players (uuid,name,active_demon,blood,playtime_ms,first_seen,mob_kills,demon_kills,pvp_wins,boss_kills,skills_used) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, d.uuid.toString());
                    ps.setString(2, d.name);
                    ps.setString(3, d.activeDemon);
                    ps.setInt(4, d.blood);
                    ps.setLong(5, d.playtimeMs);
                    ps.setLong(6, d.firstSeen);
                    ps.setInt(7, d.mobKills);
                    ps.setInt(8, d.demonKills);
                    ps.setInt(9, d.pvpWins);
                    ps.setInt(10, d.bossKills);
                    ps.setInt(11, d.skillsUsed);
                    ps.executeUpdate();
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM cs_contracts WHERE uuid=?")) {
                    del.setString(1, d.uuid.toString());
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO cs_contracts (uuid,demon_id,level,xp,awakened,acquired_at) VALUES (?,?,?,?,?,?)")) {
                    for (PlayerData.Contract ct : d.contracts.values()) {
                        ps.setString(1, d.uuid.toString());
                        ps.setString(2, ct.demonId);
                        ps.setInt(3, ct.level);
                        ps.setLong(4, ct.xp);
                        ps.setInt(5, ct.awakened ? 1 : 0);
                        ps.setLong(6, ct.acquiredAt);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    // ---------- tetes ----------
    public void insertHead(HeadRecord h) {
        async(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO cs_heads (id,victim_uuid,victim_name,killer_uuid,created_at,status) VALUES (?,?,?,?,?,'ACTIVE')")) {
                ps.setString(1, h.id().toString());
                ps.setString(2, h.victim().toString());
                ps.setString(3, h.victimName());
                ps.setString(4, h.killer().toString());
                ps.setLong(5, h.createdAt());
                ps.executeUpdate();
            }
        });
    }

    public void consumeHead(UUID id, UUID consumer) {
        async(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cs_heads SET status='CONSUMED', consumed_by=?, consumed_at=? WHERE id=?")) {
                ps.setString(1, consumer == null ? null : consumer.toString());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, id.toString());
                ps.executeUpdate();
            }
        });
    }

    public Map<UUID, HeadRecord> loadActiveHeads() throws Exception {
        return query(c -> {
            Map<UUID, HeadRecord> out = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id,victim_uuid,victim_name,killer_uuid,created_at FROM cs_heads WHERE status='ACTIVE'")) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    out.put(id, new HeadRecord(id, UUID.fromString(rs.getString(2)), rs.getString(3), UUID.fromString(rs.getString(4)), rs.getLong(5)));
                }
            }
            return out;
        });
    }

    public void logHead(UUID victim, UUID killer, long ts) {
        async(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cs_head_log (victim_uuid,killer_uuid,ts) VALUES (?,?,?)")) {
                ps.setString(1, victim.toString());
                ps.setString(2, killer.toString());
                ps.setLong(3, ts);
                ps.executeUpdate();
            }
        });
    }

    public List<HeadLog> loadHeadLog(long sinceMs) throws Exception {
        return query(c -> {
            List<HeadLog> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT victim_uuid,killer_uuid,ts FROM cs_head_log WHERE ts>=?")) {
                ps.setLong(1, sinceMs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new HeadLog(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getLong(3)));
                }
            }
            return out;
        });
    }

    /** Purge le journal ancien (appelé à l'enable). */
    public void purgeHeadLog(long beforeMs) {
        async(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cs_head_log WHERE ts<?")) {
                ps.setLong(1, beforeMs);
                ps.executeUpdate();
            }
        });
    }
}
