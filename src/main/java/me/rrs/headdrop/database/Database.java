package me.rrs.headdrop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.dejvokep.boostedyaml.YamlDocument;
import me.rrs.headdrop.HeadDrop;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.*;

public class Database {

    private final YamlDocument config = HeadDrop.getInstance().getConfiguration();
    private boolean isSQLite;
    private HikariDataSource dataSource;


    private String getCurrentTimestampFunction() {
        return isSQLite ? "datetime('now')" : "NOW()";
    }

    public void createTable() {
        String createTableSQL;
        if (isSQLite) {
            createTableSQL = "CREATE TABLE IF NOT EXISTS headdrop ("
                    + "name TEXT, "
                    + "uuid TEXT, "
                    + "data INTEGER, "
                    + "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (name, uuid)"
                    + ");";
        } else {
            createTableSQL = "CREATE TABLE IF NOT EXISTS headdrop ("
                    + "name VARCHAR(16), "
                    + "uuid VARCHAR(36), "
                    + "data INT, "
                    + "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (name, uuid)"
                    + ");";
        }

        String createBountiesSQL = "CREATE TABLE IF NOT EXISTS premium_bounties (" +
                "uuid VARCHAR(36), " +
                "mob_type VARCHAR(32), " +
                "kills INTEGER DEFAULT 0, " +
                "completed BOOLEAN DEFAULT FALSE, " +
                "PRIMARY KEY (uuid, mob_type)" +
                ");";

        String createAchievementsSQL = "CREATE TABLE IF NOT EXISTS premium_achievements (" +
                "uuid VARCHAR(36), " +
                "achievement_id VARCHAR(64), " +
                "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (uuid, achievement_id)" +
                ");";

        String createMarketSQL = isSQLite ?
                "CREATE TABLE IF NOT EXISTS premium_market (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "seller_uuid VARCHAR(36), " +
                        "seller_name VARCHAR(16), " +
                        "head_type VARCHAR(64), " +
                        "price DOUBLE, " +
                        "status VARCHAR(16) DEFAULT 'ACTIVE', " +
                        "category VARCHAR(32) DEFAULT 'MOB', " +
                        "buyer_name VARCHAR(16), " +
                        "notified BOOLEAN DEFAULT FALSE, " +
                        "listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ");" :
                "CREATE TABLE IF NOT EXISTS premium_market (" +
                        "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                        "seller_uuid VARCHAR(36), " +
                        "seller_name VARCHAR(16), " +
                        "head_type VARCHAR(64), " +
                        "price DOUBLE, " +
                        "status VARCHAR(16) DEFAULT 'ACTIVE', " +
                        "category VARCHAR(32) DEFAULT 'MOB', " +
                        "buyer_name VARCHAR(16), " +
                        "notified BOOLEAN DEFAULT FALSE, " +
                        "listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ");";

        // Execute table creations independently for max compatibility on upgrades
        executeTableCreation(createTableSQL);
        executeTableCreation(createBountiesSQL);
        executeTableCreation(createAchievementsSQL);
        executeTableCreation(createMarketSQL);

        addMissingColumns();
    }

    private void executeTableCreation(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addMissingColumns() {
        try (Connection connection = dataSource.getConnection()) {
            boolean hasLastUpdated = false;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM headdrop LIMIT 1")) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    if ("last_updated".equalsIgnoreCase(metaData.getColumnName(i))) {
                        hasLastUpdated = true;
                        break;
                    }
                }
            }

            if (!hasLastUpdated) {
                if (isSQLite) {
                    String alterTableQuery = "ALTER TABLE headdrop ADD COLUMN last_updated TIMESTAMP";
                    try (PreparedStatement alterStmt = connection.prepareStatement(alterTableQuery)) {
                        alterStmt.execute();
                        System.out.println("Column 'last_updated' added to SQLite database.");
                    }
                    String updateQuery = "UPDATE headdrop SET last_updated = datetime('now') WHERE last_updated IS NULL";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                        updateStmt.execute();
                    }
                } else {
                    String alterTableQuery = "ALTER TABLE headdrop ADD COLUMN last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
                    try (PreparedStatement alterStmt = connection.prepareStatement(alterTableQuery)) {
                        alterStmt.execute();
                        System.out.println("Column 'last_updated' added to MySQL database.");
                    }
                }
            }

            // Migration check for premium_market table columns
            boolean hasMarketStatus = false;
            boolean hasMarketCategory = false;
            boolean hasMarketBuyer = false;
            boolean hasMarketNotified = false;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM premium_market LIMIT 1")) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    if ("status".equalsIgnoreCase(metaData.getColumnName(i))) hasMarketStatus = true;
                    if ("category".equalsIgnoreCase(metaData.getColumnName(i))) hasMarketCategory = true;
                    if ("buyer_name".equalsIgnoreCase(metaData.getColumnName(i))) hasMarketBuyer = true;
                    if ("notified".equalsIgnoreCase(metaData.getColumnName(i))) hasMarketNotified = true;
                }
            } catch (SQLException ignored) {}

            if (!hasMarketStatus) {
                try (PreparedStatement alterStmt = connection.prepareStatement("ALTER TABLE premium_market ADD COLUMN status VARCHAR(16) DEFAULT 'ACTIVE'")) {
                    alterStmt.execute();
                } catch (SQLException ignored) {}
            }
            if (!hasMarketCategory) {
                try (PreparedStatement alterStmt = connection.prepareStatement("ALTER TABLE premium_market ADD COLUMN category VARCHAR(32) DEFAULT 'MOB'")) {
                    alterStmt.execute();
                } catch (SQLException ignored) {}
            }
            if (!hasMarketBuyer) {
                try (PreparedStatement alterStmt = connection.prepareStatement("ALTER TABLE premium_market ADD COLUMN buyer_name VARCHAR(16)")) {
                    alterStmt.execute();
                } catch (SQLException ignored) {}
            }
            if (!hasMarketNotified) {
                try (PreparedStatement alterStmt = connection.prepareStatement("ALTER TABLE premium_market ADD COLUMN notified BOOLEAN DEFAULT FALSE")) {
                    alterStmt.execute();
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            System.out.println("Error verifying database column migrations.");
            e.printStackTrace();
        }
    }

    public int getDataByUuid(String uuid) {
        String query = "SELECT data FROM headdrop WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("data");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public Set<String> getCollectedHeadNames(String uuid) {
        Set<String> collected = new HashSet<>();
        String query = "SELECT name FROM headdrop WHERE uuid = ? AND data > 0";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    collected.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return collected;
    }

    public Integer getDataByName(String name) {
        String query = "SELECT data FROM headdrop WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("data");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void updateDataByUuid(String uuid, String name, int data) {
        String selectSQL = "SELECT * FROM headdrop WHERE uuid = ?";
        String insertSQL = "INSERT INTO headdrop (name, uuid, data, last_updated) VALUES (?, ?, ?, " + getCurrentTimestampFunction() + ")";
        String updateSQL = "UPDATE headdrop SET name = ?, data = ?, last_updated = " + getCurrentTimestampFunction() + " WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement selectStatement = connection.prepareStatement(selectSQL)) {

            selectStatement.setString(1, uuid);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
                        updateStatement.setString(1, name);
                        updateStatement.setInt(2, data);
                        updateStatement.setString(3, uuid);
                        updateStatement.executeUpdate();
                    }
                } else {
                    // Row doesn't exist, insert it
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSQL)) {
                        insertStatement.setString(1, name);
                        insertStatement.setString(2, uuid);
                        insertStatement.setInt(3, data);
                        insertStatement.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateDataByName(String name, int data) {
        String selectSQL = "SELECT * FROM headdrop WHERE name = ?";
        String insertSQL = "INSERT INTO headdrop (name, uuid, data, last_updated) VALUES (?, ?, ?, " + getCurrentTimestampFunction() + ")";
        String updateSQL = "UPDATE headdrop SET data = ?, last_updated = " + getCurrentTimestampFunction() + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement selectStatement = connection.prepareStatement(selectSQL)) {

            selectStatement.setString(1, name);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    // Row exists, update it
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSQL)) {
                        updateStatement.setInt(1, data);
                        updateStatement.setString(2, name);
                        updateStatement.executeUpdate();
                    }
                } else {
                    // Row doesn't exist, insert it.
                    // Note: Ensure that Bukkit.getPlayer(name) is not null. Otherwise, handle the null case.
                    UUID uuid = Bukkit.getPlayer(name).getUniqueId();
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSQL)) {
                        insertStatement.setString(1, name);
                        insertStatement.setString(2, uuid.toString());
                        insertStatement.setInt(3, data);
                        insertStatement.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> getPlayerData() {
        Map<String, Integer> playerData = new HashMap<>();
        String query = "SELECT name, data FROM headdrop";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String name = result.getString("name");
                int data = result.getInt("data");
                playerData.put(name, data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playerData;
    }

    public void cleanupOldData(int days) {
        String query;
        if (isSQLite) {
            query = "DELETE FROM headdrop WHERE last_updated < datetime('now', '-' || ? || ' days')";
        } else {
            query = "DELETE FROM headdrop WHERE last_updated < NOW() - INTERVAL ? DAY";
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, days);
            int rowsDeleted = statement.executeUpdate();
            System.out.println("Cleaned up " + rowsDeleted + " rows older than " + days + " days.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Bounties
    public int getBountyProgress(UUID playerUuid, String mobType) {
        String query = "SELECT kills FROM premium_bounties WHERE uuid = ? AND mob_type = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, mobType);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("kills");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean isBountyCompleted(UUID playerUuid, String mobType) {
        String query = "SELECT completed FROM premium_bounties WHERE uuid = ? AND mob_type = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, mobType);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("completed");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateBountyProgress(UUID playerUuid, String mobType, int kills, boolean completed) {
        if (getBountyProgress(playerUuid, mobType) == 0 && kills >= 0) {
            String insert = "INSERT INTO premium_bounties (uuid, mob_type, kills, completed) VALUES (?, ?, ?, ?)";
            execute(insert, playerUuid.toString(), mobType, kills, completed);
        } else {
            String update = "UPDATE premium_bounties SET kills = ?, completed = ? WHERE uuid = ? AND mob_type = ?";
            execute(update, kills, completed, playerUuid.toString(), mobType);
        }
    }

    public void removeBountyData(String mobType) {
        String delete = "DELETE FROM premium_bounties WHERE mob_type = ?";
        execute(delete, mobType);
    }

    public void clearAllBountyData() {
        String delete = "DELETE FROM premium_bounties";
        execute(delete);
    }

    // Achievements
    public boolean isAchievementUnlocked(UUID playerUuid, String achievementId) {
        String query = "SELECT 1 FROM premium_achievements WHERE uuid = ? AND achievement_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, achievementId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void unlockAchievement(UUID playerUuid, String achievementId) {
        if (!isAchievementUnlocked(playerUuid, achievementId)) {
            String insert = "INSERT INTO premium_achievements (uuid, achievement_id) VALUES (?, ?)";
            execute(insert, playerUuid.toString(), achievementId);
        }
    }

    public Set<String> getUnlockedAchievements(UUID playerUuid) {
        Set<String> unlocked = new HashSet<>();
        String query = "SELECT achievement_id FROM premium_achievements WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    unlocked.add(rs.getString("achievement_id"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return unlocked;
    }

    // Market
    public record MarketEntry(int id, UUID sellerUuid, String sellerName, String headType, double price, String status, String category) {}

    public void addMarketListing(UUID sellerUuid, String sellerName, String headType, double price, String category) {
        String insert = "INSERT INTO premium_market (seller_uuid, seller_name, head_type, price, status, category) VALUES (?, ?, ?, ?, 'ACTIVE', ?)";
        execute(insert, sellerUuid.toString(), sellerName, headType, price, category);
    }

    public void removeMarketListing(int id) {
        String delete = "DELETE FROM premium_market WHERE id = ?";
        execute(delete, id);
    }

    public void updateMarketStatus(int id, String status) {
        String update = "UPDATE premium_market SET status = ? WHERE id = ?";
        execute(update, status, id);
    }

    public List<MarketEntry> getMarketListings() {
        List<MarketEntry> list = new ArrayList<>();
        String query = "SELECT id, seller_uuid, seller_name, head_type, price, status, category FROM premium_market WHERE status = 'ACTIVE' ORDER BY id DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String st = rs.getString("status");
                String cat = rs.getString("category");
                list.add(new MarketEntry(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("seller_uuid")),
                        rs.getString("seller_name"),
                        rs.getString("head_type"),
                        rs.getDouble("price"),
                        st != null ? st : "ACTIVE",
                        cat != null ? cat : "MOB"
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<MarketEntry> getClaimableListings(UUID sellerUuid) {
        List<MarketEntry> list = new ArrayList<>();
        String query = "SELECT id, seller_uuid, seller_name, head_type, price, status, category FROM premium_market WHERE seller_uuid = ? AND status IN ('EXPIRED', 'CANCELLED') ORDER BY id DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new MarketEntry(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("seller_name"),
                            rs.getString("head_type"),
                            rs.getDouble("price"),
                            rs.getString("status"),
                            rs.getString("category")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void markSold(int id, String buyerName) {
        String update = "UPDATE premium_market SET status = 'SOLD', buyer_name = ?, notified = FALSE WHERE id = ?";
        execute(update, buyerName, id);
    }

    public record SoldNotification(int id, String headType, double price, String buyerName) {}

    public List<SoldNotification> getUnnotifiedSales(UUID sellerUuid) {
        List<SoldNotification> list = new ArrayList<>();
        String query = "SELECT id, head_type, price, buyer_name FROM premium_market WHERE seller_uuid = ? AND status = 'SOLD' AND (notified IS NULL OR notified = FALSE)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new SoldNotification(
                            rs.getInt("id"),
                            rs.getString("head_type"),
                            rs.getDouble("price"),
                            rs.getString("buyer_name")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void markSaleNotified(int id) {
        String update = "UPDATE premium_market SET notified = TRUE WHERE id = ?";
        execute(update, id);
    }

    public int getPlayerListingCount(UUID sellerUuid) {
        String query = "SELECT COUNT(*) FROM premium_market WHERE seller_uuid = ? AND status = 'ACTIVE'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void expireOldListings(int days) {
        if (days <= 0) return;
        String query = isSQLite ?
                "UPDATE premium_market SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND listed_at < datetime('now', '-' || ? || ' days')" :
                "UPDATE premium_market SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND listed_at < NOW() - INTERVAL ? DAY";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, days);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void setupDataSource() {
        String connectionString = config.getString("Database.URL");
        String username = config.getString("Database.User");
        String password = config.getString("Database.Password");
        HikariConfig hikariConfig = new HikariConfig();

        if (connectionString.contains("mysql")) {
            isSQLite = false;
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setJdbcUrl(connectionString);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else if (connectionString.contains("sqlite")) {
            isSQLite = true;
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            File dataFolder = HeadDrop.getInstance().getDataFolder();
            File dbFile = new File(dataFolder, connectionString.replace("jdbc:sqlite:", ""));
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } else {
            throw new IllegalArgumentException("Unsupported database type");
        }

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(2000);

        this.dataSource = new HikariDataSource(hikariConfig);

        if (isSQLite) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL;");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
