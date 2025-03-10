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
package org.jkiss.dbeaver.tools.transfer.ui.wizard;

import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.tools.transfer.*;
import org.jkiss.dbeaver.tools.transfer.internal.DTMessages;
import org.jkiss.dbeaver.tools.transfer.registry.DataTransferNodeDescriptor;
import org.jkiss.dbeaver.tools.transfer.registry.DataTransferProcessorDescriptor;
import org.jkiss.dbeaver.tools.transfer.ui.internal.DTUIMessages;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardPage;
import org.jkiss.dbeaver.ui.dialogs.IWizardPageNavigable;
import org.jkiss.utils.CommonUtils;

import java.util.List;

class DataTransferPageFinal extends ActiveWizardPage<DataTransferWizard> implements IWizardPageNavigable {

    private static final Log log = Log.getLog(DataTransferPageFinal.class);

    private Table resultTable;
    private boolean activated = false;
    private Text sourceSettingsText;
    private Text targetSettingsText;

    DataTransferPageFinal() {
        super(DTUIMessages.data_transfer_wizard_final_name);
        setTitle(DTUIMessages.data_transfer_wizard_final_title);
        setDescription(DTUIMessages.data_transfer_wizard_final_description);
        setPageComplete(true);
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite composite = UIUtils.createComposite(parent, 1);

        SashForm sash = new SashForm(composite, SWT.VERTICAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));

        {
            Group tablesGroup = UIUtils.createControlGroup(sash, DTUIMessages.data_transfer_wizard_final_group_objects, 3, GridData.FILL_BOTH, 0);

            resultTable = new Table(tablesGroup, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
            resultTable.setLayoutData(new GridData(GridData.FILL_BOTH));
            resultTable.setHeaderVisible(true);
            resultTable.setLinesVisible(true);

            UIUtils.createTableColumn(resultTable, SWT.LEFT, DTUIMessages.data_transfer_wizard_final_column_source_container);
            UIUtils.createTableColumn(resultTable, SWT.LEFT, DTUIMessages.data_transfer_wizard_final_column_source);
            UIUtils.createTableColumn(resultTable, SWT.LEFT, DTUIMessages.data_transfer_wizard_final_column_target_container);
            UIUtils.createTableColumn(resultTable, SWT.LEFT, DTUIMessages.data_transfer_wizard_final_column_target);
        }

        {
            Composite settingsGroup = UIUtils.createComposite(sash, 2);
            settingsGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

            Group sourceSettingsGroup = UIUtils.createControlGroup(settingsGroup, DTUIMessages.data_transfer_wizard_final_group_settings_source, 1, GridData.FILL_BOTH, 0);
            sourceSettingsText = new Text(sourceSettingsGroup, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
            sourceSettingsText.setLayoutData(new GridData(GridData.FILL_BOTH));
            ((GridData) sourceSettingsText.getLayoutData()).heightHint = 30;

            Group targetSettingsGroup = UIUtils.createControlGroup(settingsGroup, DTUIMessages.data_transfer_wizard_final_group_settings_target, 1, GridData.FILL_BOTH, 0);
            targetSettingsText = new Text(targetSettingsGroup, SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
            targetSettingsText.setLayoutData(new GridData(GridData.FILL_BOTH));
            ((GridData) targetSettingsText.getLayoutData()).heightHint = 30;
        }

        setControl(composite);
    }

    @Override
    public void activatePage() {
        getWizard().loadNodeSettings();

        resultTable.removeAll();
        DataTransferSettings settings = getWizard().getSettings();
        List<DataTransferPipe> dataPipes = settings.getDataPipes();

        IDataTransferSettings consumerSettings = null, producerSettings = null;

        for (int i = 0; i < dataPipes.size(); i++) {
            DataTransferPipe pipe = dataPipes.get(i);
            try {
                pipe.initPipe(settings, i, dataPipes.size());
            } catch (DBException e) {
                DBWorkbench.getPlatformUI().showError(DTUIMessages.data_transfer_page_final_title_error_initializing_transfer_pipe,
                    DTUIMessages.data_transfer_page_final_message_error_initializing_data_transfer_pipe, e);
                continue;
            }

            @Nullable
            IDataTransferConsumer<?, ?> consumer = pipe.getConsumer();
            @Nullable
            IDataTransferProducer<?> producer = pipe.getProducer();

            if (consumerSettings == null) {
                consumerSettings = settings.getNodeSettings(settings.getConsumer());
            }
            if (producerSettings == null) {
                producerSettings = settings.getNodeSettings(settings.getProducer());
            }
            DataTransferProcessorDescriptor processorDescriptor = settings.getProcessor();

            TableItem item = new TableItem(resultTable, SWT.NONE);
            if (producer != null) {
                item.setText(0, producer.getObjectContainerName());
                if (producer.getObjectContainerIcon() != null) {
                    item.setImage(0, DBeaverIcons.getImage(producer.getObjectContainerIcon()));
                }
                item.setText(1, CommonUtils.notEmpty(producer.getObjectName()));
                DBPImage producerObjectIcon = producer.getObjectIcon();
                if (producerObjectIcon == null) {
                    producerObjectIcon = settings.getProducer().getIcon();
                }
                if (producerObjectIcon != null) {
                    item.setImage(1, DBeaverIcons.getImage(producerObjectIcon));
                }
                Color producerColor = getNodeColor(producer);
                if (producerColor != null) {
                    item.setBackground(0, producerColor);
                    item.setBackground(1, producerColor);
                }
            }
            if (consumer != null) {
                item.setText(2, consumer.getObjectContainerName());
                if (consumer.getObjectContainerIcon() != null) {
                    item.setImage(2, DBeaverIcons.getImage(consumer.getObjectContainerIcon()));
                }
                item.setText(3, consumer.getObjectName());
                DBPImage consumerObjectIcon = consumer.getObjectIcon();
                if (consumerObjectIcon == null && processorDescriptor != null) {
                    consumerObjectIcon = processorDescriptor.getIcon();
                }
                if (consumerObjectIcon == null && settings.getConsumer() != null) {
                    consumerObjectIcon = settings.getConsumer().getIcon();
                }
                if (consumerObjectIcon != null) {
                    item.setImage(3, DBeaverIcons.getImage(consumerObjectIcon));
                }
                Color consumerColor = getNodeColor(consumer);
                if (consumerColor != null) {
                    item.setBackground(2, consumerColor);
                    item.setBackground(3, consumerColor);
                }
            }
        }

        printSummary(sourceSettingsText,
            settings.getProducer(),
            producerSettings,
            settings.isProducerProcessor() ? settings.getProcessor() : null);
        printSummary(targetSettingsText,
            settings.getConsumer(),
            consumerSettings,
            settings.isProducerProcessor() ? null : settings.getProcessor());

        activated = true;
        UIUtils.asyncExec(() -> {
            if (!resultTable.isDisposed()) {
                int tableWidth = resultTable.getSize().x;
                TableColumn[] columns = resultTable.getColumns();
                for (TableColumn column : columns) {
                    column.setWidth(tableWidth / columns.length - 1);
                }
            }
        });
        updatePageCompletion();
        getWizard().updateSaveTaskButtons();
    }

    @Override
    public boolean isPageComplete() {
        return activated;
    }

    private Color getNodeColor(IDataTransferNode<?> node) {
        DBSObject dbObject = node.getDatabaseObject();
        if (dbObject != null) {
            DBPDataSource dataSource = dbObject.getDataSource();
            if (dataSource != null) {
                DBPDataSourceContainer container = dataSource.getContainer();
                return UIUtils.getConnectionColor(container.getConnectionConfiguration());
            }
        }
        return null;
    }

    private void printSummary(Text text, DataTransferNodeDescriptor node, IDataTransferSettings settings, DataTransferProcessorDescriptor processor) {
        StringBuilder summary = new StringBuilder();
        if (settings != null) {
            String settingsSummary = settings.getSettingsSummary();
            if (!CommonUtils.isEmpty(settingsSummary)) {
                if (node != null) {
                    summary.append(NLS.bind(DTMessages.data_transfer_summary_title, node.getName())).append(":\n");
                }
                summary.append(CommonUtils.notEmpty(settingsSummary));
            }
        }
        if (processor != null) {
            DTUtils.addSummary(summary, processor, getWizard().getSettings().getProcessorProperties());
        }
        text.setText(summary.toString());
    }

    public boolean isActivated() {
        return activated;
    }

    @Override
    protected boolean determinePageCompletion() {
        for (DataTransferPipe pipe : getWizard().getSettings().getDataPipes()) {
            if (pipe.getProducer() == null || !pipe.getProducer().isConfigurationComplete()) {
                setErrorMessage(NLS.bind(DTUIMessages.data_transfer_error_source_not_specified,
                    pipe.getConsumer().getObjectName()));
                return false;
            }
            if (pipe.getConsumer() == null || !pipe.getConsumer().isConfigurationComplete()) {
                setErrorMessage(NLS.bind(DTUIMessages.data_transfer_error_target_not_specified,
                    pipe.getProducer().getObjectName()));
                return false;
            }
        }
        return activated;
    }

    @Override
    public boolean isPageNavigable() {
        return true;
    }

    @Override
    public boolean isPageApplicable() {
        return true;
    }

}