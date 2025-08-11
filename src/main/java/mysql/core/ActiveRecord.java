package mysql.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mysql.MySQL;
import mysql.annotations.Column;
import mysql.annotations.Id;
import mysql.annotations.ManyToOne;
import mysql.annotations.Table;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ActiveRecord<T> {

    private static final ExecutorService persistenceExecutor = Executors.newFixedThreadPool(4);
    private static final Gson gson = new Gson();

    public void save() {
        persistenceExecutor.submit(() -> {
            try {
                Field idField = findIdFieldInClass(this.getClass());
                idField.setAccessible(true);
                Object idValue = idField.get(this);

                if (idValue != null && ((Number) idValue).intValue() != 0) {
                    executeUpdate();
                } else {
                    executeInsert();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static <T> Optional<T> findById(Object id, Class<T> clazz) {
        String tableName = clazz.getAnnotation(Table.class).value();
        Field idField = findIdFieldInClass(clazz);
        String idColumnName = getColumnName(idField);

        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumnName + " = ?;";
        try (Connection conn = MySQL.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs, clazz));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private void executeInsert() throws Exception {
        String tableName = this.getClass().getAnnotation(Table.class).value();
        Field idField = findIdFieldInClass(this.getClass());

        Map<String, Object> columns = getColumnsAndValues(this);
        columns.remove(getColumnName(idField));

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", columns.keySet()));
        sql.append(") VALUES (");
        sql.append(String.join(", ", Collections.nCopies(columns.size(), "?")));
        sql.append(");");

        try (Connection conn = MySQL.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, new ArrayList<>(columns.values()));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    idField.set(this, rs.getInt(1));
                }
            }
        }
    }

    private void executeUpdate() throws Exception {
        String tableName = this.getClass().getAnnotation(Table.class).value();
        Field idField = findIdFieldInClass(this.getClass());

        Map<String, Object> columns = getColumnsAndValues(this);
        String idColumnName = getColumnName(idField);
        Object idValue = columns.remove(idColumnName);

        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        for (String columnName : columns.keySet()) {
            sql.append(columnName).append(" = ?, ");
        }
        sql.delete(sql.length() - 2, sql.length());
        sql.append(" WHERE ").append(idColumnName).append(" = ?;");

        List<Object> params = new ArrayList<>(columns.values());
        params.add(idValue);

        try (Connection conn = MySQL.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            setParameters(stmt, params);
            stmt.executeUpdate();
        }
    }

    private static Map<String, Object> getColumnsAndValues(Object entity) throws IllegalAccessException {
        Map<String, Object> columns = new HashMap<>();
        for (Field field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(ManyToOne.class)) {
                String columnName = field.getName() + "_id";
                Object relatedEntity = field.get(entity);
                if (relatedEntity != null) {
                    Field relatedIdField = findIdFieldInClass(relatedEntity.getClass());
                    relatedIdField.setAccessible(true);
                    columns.put(columnName, relatedIdField.get(relatedEntity));
                } else {
                    columns.put(columnName, null);
                }
            } else if (isPersistableField(field)) {
                columns.put(getColumnName(field), field.get(entity));
            }
        }
        return columns;
    }

    private static <T> T mapResultSetToEntity(ResultSet rs, Class<T> clazz) throws Exception {
        T entity = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(ManyToOne.class)) {
                String columnName = field.getName() + "_id";
                Object relatedId = rs.getObject(columnName);
                field.set(entity, null);
            } else if (isPersistableField(field)) {
                String columnName = getColumnName(field);
                Object value = rs.getObject(columnName);

                if (value instanceof String && field.getType() == UUID.class) {
                    field.set(entity, UUID.fromString((String) value));
                } else if (value instanceof String && field.getType() == List.class) {
                    TypeToken<?> typeToken = TypeToken.get(field.getGenericType());
                    field.set(entity, gson.fromJson((String) value, typeToken.getType()));
                } else {
                    field.set(entity, value);
                }
            }
        }
        return entity;
    }

    private static void setParameters(PreparedStatement stmt, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof UUID) {
                stmt.setString(i + 1, param.toString());
            } else if (param instanceof List) {
                stmt.setString(i + 1, gson.toJson(param));
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }

    private static Field findIdFieldInClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        return null;
    }

    private static String getColumnName(Field field) {
        if (field.isAnnotationPresent(Column.class)) {
            return field.getAnnotation(Column.class).value();
        }
        if (field.isAnnotationPresent(Id.class)) {
            return "id";
        }
        return field.getName();
    }

    private static boolean isPersistableField(Field field) {
        return field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class);
    }
}