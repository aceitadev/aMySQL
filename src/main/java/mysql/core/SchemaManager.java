package mysql.core;

import com.google.gson.Gson;
import mysql.MySQL;
import mysql.annotations.Column;
import mysql.annotations.Id;
import mysql.annotations.ManyToOne;
import mysql.annotations.Table;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class SchemaManager {

    private final List<Class<?>> modelClasses;
    private final Gson gson = new Gson();

    public SchemaManager(List<Class<?>> modelClasses) {
        this.modelClasses = modelClasses;
    }

    public void migrate() throws SQLException {
        for (Class<?> modelClass : modelClasses) {
            Table tableAnnotation = modelClass.getAnnotation(Table.class);
            if (tableAnnotation == null) continue;

            String tableName = tableAnnotation.value();
            Map<String, String> desiredColumns = getColumnsFromClass(modelClass);
            List<String> foreignKeys = getForeignKeysFromClass(modelClass);
            Map<String, String> existingColumns = getExistingColumns(tableName);

            if (existingColumns.isEmpty()) {
                createTable(tableName, desiredColumns, foreignKeys);
            } else {
                updateTable(tableName, desiredColumns, existingColumns);
            }
        }
    }

    private void createTable(String tableName, Map<String, String> columns, List<String> foreignKeys) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            sql.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
        }
        for (String fk : foreignKeys) {
            sql.append(fk).append(", ");
        }
        sql.delete(sql.length() - 2, sql.length()).append(");");

        try (Connection conn = MySQL.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        }
    }

    private void updateTable(String tableName, Map<String, String> desiredColumns, Map<String, String> existingColumns) throws SQLException {
        for (Map.Entry<String, String> entry : desiredColumns.entrySet()) {
            if (!existingColumns.containsKey(entry.getKey())) {
                StringBuilder sql = new StringBuilder("ALTER TABLE ").append(tableName)
                        .append(" ADD COLUMN ").append(entry.getKey()).append(" ").append(entry.getValue()).append(";");

                try (Connection conn = MySQL.getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql.toString());
                }
            }
        }
    }

    private List<String> getForeignKeysFromClass(Class<?> modelClass) {
        List<String> foreignKeys = new ArrayList<>();
        String ownTableName = modelClass.getAnnotation(Table.class).value();

        for (Field field : modelClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ManyToOne.class)) {
                String columnName = field.getName() + "_id";
                Class<?> relatedEntityType = field.getType();
                Table relatedTable = relatedEntityType.getAnnotation(Table.class);
                Field relatedIdField = findIdFieldInClass(relatedEntityType);

                if (relatedTable != null && relatedIdField != null) {
                    String fkConstraint = String.format("FOREIGN KEY (%s) REFERENCES %s(%s)",
                            columnName, relatedTable.value(), getColumnName(relatedIdField));
                    foreignKeys.add(fkConstraint);
                }
            }
        }
        return foreignKeys;
    }

    private Map<String, String> getColumnsFromClass(Class<?> modelClass) {
        Map<String, String> columns = new HashMap<>();
        for (Field field : modelClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ManyToOne.class)) {
                Class<?> relatedEntityType = field.getType();
                Field relatedIdField = findIdFieldInClass(relatedEntityType);
                if (relatedIdField != null) {
                    String columnName = field.getName() + "_id";
                    String columnType = getSimpleSqlType(relatedIdField.getType());
                    columns.put(columnName, columnType);
                }
                continue;
            }

            String columnName = getColumnName(field);
            if (columnName != null) {
                String columnType = getSimpleSqlType(field.getType());
                if (field.getType() == List.class) columnType = "TEXT";
                if (field.isAnnotationPresent(Id.class)) {
                    columnType += " PRIMARY KEY AUTO_INCREMENT";
                }
                columns.put(columnName, columnType);
            }
        }
        return columns;
    }

    private String getColumnName(Field field) {
        if (field.isAnnotationPresent(Column.class)) {
            return field.getAnnotation(Column.class).value();
        }
        if (field.isAnnotationPresent(Id.class)) {
            return "id";
        }
        return null;
    }

    private String getSimpleSqlType(Class<?> type) {
        if (type == int.class || type == Integer.class) return "INT";
        if (type == long.class || type == Long.class) return "BIGINT";
        if (type == String.class) return "VARCHAR(255)";
        if (type == UUID.class) return "VARCHAR(36)";
        return "TEXT";
    }

    private Field findIdFieldInClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        return null;
    }

    private Map<String, String> getExistingColumns(String tableName) throws SQLException {
        Map<String, String> columns = new HashMap<>();
        try (Connection conn = MySQL.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    columns.put(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"));
                }
            }
        }
        return columns;
    }
}