/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ext.postgresql.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ext.postgresql.PostgreConstants;
import org.jkiss.dbeaver.ext.postgresql.PostgreDataSourceProvider;
import org.jkiss.dbeaver.ext.postgresql.PostgreUtils;
import org.jkiss.dbeaver.ext.postgresql.model.impls.PostgreServerPostgreSQL;
import org.jkiss.dbeaver.ext.postgresql.model.impls.PostgreServerType;
import org.jkiss.dbeaver.ext.postgresql.model.jdbc.PostgreJdbcFactory;
import org.jkiss.dbeaver.ext.postgresql.model.plan.PostgreQueryPlaner;
import org.jkiss.dbeaver.ext.postgresql.model.session.PostgreSessionManager;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.access.DBAUserPasswordManager;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManager;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.connection.DBPDriverConfigurationType;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.exec.jdbc.*;
import org.jkiss.dbeaver.model.exec.output.DBCServerOutputReader;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.impl.jdbc.*;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectLookupCache;
import org.jkiss.dbeaver.model.impl.net.SSLHandlerTrustStoreImpl;
import org.jkiss.dbeaver.model.impl.sql.QueryTransformerLimit;
import org.jkiss.dbeaver.model.meta.ForTest;
import org.jkiss.dbeaver.model.net.DBWHandlerConfiguration;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLState;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.model.struct.cache.SimpleObjectCache;
import org.jkiss.dbeaver.registry.timezone.TimezoneRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.net.DefaultCallbackHandler;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.BeanUtils;
import org.jkiss.utils.CommonUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreDataSource
 */
public class PostgreDataSource extends JDBCDataSource implements DBSInstanceContainer, DBPAdaptable, DBPObjectStatisticsCollector {

    private static final Log log = Log.getLog(PostgreDataSource.class);
    private static final PostgrePrivilegeType[] SUPPORTED_PRIVILEGE_TYPES = new PostgrePrivilegeType[]{
        PostgrePrivilegeType.SELECT,
        PostgrePrivilegeType.INSERT,
        PostgrePrivilegeType.UPDATE,
        PostgrePrivilegeType.DELETE,
        PostgrePrivilegeType.TRUNCATE,
        PostgrePrivilegeType.REFERENCES,
        PostgrePrivilegeType.TRIGGER,
        PostgrePrivilegeType.CREATE,
        PostgrePrivilegeType.CONNECT,
        PostgrePrivilegeType.TEMPORARY,
        PostgrePrivilegeType.EXECUTE,
        PostgrePrivilegeType.USAGE
    };

    private DatabaseCache databaseCache;
    private SettingCache settingCache;
    private String activeDatabaseName;
    private PostgreServerExtension serverExtension;
    private String serverVersion;
    private volatile boolean hasStatistics;
    private boolean supportsEnumTable;

    private static final String LEGACY_UA_TIMEZONE = "Europe/Kiev";
    private static final String NEW_UA_TIMEZONE = "Europe/Kyiv";

    public PostgreDataSource(DBRProgressMonitor monitor, DBPDataSourceContainer container)
        throws DBException
    {
        super(monitor, container, new PostgreDialect());

        // Statistics was disabled then mark it as already read
        this.hasStatistics = !CommonUtils.getBoolean(container.getConnectionConfiguration().getProviderProperty(
            PostgreConstants.PROP_SHOW_DATABASE_STATISTICS));
    }

    // Constructor for tests
    @ForTest
    public PostgreDataSource(DBPDataSourceContainer container, String serverVersion, String activeDatabaseName) {
        super(container, new PostgreDialect());
        this.serverVersion = serverVersion;
        this.activeDatabaseName = activeDatabaseName;
        this.hasStatistics = false;

        databaseCache = new DatabaseCache();
        PostgreDatabase defDatabase = new PostgreDatabase(
            this,
            activeDatabaseName);
        databaseCache.setCache(Collections.singletonList(defDatabase));
        settingCache = new SettingCache();
    }

    @Override
    public Object getDataSourceFeature(String featureId) {
        switch (featureId) {
            case DBPDataSource.FEATURE_MAX_STRING_LENGTH:
                return 10485760;
            case DBPDataSource.FEATURE_LOB_REQUIRE_TRANSACTIONS:
                return true;
        }
        return super.getDataSourceFeature(featureId);
    }

    @Override
    protected void initializeRemoteInstance(@NotNull DBRProgressMonitor monitor) throws DBException {
        DBPConnectionConfiguration configuration = getContainer().getActualConnectionConfiguration();
        if (configuration.getConfigurationType() == DBPDriverConfigurationType.MANUAL) {
            activeDatabaseName = configuration.getBootstrap().getDefaultCatalogName();
            if (CommonUtils.isEmpty(activeDatabaseName)) {
                activeDatabaseName = configuration.getDatabaseName();
            }
        } else {
            String url = configuration.getUrl();
            int divPos = url.lastIndexOf('/');
            if (divPos > 0) {
                int lastPos = -1;
                for (int i = divPos + 1; i < url.length(); i++) {
                    char c = url.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '.') {
                        lastPos = i;
                    }
                }
                if (lastPos < 0) lastPos = url.length();
                activeDatabaseName = url.substring(divPos + 1, lastPos);
            }
        }
        if (CommonUtils.isEmpty(activeDatabaseName)) {
            activeDatabaseName = PostgreConstants.DEFAULT_DATABASE;
        }

        databaseCache = new DatabaseCache();
        settingCache = new SettingCache();

        final boolean showNDD = isReadDatabaseList(configuration);
        List<PostgreDatabase> dbList = new ArrayList<>();
        if (!showNDD) {
            PostgreDatabase defDatabase = createDatabaseImpl(monitor, activeDatabaseName);
            dbList.add(defDatabase);
        } else {
            loadAvailableDatabases(monitor, configuration, dbList);
        }
        databaseCache.setCache(dbList);
        // Initiate default context
        getDefaultInstance().checkInstanceConnection(monitor, false);
        try {
            // Preload some settings, if available
            settingCache.getAllObjects(monitor, this);
        } catch (DBException e) {
            // ignore
        }
    }

    private void loadAvailableDatabases(@NotNull DBRProgressMonitor monitor, DBPConnectionConfiguration configuration, List<PostgreDatabase> dbList) throws DBException {
        DBExecUtils.startContextInitiation(getContainer());
        try (Connection bootstrapConnection = openConnection(monitor, null, "Read PostgreSQL database list")) {
            // Read server version info here - it is needed during database metadata fetch (#8061)
            readDatabaseServerVersion(bootstrapConnection.getMetaData());

            // Get all databases
            try (PreparedStatement dbStat = prepareReadDatabaseListStatement(monitor, bootstrapConnection, configuration)) {
                try (ResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        PostgreDatabase database = createDatabaseImpl(monitor, dbResult);
                        dbList.add(database);
                    }
                }
            }
            if (activeDatabaseName == null) {
                try (PreparedStatement stat = bootstrapConnection.prepareStatement("SELECT current_database()")) {
                    try (ResultSet rs = stat.executeQuery()) {
                        if (rs.next()) {
                            activeDatabaseName = JDBCUtils.safeGetString(rs, 1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DBException("Can't connect ot remote PostgreSQL server", e);
        } finally {
            DBExecUtils.finishContextInitiation(getContainer());
        }
    }

    // True if we need multiple databases
    protected boolean isReadDatabaseList(DBPConnectionConfiguration configuration) {
        // It is configurable by default
        return configuration.getConfigurationType() != DBPDriverConfigurationType.URL &&
            CommonUtils.getBoolean(configuration.getProviderProperty(PostgreConstants.PROP_SHOW_NON_DEFAULT_DB), false);
    }

    protected PreparedStatement prepareReadDatabaseListStatement(
        @NotNull DBRProgressMonitor monitor,
        @NotNull Connection bootstrapConnection,
        @NotNull DBPConnectionConfiguration configuration) throws SQLException
    {
        // Make initial connection to read database list
        DBSObjectFilter catalogFilters = getContainer().getObjectFilter(PostgreDatabase.class, null, false);
        StringBuilder catalogQuery = new StringBuilder("SELECT db.oid,db.* FROM pg_catalog.pg_database db WHERE 1 = 1");
        boolean addExclusionName = false;
        String connectionDBName = getContainer().getConnectionConfiguration().getDatabaseName();
        {
            final boolean showTemplates = CommonUtils.toBoolean(configuration.getProviderProperty(PostgreConstants.PROP_SHOW_TEMPLATES_DB));
            final boolean showUnavailable = CommonUtils.toBoolean(configuration.getProviderProperty(PostgreConstants.PROP_SHOW_UNAVAILABLE_DB));

            if (!showUnavailable) {
                catalogQuery.append(" AND datallowconn");
            }
            if (!showTemplates) {
                catalogQuery.append(" AND NOT datistemplate ");
                if (!CommonUtils.isEmpty(connectionDBName)) {
                    // User can add the name of template database in the Database field of connection settings. We must take it into account
                    catalogQuery.append("OR db.datname =?");
                    addExclusionName = true;
                }
            }
            if (catalogFilters != null) {
                JDBCUtils.appendFilterClause(catalogQuery, catalogFilters, "datname", false, this);
            }
            catalogQuery.append("\nORDER BY db.datname");
        }

        // Get all databases
        PreparedStatement dbStat = bootstrapConnection.prepareStatement(catalogQuery.toString());

        if (addExclusionName) {
            dbStat.setString(1, connectionDBName);
        }
        if (catalogFilters != null) {
            JDBCUtils.setFilterParameters(dbStat, addExclusionName ? 2 : 1, catalogFilters);
        }

        return dbStat;
    }

    @NotNull
    public PostgreDatabase createDatabaseImpl(@NotNull DBRProgressMonitor monitor, ResultSet dbResult) throws DBException {
        return new PostgreDatabase(monitor, this, dbResult);
    }

    @NotNull
    public PostgreDatabase createDatabaseImpl(@NotNull DBRProgressMonitor monitor, String name) throws DBException {
        return new PostgreDatabase(monitor, this, name);
    }

    @NotNull
    public PostgreDatabase createDatabaseImpl(DBRProgressMonitor monitor, String name, PostgreRole owner, String templateName, PostgreTablespace tablespace, PostgreCharset encoding) throws DBException {
        return new PostgreDatabase(monitor, this, name, owner, templateName, tablespace, encoding);
    }

    @Override
    protected Map<String, String> getInternalConnectionProperties(
        DBRProgressMonitor monitor,
        DBPDriver driver,
        JDBCExecutionContext context,
        String purpose,
        DBPConnectionConfiguration connectionInfo
    ) throws DBCException {
        Map<String, String> props = new LinkedHashMap<>(PostgreDataSourceProvider.getConnectionsProps());
        final DBWHandlerConfiguration sslConfig = getContainer().getActualConnectionConfiguration().getHandler(PostgreConstants.HANDLER_SSL);
        if (sslConfig != null && sslConfig.isEnabled()) {
            try {
                boolean useProxy = sslConfig.getBooleanProperty(PostgreConstants.PROP_SSL_PROXY);
                if (useProxy) {
                    initProxySSL(props, sslConfig);
                } else {
                    initServerSSL(props, sslConfig);
                }
            } catch (Exception e) {
                throw new DBCException("Error configuring SSL certificates", e);
            }
        } else {
            getServerType().initDefaultSSLConfig(connectionInfo, props);
        }
        PostgreServerType serverType = getType();
        if (serverType.turnOffPreparedStatements()
            && !CommonUtils.toBoolean(getContainer().getActualConnectionConfiguration().getProviderProperty(PostgreConstants.PROP_USE_PREPARED_STATEMENTS))) {
            // Turn off prepared statements using, to avoid error: "ERROR: prepared statement "S_1" already exists" from PGBouncer #10742
            props.put("prepareThreshold", "0");
        }

        if (getContainer().isConnectionReadOnly()) {
            props.put("readOnly", "true");
            props.put("readOnlyMode", "always");
        }

        return props;
    }

    private void initServerSSL(Map<String, String> props, DBWHandlerConfiguration sslConfig) throws DBException {
        props.put(PostgreConstants.PROP_SSL, "true");

        if (!isMultiUserOrDistributed()) {
            // Local FS mode
            final String rootCertProp;
            final String clientCertProp;
            final String keyCertProp;

            if (CommonUtils.isEmpty(sslConfig.getStringProperty(SSLHandlerTrustStoreImpl.PROP_SSL_METHOD))) {
                // Backward compatibility
                rootCertProp = sslConfig.getStringProperty(PostgreConstants.PROP_SSL_ROOT_CERT);
                clientCertProp = sslConfig.getStringProperty(PostgreConstants.PROP_SSL_CLIENT_CERT);
                keyCertProp = sslConfig.getStringProperty(PostgreConstants.PROP_SSL_CLIENT_KEY);
            } else {
                rootCertProp = sslConfig.getStringProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CA_CERT);
                clientCertProp = sslConfig.getStringProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CLIENT_CERT);
                keyCertProp = sslConfig.getStringProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CLIENT_KEY);
            }

            if (!CommonUtils.isEmpty(rootCertProp)) {
                props.put("sslrootcert", rootCertProp);
            }
            if (!CommonUtils.isEmpty(clientCertProp)) {
                props.put("sslcert", clientCertProp);
            }
            if (!CommonUtils.isEmpty(keyCertProp)) {
                props.put("sslkey", keyCertProp);
            }
        } else {
            try {
                String rootCertProp = sslConfig.getSecureProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CA_CERT_VALUE);
                if (!CommonUtils.isEmpty(rootCertProp)) {
                    props.put("sslrootcert", saveCertificateToFile(rootCertProp));
                }
                String clientCertProp = sslConfig.getSecureProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CLIENT_CERT_VALUE);
                if (!CommonUtils.isEmpty(clientCertProp)) {
                    props.put("sslcert", saveCertificateToFile(clientCertProp));
                }
                String keyCertDer = sslConfig.getSecureProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CLIENT_KEY);
                String keyCertProp = sslConfig.getSecureProperty(SSLHandlerTrustStoreImpl.PROP_SSL_CLIENT_KEY_VALUE);
                if (CommonUtils.isNotEmpty(keyCertDer)) { // may be after exception
                    props.put("sslkey", keyCertDer);
                } else if (CommonUtils.isNotEmpty(keyCertProp)) {
                    props.put("sslkey", saveCertificateToFile(keyCertProp));
                }
            } catch (IOException e) {
                throw new DBException("Can not configure SSL", e);
            }

        }

        final String modeProp = sslConfig.getStringProperty(PostgreConstants.PROP_SSL_MODE);
        if (!CommonUtils.isEmpty(modeProp)) {
            props.put("sslmode", modeProp);
        }
        final String factoryProp = sslConfig.getStringProperty(PostgreConstants.PROP_SSL_FACTORY);
        if (!CommonUtils.isEmpty(factoryProp)) {
            props.put("sslfactory", factoryProp);
        }
        props.put("sslpasswordcallback", DefaultCallbackHandler.class.getName());
    }

    private boolean isMultiUserOrDistributed() {
        return DBWorkbench.isDistributed() || DBWorkbench.getPlatform().getApplication().isMultiuser();
    }

    private void initProxySSL(Map<String, String> props, DBWHandlerConfiguration sslConfig) {
        // No special config
        //initServerSSL(props, sslConfig);
    }

    @Override
    protected PostgreExecutionContext createExecutionContext(JDBCRemoteInstance instance, String type) {
        return new PostgreExecutionContext((PostgreDatabase) instance, type);
    }

    protected void initializeContextState(@NotNull DBRProgressMonitor monitor, @NotNull JDBCExecutionContext context,
                                          @Nullable JDBCExecutionContext initFrom) throws DBException {
        PostgreExecutionContext postgreContext = (PostgreExecutionContext) context;
        PostgreSchema activeSchema = null;
        if (initFrom != null) {
            activeSchema = ((PostgreExecutionContext)initFrom).getDefaultSchema();
        }
        postgreContext.refreshDefaults(monitor, true);
        if (activeSchema != null) {
            postgreContext.setDefaultCatalog(monitor, activeSchema.getDatabase(), activeSchema, true);
        }
    }

    public DatabaseCache getDatabaseCache()
    {
        return databaseCache;
    }

    public List<PostgreDatabase> getDatabases()
    {
        return databaseCache.getCachedObjects();
    }

    public PostgreDatabase getDatabase(String name)
    {
        return databaseCache.getCachedObject(name);
    }

    public SettingCache getSettingCache() {
        return settingCache;
    }

    public Collection<PostgreSetting> getSettings() {
        return settingCache.getCachedObjects();
    }

    public PostgreSetting getSetting(String name) {
        return settingCache.getCachedObject(name);
    }

    @Override
    public void initialize(@NotNull DBRProgressMonitor monitor)
        throws DBException
    {
        super.initialize(monitor);

        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read server version")) {
            serverVersion = JDBCUtils.queryString(session, "SELECT version()");
        } catch (Exception e) {
            log.debug("Error reading PostgreSQL version: " + e.getMessage());
            serverVersion = "";
        }

        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read pg_enum table availability")) {
            supportsEnumTable = PostgreUtils.isMetaObjectExists(session, "pg_enum", "*");
        } catch (Exception e) {
            log.debug("Error reading pg_enum " + e.getMessage());
            supportsEnumTable = false;
        }

        // Read databases
        getDefaultInstance().cacheDataTypes(monitor, true);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor)
        throws DBException
    {
        super.refreshObject(monitor);
        shutdown(monitor);

        this.databaseCache.clearCache();
        this.activeDatabaseName = null;

        this.initializeRemoteInstance(monitor);
        this.initialize(monitor);

        return this;
    }

    @Override
    public List<? extends DBSObject> getChildren(@NotNull DBRProgressMonitor monitor) {
        return getDatabases();
    }

    @Override
    public DBSObject getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) {
        return getDatabase(childName);
    }

    @NotNull
    @Override
    public Class<? extends DBSObject> getPrimaryChildType(@Nullable DBRProgressMonitor monitor) {
        return PostgreDatabase.class;
    }

    @Override
    public void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope)
        throws DBException
    {
        databaseCache.getAllObjects(monitor, this);
    }

    ////////////////////////////////////////////
    // Connections

    @Override
    protected Connection openConnection(@NotNull DBRProgressMonitor monitor, @Nullable JDBCExecutionContext context, @NotNull String purpose) throws DBCException {
        final DBPConnectionConfiguration conConfig = getContainer().getActualConnectionConfiguration();

        JDBCRemoteInstance instance = context == null ? null : context.getOwnerInstance();
        Connection pgConnection;
        if (instance != null) {
            log.debug("Initiate connection to " + getServerType().getServerTypeName() + " database [" + instance.getName() + "@" + conConfig.getHostName() + "] for " + purpose);
        }
        boolean timezoneOverridden = false;

        try {
            // Old versions of postgres and some linux distributions, on which docker images are made, may not contain
            // new timezone, which will lead to the error while connecting, there is no way to know before connecting
            // so to be sure we will use the old name
            if (NEW_UA_TIMEZONE.equals(TimeZone.getDefault().getID())) { //$NON-NLS-1$
                timezoneOverridden = true;
                TimezoneRegistry.setDefaultZone(ZoneId.of(LEGACY_UA_TIMEZONE)); //$NON-NLS-1$
            }
            if (conConfig.getConfigurationType() != DBPDriverConfigurationType.URL &&
                instance instanceof PostgreDatabase &&
                !CommonUtils.equalObjects(instance.getName(), conConfig.getDatabaseName())
            ) {
                // If database was changed then use new name for connection
                final DBPConnectionConfiguration originalConfig = new DBPConnectionConfiguration(conConfig);
                try {
                    // Patch URL with new database name
                    if (CommonUtils.isEmpty(conConfig.getUrl()) || !CommonUtils.isEmpty(conConfig.getHostName())) {
                        conConfig.setDatabaseName(instance.getName());
                        final DBPDriver driver = getContainer().getDriver();
                        String newURL = DatabaseURL.generateUrlByTemplate(driver, conConfig);
                        if (CommonUtils.isEmpty(newURL)) {
                            newURL = driver.getDataSourceProvider().getConnectionURL(driver, conConfig);
                        }
                        conConfig.setUrl(newURL);
                    }

                    pgConnection = super.openConnection(monitor, context, purpose);
                }
                finally {
                    conConfig.setDatabaseName(originalConfig.getDatabaseName());
                    conConfig.setUrl(originalConfig.getUrl());
                }
            } else {
                pgConnection = super.openConnection(monitor, context, purpose);
            }
        } catch (DBCException e) {
            final Throwable cause = GeneralUtils.getRootCause(e);
            final StackTraceElement element = cause.getStackTrace()[0];

            if ("sun.security.util.DerValue".equals(element.getClassName())) { //$NON-NLS-1$
                final DBWHandlerConfiguration handler = Objects.requireNonNull(conConfig.getHandler(PostgreConstants.HANDLER_SSL));
                final Path dst;

                try {
                    dst = DBWorkbench.getPlatform().getTempFolder(monitor, "ssl").resolve(container.getId() + ".pk8");
                } catch (IOException ex) {
                    log.error("Error creating temporary SSL key", ex);
                    throw e;
                }

                try {
                    SSLHandlerTrustStoreImpl.loadDerFromPem(handler, dst);
                } catch (IOException ex) {
                    log.error("Error converting SSL key", ex);
                    throw e;
                }

                // Unfortunately, we can't delete the temp file here.
                // The chain is built asynchronously by the driver, and we don't know at which moment in time it will happen.
                // It will still be deleted during shutdown.

                return openConnection(monitor, context, purpose);
            }

            throw e;
        } finally {
            if (timezoneOverridden && LEGACY_UA_TIMEZONE.equals(TimeZone.getDefault().getID())) { //$NON-NLS-1$
                TimezoneRegistry.setDefaultZone(ZoneId.of(NEW_UA_TIMEZONE)); //$NON-NLS-1$
            }
        }

        if (getServerType().supportsClientInfo() && !getContainer().getPreferenceStore().getBoolean(ModelPreferences.META_CLIENT_NAME_DISABLE)) {
            // Provide client info. Not supported by Redshift?
            try {
                pgConnection.setClientInfo(JDBCConstants.APPLICATION_NAME_CLIENT_PROPERTY, DBUtils.getClientApplicationName(getContainer(), context, purpose));
            } catch (Throwable e) {
                // just ignore
                log.debug(e);
            }
        }

        return pgConnection;
    }

    @Override
    public <T> T getAdapter(Class<T> adapter)
    {
        if (adapter == DBSStructureAssistant.class) {
            return adapter.cast(new PostgreStructureAssistant(this));
        } else if (adapter == DBCServerOutputReader.class) {
            return adapter.cast(new PostgreServerOutputReader());
        } else if (adapter == DBAServerSessionManager.class) {
            return adapter.cast(new PostgreSessionManager(this));
        } else if (adapter == DBCQueryPlanner.class) {
            return adapter.cast(new PostgreQueryPlaner(this));
        } else if (adapter == DBSDataBulkLoader.class) {
            if (getServerType().supportsCopyFromStdIn()) {
                return adapter.cast(new PostgreCopyLoader(this));
            }
        } else if (adapter == DBAUserPasswordManager.class) {
            if (getServerType().supportsAlterUserChangePassword()) {
                return adapter.cast(new PostgresUserPasswordManager(this));
            }
        }
        return super.getAdapter(adapter);
    }

    @Nullable
    @Override
    public DBSDataType resolveDataType(@NotNull DBRProgressMonitor monitor, @NotNull String typeFullName) throws DBException {
        DBSDataType dataType = super.resolveDataType(monitor, typeFullName);
        if (dataType != null) {
            return dataType;
        }
        return PostgreUtils.resolveTypeFullName(monitor, this, typeFullName);
    }
    
    @Override
    public Collection<PostgreDataType> getLocalDataTypes()
    {
        return getDefaultInstance().getLocalDataTypes();
    }

    @Override
    public PostgreDataType getLocalDataType(String typeName)
    {
        return getDefaultInstance().getLocalDataType(typeName);
    }

    @Override
    public DBSDataType getLocalDataType(int typeID) {
        return getDefaultInstance().getLocalDataType(typeID);
    }

    @Override
    public String getDefaultDataTypeName(@NotNull DBPDataKind dataKind) {
        return getDefaultInstance().getDefaultDataTypeName(dataKind);
    }

    @NotNull
    @Override
    public PostgreDatabase getDefaultInstance() {
        PostgreDatabase defDatabase = databaseCache.getCachedObject(activeDatabaseName);
        if (defDatabase == null) {
            defDatabase = databaseCache.getCachedObject(PostgreConstants.DEFAULT_DATABASE);
        }
        if (defDatabase == null) {
            final List<PostgreDatabase> allDatabases = databaseCache.getCachedObjects();
            if (allDatabases.isEmpty()) {
                // Looks like we are not connected or in connection process right now - no instance then
                throw new IllegalStateException("No databases found on the server");
            }
            defDatabase = allDatabases.get(0);
        }
        return defDatabase;
    }

    @NotNull
    @Override
    public List<PostgreDatabase> getAvailableInstances() {
        return databaseCache.getCachedObjects();
    }

    void setActiveDatabase(PostgreDatabase newDatabase) {
        final PostgreDatabase oldDatabase = getDefaultInstance();
        if (oldDatabase == newDatabase) {
            return;
        }

        activeDatabaseName = newDatabase.getName();

        // Notify UI
        DBUtils.fireObjectSelect(oldDatabase, false);
        DBUtils.fireObjectSelect(newDatabase, true);
    }

    /**
     * Deprecated. Database change is not supported (as it is ambiguous)
     */
    @Deprecated
    public void setDefaultInstance(@NotNull DBRProgressMonitor monitor, @NotNull PostgreDatabase newDatabase, PostgreSchema schema)
        throws DBException
    {
        final PostgreDatabase oldDatabase = getDefaultInstance();
        if (oldDatabase != newDatabase) {
            newDatabase.initializeMetaContext(monitor);
            newDatabase.cacheDataTypes(monitor, false);
        }

        PostgreSchema oldDefaultSchema = null;
        if (schema != null) {
            oldDefaultSchema = newDatabase.getMetaContext().getDefaultSchema();
            newDatabase.getMetaContext().changeDefaultSchema(monitor, schema, false, false);
        }

        activeDatabaseName = newDatabase.getName();

        // Notify UI
        DBUtils.fireObjectSelect(oldDatabase, false);
        DBUtils.fireObjectSelect(newDatabase, true);

        if (schema != null && schema != oldDefaultSchema) {
            if (oldDefaultSchema != null) {
                DBUtils.fireObjectSelect(oldDefaultSchema, false);
            }
            DBUtils.fireObjectSelect(schema, true);
        }

        // Close all database connections but meta (we need it to browse metadata like navigator tree)
        oldDatabase.shutdown(monitor, true);
    }

    public List<String> getTemplateDatabases(DBRProgressMonitor monitor) throws DBException {
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load template databases")) {
            try (PreparedStatement dbStat = session.prepareStatement("SELECT db.datname FROM pg_catalog.pg_database db WHERE datistemplate")) {
                try (ResultSet resultSet = dbStat.executeQuery()) {
                    List<String> dbNames = new ArrayList<>();
                    while (resultSet.next()) {
                        dbNames.add(resultSet.getString(1));
                    }
                    return dbNames;
                }
            }
        } catch (Exception e) {
            throw new DBException("Error reading template databases", e);
        }
    }

    public PostgreServerExtension getServerType() {
        if (serverExtension == null) {
            PostgreServerType serverType = getType();

            try {
                serverExtension = serverType.createServerExtension(this);
            } catch (Throwable e) {
                log.error("Can't determine server type", e);
                serverExtension = new PostgreServerPostgreSQL(this);
            }
        }
        return serverExtension;
    }

    public PostgreServerType getType() {
        return PostgreUtils.getServerType(getContainer().getDriver());
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public boolean supportsRoles() {
        return getServerType().supportsRoles() && !getContainer().getNavigatorSettings().isShowOnlyEntities() && !getContainer().getNavigatorSettings().isHideFolders();
    }

    @NotNull
    public PostgrePrivilegeType[] getSupportedPrivilegeTypes() {
        return SUPPORTED_PRIVILEGE_TYPES;
    }

    @Override
    public boolean isStatisticsCollected() {
        return hasStatistics;
    }

    @Override
    public void collectObjectStatistics(DBRProgressMonitor monitor, boolean totalSizeOnly, boolean forceRefresh) throws DBException {
        if (hasStatistics && !forceRefresh) {
            return;
        }
        hasStatistics = true;
        if (!getServerType().supportsDatabaseSize()) {
            return;
        }
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load table status")) {
            Collection<PostgreDatabase> databases = getDatabases();
            try (JDBCPreparedStatement dbStat = session.prepareStatement(
                "SELECT db.datname,pg_database_size(db.oid) FROM pg_catalog.pg_database db " +
                    (databases.size() == 1 ? "WHERE db.oid=?" : "")))
            {
                if (databases.size() == 1) {
                    dbStat.setLong(1, databases.iterator().next().getObjectId());
                }
                try (JDBCResultSet dbResult = dbStat.executeQuery()) {
                    while (dbResult.next()) {
                        String dbName = JDBCUtils.safeGetString(dbResult, 1);
                        long dbSize = dbResult.getLong(2);
                        PostgreDatabase database = getDatabase(dbName);
                        if (database != null) {
                            database.setDbTotalSize(dbSize);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new DBCException(e, session.getExecutionContext());
            }
        } finally {
            hasStatistics = true;
        }
    }

    private static class DatabaseCache extends SimpleObjectCache<PostgreDataSource, PostgreDatabase> {
    }

    static class SettingCache extends JDBCObjectLookupCache<PostgreDataSource, PostgreSetting> {
        @NotNull
        @Override
        public JDBCStatement prepareLookupStatement(@NotNull JDBCSession session, @NotNull PostgreDataSource owner, @Nullable PostgreSetting object, @Nullable String objectName) throws SQLException {
            if (object != null || objectName != null) {
                final JDBCPreparedStatement dbStat = session.prepareStatement("select * from pg_catalog.pg_settings where name=?");
                dbStat.setString(1, object != null ? object.getName() : objectName);
                return dbStat;
            }

            return session.prepareStatement("select * from pg_catalog.pg_settings");
        }

        @Nullable
        @Override
        protected PostgreSetting fetchObject(@NotNull JDBCSession session, @NotNull PostgreDataSource owner, @NotNull JDBCResultSet dbResult) throws SQLException, DBException {
            return new PostgreSetting(owner, dbResult);
        }
    }


    private final Pattern ERROR_POSITION_PATTERN = Pattern.compile("\\n\\s*\\p{L}+\\s*: ([0-9]+)");

    @Nullable
    @Override
    public ErrorPosition[] getErrorPosition(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext context, @NotNull String query, @NotNull Throwable error) {
        Throwable rootCause = GeneralUtils.getRootCause(error);
        if (PostgreConstants.PSQL_EXCEPTION_CLASS_NAME.equals(rootCause.getClass().getName())) {
            try {
                Object serverErrorMessage = BeanUtils.readObjectProperty(rootCause, "serverErrorMessage");
                if (serverErrorMessage != null) {
                    Object position = BeanUtils.readObjectProperty(serverErrorMessage, "position");
                    if (position instanceof Number) {
                        ErrorPosition pos = new ErrorPosition();
                        pos.position = ((Number) position).intValue() - 1;
                        return new ErrorPosition[] {pos};
                    }
                }
            } catch (Throwable e) {
                // Something went wrong. Doesn't matter, ignore it as we are already in error handling routine
            }
        }
        String message = error.getMessage();
        if (!CommonUtils.isEmpty(message)) {
            Matcher matcher = ERROR_POSITION_PATTERN.matcher(message);
            if (matcher.find()) {
                DBPErrorAssistant.ErrorPosition pos = new DBPErrorAssistant.ErrorPosition();
                pos.position = Integer.parseInt(matcher.group(1)) - 1;
                return new ErrorPosition[] {pos};
            }
        }
        return null;
    }

    @NotNull
    @Override
    protected JDBCFactory createJdbcFactory() {
        return new PostgreJdbcFactory();
    }

    @Override
    public ErrorType discoverErrorType(@NotNull Throwable error) {
        String sqlState = SQLState.getStateFromException(error);
        if (sqlState != null) {
            if (PostgreConstants.ERROR_ADMIN_SHUTDOWN.equals(sqlState)) {
                return ErrorType.CONNECTION_LOST;
            } else if (PostgreConstants.ERROR_TRANSACTION_ABORTED.equals(sqlState)) {
                return ErrorType.TRANSACTION_ABORTED;
            }
        }
        if (getServerType() instanceof DBPErrorAssistant) {
            ErrorType errorType = ((DBPErrorAssistant) getServerType()).discoverErrorType(error);
            if (errorType != null) {
                return errorType;
            }
        }

        return super.discoverErrorType(error);
    }

    @Override
    protected DBPDataSourceInfo createDataSourceInfo(DBRProgressMonitor monitor, @NotNull JDBCDatabaseMetaData metaData)
    {
        return new PostgreDataSourceInfo(this, metaData);
    }

    @Nullable
    @Override
    public DBCQueryTransformer createQueryTransformer(@NotNull DBCQueryTransformType type) {
        if (type == DBCQueryTransformType.RESULT_SET_LIMIT) {
            return new QueryTransformerLimit(false, true);
        } else if (type == DBCQueryTransformType.FETCH_ALL_TABLE) {
            return new QueryTransformerFetchAll();
        }
        return null;
    }

    public boolean supportReadingAllDataTypes() {
        return CommonUtils.toBoolean(getContainer().getActualConnectionConfiguration().getProviderProperty(PostgreConstants.PROP_READ_ALL_DATA_TYPES));
    }

    public boolean supportsReadingKeysWithColumns() {
        return CommonUtils.toBoolean(
            getContainer().getActualConnectionConfiguration().getProviderProperty(PostgreConstants.PROP_READ_KEYS_WITH_COLUMNS));
    }

    public boolean isSupportsEnumTable() {
        return supportsEnumTable;
    }
}
