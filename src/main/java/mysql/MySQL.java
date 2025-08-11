package mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import mysql.annotations.Table;
import mysql.core.Auth;
import mysql.core.ClasspathScanner;
import mysql.core.SchemaManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MySQL {

    private static HikariDataSource ds;

    public static void init(Auth auth, String basePackage) {
        String host = auth.getHost();
        int port = auth.getPort();
        String database = auth.getDatabase();
        String user = auth.getUser();
        String password = auth.getPassword();
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        ds = new HikariDataSource(config);

        try {
            List<Class<?>> modelClasses = ClasspathScanner.findClasses(basePackage, Table.class);

            SchemaManager schemaManager = new SchemaManager(modelClasses);
            schemaManager.migrate();

        } catch (Exception e) {
            System.err.println("Falha ao inicializar o framework de persistência.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (ds == null) {
            throw new SQLException("O pool de conexões não foi inicializado. Chame MySQL.init() primeiro.");
        }
        return ds.getConnection();
    }
}