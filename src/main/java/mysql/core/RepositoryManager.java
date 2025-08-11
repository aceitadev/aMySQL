package mysql.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mysql.MySQL;
import mysql.annotations.Column;
import mysql.annotations.Id;
import mysql.annotations.ManyToOne;
import mysql.annotations.Table;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RepositoryManager {

    private final Map<Class<?>, Object> repositoryCache = new HashMap<>();
    private final ExecutorService persistenceExecutor = Executors.newFixedThreadPool(4);
    private final Gson gson = new Gson();

    @SuppressWarnings("unchecked")
    public <T> T getRepository(Class<T> repositoryInterface) {
        return (T) repositoryCache.computeIfAbsent(repositoryInterface, this::createRepositoryProxy);
    }

    private <T> Object createRepositoryProxy(Class<T> repositoryInterface) {
        return Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                new RepositoryInvocationHandler(getEntityType(repositoryInterface))
        );
    }

    private Class<?> getEntityType(Class<?> repositoryInterface) {
        return (Class<?>) ((ParameterizedType) repositoryInterface.getGenericInterfaces()[0]).getActualTypeArguments()[0];
    }

    private class RepositoryInvocationHandler implements InvocationHandler {
        private final Class<?> entityClass;
        private final String tableName;
        private final Field idField;

        public RepositoryInvocationHandler(Class<?> entityClass) {
            this.entityClass = entityClass;
            this.tableName = entityClass.getAnnotation(Table.class).value();
            this.idField = findIdFieldInClass(entityClass);
            if (this.idField != null) this.idField.setAccessible(true);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("save")) {
                persistenceExecutor.submit(() -> performSave(args[0]));
                return args[0];
            }
            if (method.getName().equals("findById")) {
                return findById(args[0]);
            }
            return null;
        }

        private void performSave(Object entity) {
            try {
                Object idValue = idField.get(entity);
                if (idValue != null && ((Number) idValue).intValue() != 0) {
                    executeUpdate(entity);
                } else {
                    executeInsert(entity);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Map<String, Object> getColumnsAndValues(Object entity) throws IllegalAccessException {
            Map<String, Object> columns = new HashMap<>();
            for (Field field : entityClass.getDeclaredFields()) {
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

        private void executeInsert(Object entity) throws Exception {
            Map<String, Object> columns = getColumnsAndValues(entity);
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
                        idField.set(entity, rs.getInt(1));
                    }
                }
            }
        }

        private void executeUpdate(Object entity) throws Exception {
            Map<String, Object> columns = getColumnsAndValues(entity);
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

        private Optional<Object> findById(Object id) throws Exception {
            String sql = "SELECT * FROM " + tableName + " WHERE " + getColumnName(idField) + " = ?;";
            try (Connection conn = MySQL.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToEntity(rs));
                    }
                }
            }
            return Optional.empty();
        }

        private Object mapResultSetToEntity(ResultSet rs) throws Exception {
            Object entity = entityClass.getDeclaredConstructor().newInstance();
            for (Field field : entityClass.getDeclaredFields()) {
                field.setAccessible(true);

                if (field.isAnnotationPresent(ManyToOne.class)) {
                    String columnName = field.getName() + "_id";
                    Object relatedId = rs.getObject(columnName);
                    // Aqui viria a lógica de lazy/eager loading. Por enquanto, deixamos nulo.
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

        private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
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

        private Field findIdFieldInClass(Class<?> clazz) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }
            return null;
        }

        private String getColumnName(Field field) {
            if (field.isAnnotationPresent(Column.class)) {
                return field.getAnnotation(Column.class).value();
            }
            if (field.isAnnotationPresent(Id.class)) {
                return "id";
            }
            return field.getName();
        }

        private boolean isPersistableField(Field field) {
            return field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class);
        }
    }
}