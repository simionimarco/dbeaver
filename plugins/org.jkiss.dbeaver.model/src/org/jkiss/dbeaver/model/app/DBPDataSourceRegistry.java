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

package org.jkiss.dbeaver.model.app;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.access.DBAAuthProfile;
import org.jkiss.dbeaver.model.access.DBACredentialsProvider;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.net.DBWNetworkProfile;
import org.jkiss.dbeaver.model.secret.DBPSecretHolder;
import org.jkiss.dbeaver.model.struct.DBSObjectFilter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Datasource registry.
 * Extends DBPObject to support datasources ObjectManager
 */
public interface DBPDataSourceRegistry extends DBPObject, DBPSecretHolder {

    String LEGACY_CONFIG_FILE_PREFIX = ".dbeaver-data-sources"; //$NON-NLS-1$
    String LEGACY_CONFIG_FILE_EXT = ".xml"; //$NON-NLS-1$
    String LEGACY_CONFIG_FILE_NAME = LEGACY_CONFIG_FILE_PREFIX + LEGACY_CONFIG_FILE_EXT;

    String MODERN_CONFIG_FILE_PREFIX = "data-sources"; //$NON-NLS-1$
    String MODERN_CONFIG_FILE_EXT = ".json"; //$NON-NLS-1$
    String MODERN_CONFIG_FILE_NAME = MODERN_CONFIG_FILE_PREFIX + MODERN_CONFIG_FILE_EXT;
    String CREDENTIALS_CONFIG_FILE_PREFIX = "credentials-config"; //$NON-NLS-1$
    String CREDENTIALS_CONFIG_FILE_EXT = ".json"; //$NON-NLS-1$
    String CREDENTIALS_CONFIG_FILE_NAME = CREDENTIALS_CONFIG_FILE_PREFIX + CREDENTIALS_CONFIG_FILE_EXT;

    /**
     * Owner project.
     */
    DBPProject getProject();

    @Nullable
    DBPDataSourceContainer getDataSource(String id);

    @Nullable
    DBPDataSourceContainer getDataSource(DBPDataSource dataSource);

    @Nullable
    DBPDataSourceContainer findDataSourceByName(String name);

    @NotNull
    List<? extends DBPDataSourceContainer> getDataSourcesByProfile(@NotNull DBWNetworkProfile profile);

    @NotNull
    List<? extends DBPDataSourceContainer> getDataSources();

    @NotNull
    DBPDataSourceContainer createDataSource(DBPDriver driver, DBPConnectionConfiguration connConfig);

    @NotNull
    DBPDataSourceContainer createDataSource(DBPDataSourceContainer source);

    void addDataSourceListener(@NotNull DBPEventListener listener);

    boolean removeDataSourceListener(@NotNull DBPEventListener listener);

    void addDataSource(@NotNull DBPDataSourceContainer dataSource) throws DBException;

    void removeDataSource(@NotNull DBPDataSourceContainer dataSource);

    void updateDataSource(@NotNull DBPDataSourceContainer dataSource) throws DBException;

    @NotNull
    List<? extends DBPDataSourceFolder> getAllFolders();

    @NotNull
    List<? extends DBPDataSourceFolder> getRootFolders();

    DBPDataSourceFolder getFolder(String path);

    DBPDataSourceFolder addFolder(DBPDataSourceFolder parent, String name);

    void removeFolder(DBPDataSourceFolder folder, boolean dropContents);

    /**
     * Moves connection folder
     */
    void moveFolder(@NotNull String oldPath, @NotNull String newPath);

    @Nullable
    DBSObjectFilter getSavedFilter(String name);
    @NotNull
    List<DBSObjectFilter> getSavedFilters();
    void updateSavedFilter(DBSObjectFilter filter);
    void removeSavedFilter(String filterName);

    // Network profiles

    @Nullable
    DBWNetworkProfile getNetworkProfile(String source, String name);
    @NotNull
    List<DBWNetworkProfile> getNetworkProfiles();
    void updateNetworkProfile(DBWNetworkProfile profile);
    void removeNetworkProfile(DBWNetworkProfile profile);

    // Auth profiles

    @Nullable
    DBAAuthProfile getAuthProfile(String id);
    @NotNull
    List<DBAAuthProfile> getAllAuthProfiles();
    @NotNull
    List<DBAAuthProfile> getApplicableAuthProfiles(@Nullable DBPDriver driver);
    void updateAuthProfile(DBAAuthProfile profile);
    void removeAuthProfile(DBAAuthProfile profile);


    void flushConfig();
    void refreshConfig();

    /**
     * Refreshes configuration of specified datasources
     */
    void refreshConfig(@Nullable Collection<String> dataSourceIds);

    /**
     * Returns and nullifies last registry save/load error.
     */
    Throwable getLastError();

    boolean hasError();

    /**
     * Throws lasty occured load/save error
     */
    void checkForErrors() throws DBException;

    void notifyDataSourceListeners(final DBPEvent event);

    // Registry auth provider. Null by default.
    @Nullable
    DBACredentialsProvider getAuthCredentialsProvider();

    /**
     * Sets auth credentials provider to the registry.
     */
    void setAuthCredentialsProvider(DBACredentialsProvider authCredentialsProvider);

    /**
     * Returns all folders having temporary connections.
     */
    Set<DBPDataSourceFolder> getTemporaryFolders();

    void dispose();

}
