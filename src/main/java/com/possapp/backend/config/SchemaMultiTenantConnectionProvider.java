package com.possapp.backend.config;

import com.possapp.backend.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    
    private final DataSource dataSource;
    private static final String DEFAULT_SCHEMA = "public";
    
    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }
    
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        final Connection connection = getAnyConnection();
        try {
            // Use the tenant schema
            String schema = tenantIdentifier != null ? tenantIdentifier : DEFAULT_SCHEMA;
            
            // Use SET search_path for PostgreSQL
            try (var stmt = connection.createStatement()) {
                stmt.execute("SET search_path TO \"" + schema + "\", public");
            }
        } catch (SQLException e) {
            log.error("Could not set search_path to schema [{}]", tenantIdentifier, e);
            throw new SQLException("Could not set search_path to schema [" + tenantIdentifier + "]", e);
        }
        return connection;
    }
    
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Reset search_path to default before returning to pool
            try (var stmt = connection.createStatement()) {
                stmt.execute("SET search_path TO public");
            }
        } catch (SQLException e) {
            log.warn("Could not reset search_path", e);
        }
        connection.close();
    }
    
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
    
    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }
    
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
