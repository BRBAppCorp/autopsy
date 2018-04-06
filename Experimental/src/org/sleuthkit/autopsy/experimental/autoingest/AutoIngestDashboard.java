/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Cursor;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.awt.Color;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.JobsSnapshot;

/**
 * A dashboard for monitoring an automated ingest cluster.
 */
final class AutoIngestDashboard extends JPanel implements Observer {

    private static final long serialVersionUID = 1L;
    private static final int GENERIC_COL_MIN_WIDTH = 30;
    private static final int GENERIC_COL_MAX_WIDTH = 2000;
    private static final int PENDING_TABLE_COL_PREFERRED_WIDTH = 280;
    private static final int RUNNING_TABLE_COL_PREFERRED_WIDTH = 175;
    private static final int PRIORITY_COLUMN_PREFERRED_WIDTH = 60;
    private static final int PRIORITY_COLUMN_MAX_WIDTH = 150;
    private static final int STAGE_TIME_COL_MIN_WIDTH = 250;
    private static final int STAGE_TIME_COL_MAX_WIDTH = 450;
    private static final int TIME_COL_MIN_WIDTH = 30;
    private static final int TIME_COL_MAX_WIDTH = 250;
    private static final int TIME_COL_PREFERRED_WIDTH = 140;
    private static final int NAME_COL_MIN_WIDTH = 100;
    private static final int NAME_COL_MAX_WIDTH = 250;
    private static final int NAME_COL_PREFERRED_WIDTH = 140;
    private static final int STAGE_COL_MIN_WIDTH = 70;
    private static final int STAGE_COL_MAX_WIDTH = 2000;
    private static final int STAGE_COL_PREFERRED_WIDTH = 300;
    private static final int STATUS_COL_MIN_WIDTH = 55;
    private static final int STATUS_COL_MAX_WIDTH = 250;
    private static final int STATUS_COL_PREFERRED_WIDTH = 55;
    private static final int COMPLETED_TIME_COL_MIN_WIDTH = 30;
    private static final int COMPLETED_TIME_COL_MAX_WIDTH = 2000;
    private static final int COMPLETED_TIME_COL_PREFERRED_WIDTH = 280;
    private static final Logger LOGGER = Logger.getLogger(AutoIngestDashboard.class.getName());
    private AutoIngestMonitor autoIngestMonitor;
    private AutoIngestJobsPanel pendingJobsPanel;
    private AutoIngestJobsPanel runningJobsPanel;
    private AutoIngestJobsPanel finishedJobsPanel;

    /**
     * Maintain a mapping of each service to it's last status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;

    /**
     * Creates a dashboard for monitoring an automated ingest cluster.
     *
     * @return The dashboard.
     *
     * @throws AutoIngestDashboardException If there is a problem creating the
     *                                      dashboard.
     */
    public static AutoIngestDashboard createDashboard() throws AutoIngestDashboardException {
        AutoIngestDashboard dashBoard = new AutoIngestDashboard();
        try {
            dashBoard.startUp();
        } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
            throw new AutoIngestDashboardException("Error starting up auto ingest dashboard", ex);
        }
        return dashBoard;
    }

    /**
     * Constructs a panel for monitoring an automated ingest cluster.
     */
    private AutoIngestDashboard() {
        this.statusByService = new ConcurrentHashMap<>();

        initComponents();
        statusByService.put(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        statusByService.put(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        statusByService.put(ServicesMonitor.Service.MESSAGING.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        setServicesStatusMessage();
        pendingJobsPanel = new AutoIngestJobsPanel(AutoIngestNode.AutoIngestJobType.PENDING_JOB);
        pendingJobsPanel.setSize(pendingScrollPane.getSize());
        pendingScrollPane.add(pendingJobsPanel);
        pendingScrollPane.setViewportView(pendingJobsPanel);
        pendingJobsPanel.addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            AutoIngestJob job = this.pendingJobsPanel.getSelectedAutoIngestJob();

            boolean enablePrioritizeButtons = false;
            boolean enableDeprioritizeButtons = false;
            if (job != null) {
                enablePrioritizeButtons = true;
                enableDeprioritizeButtons = job.getPriority() > 0;
            }
            this.prioritizeJobButton.setEnabled(enablePrioritizeButtons);
            this.prioritizeCaseButton.setEnabled(enablePrioritizeButtons);
            this.deprioritizeJobButton.setEnabled(enableDeprioritizeButtons);
            this.deprioritizeCaseButton.setEnabled(enableDeprioritizeButtons);
        });
        runningJobsPanel = new AutoIngestJobsPanel(AutoIngestNode.AutoIngestJobType.RUNNING_JOB);
        runningJobsPanel.setSize(runningScrollPane.getSize());
        runningScrollPane.add(runningJobsPanel);
        runningScrollPane.setViewportView(runningJobsPanel);
        runningJobsPanel.addListSelectionListener((ListSelectionEvent e) -> {
            boolean enabled = false;
            this.prioritizeJobButton.setEnabled(enabled);
            this.prioritizeCaseButton.setEnabled(enabled);
            this.deprioritizeJobButton.setEnabled(enabled);
            this.deprioritizeCaseButton.setEnabled(enabled);
        });
        finishedJobsPanel = new AutoIngestJobsPanel(AutoIngestNode.AutoIngestJobType.COMPLETED_JOB);
        finishedJobsPanel.setSize(completedScrollPane.getSize());
        completedScrollPane.add(finishedJobsPanel);
        completedScrollPane.setViewportView(finishedJobsPanel);
        finishedJobsPanel.addListSelectionListener((ListSelectionEvent e) -> {
            boolean enabled = false;
            this.prioritizeJobButton.setEnabled(enabled);
            this.prioritizeCaseButton.setEnabled(enabled);
            this.deprioritizeJobButton.setEnabled(enabled);
            this.deprioritizeCaseButton.setEnabled(enabled);
        });
        /*
         * Must set this flag, otherwise pop up menus don't close properly.
         */
        UIManager.put("PopupMenu.consumeEventOnClose", false);
    }

    /**
     * Update status of the services on the dashboard
     */
    private void displayServicesStatus() {
        tbServicesStatusMessage.setText(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message",
                statusByService.get(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()),
                statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()),
                statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()),
                statusByService.get(ServicesMonitor.Service.MESSAGING.toString())));
        String upStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
        if (statusByService.get(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()).compareTo(upStatus) != 0
                || statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()).compareTo(upStatus) != 0
                || statusByService.get(ServicesMonitor.Service.MESSAGING.toString()).compareTo(upStatus) != 0) {
            tbServicesStatusMessage.setForeground(Color.RED);
        } else {
            tbServicesStatusMessage.setForeground(Color.BLACK);
        }
    }

    /**
     * Queries the services monitor and sets the text for the services status
     * text box.
     */
    private void setServicesStatusMessage() {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                statusByService.put(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE));
                statusByService.put(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString(), getServiceStatus(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH));
                statusByService.put(ServicesMonitor.Service.MESSAGING.toString(), getServiceStatus(ServicesMonitor.Service.MESSAGING));
                return null;
            }

            /**
             * Gets a status string for a given service.
             *
             * @param service The service to test.
             *
             * @return The status string.
             */
            private String getServiceStatus(ServicesMonitor.Service service) {
                String serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Unknown");
                try {
                    ServicesMonitor servicesMonitor = ServicesMonitor.getInstance();
                    serviceStatus = servicesMonitor.getServiceStatus(service.toString());
                    if (serviceStatus.compareTo(ServicesMonitor.ServiceStatus.UP.toString()) == 0) {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                    } else {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down");
                    }
                } catch (ServicesMonitor.ServicesMonitorException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Dashboard error getting service status for %s", service), ex);
                }
                return serviceStatus;
            }

            @Override
            protected void done() {
                displayServicesStatus();
            }

        }.execute();
    }

    /**
     * Starts up the auto ingest monitor and adds this panel as an observer,
     * subscribes to services monitor events and starts a task to populate the
     * auto ingest job tables.
     */
    private void startUp() throws AutoIngestMonitor.AutoIngestMonitorException {

        PropertyChangeListener propChangeListener = (PropertyChangeEvent evt) -> {

            String serviceDisplayName = ServicesMonitor.Service.valueOf(evt.getPropertyName()).toString();
            String status = evt.getNewValue().toString();

            if (status.equals(ServicesMonitor.ServiceStatus.UP.toString())) {
                status = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                LOGGER.log(Level.INFO, "Connection to {0} is up", serviceDisplayName); //NON-NLS
            } else if (status.equals(ServicesMonitor.ServiceStatus.DOWN.toString())) {
                status = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down");
                LOGGER.log(Level.SEVERE, "Connection to {0} is down", serviceDisplayName); //NON-NLS
            } else {
                LOGGER.log(Level.INFO, "Status for {0} is {1}", new Object[]{serviceDisplayName, status}); //NON-NLS
            }

            // if the status update is for an existing service who's status hasn't changed - do nothing.       
            if (statusByService.containsKey(serviceDisplayName) && status.equals(statusByService.get(serviceDisplayName))) {
                return;
            }

            statusByService.put(serviceDisplayName, status);
            displayServicesStatus();
        };

        // Subscribe to all multi-user services in order to display their status
        Set<String> servicesList = new HashSet<>();
        servicesList.add(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString());
        servicesList.add(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString());
        servicesList.add(ServicesMonitor.Service.MESSAGING.toString());
        ServicesMonitor.getInstance().addSubscriber(servicesList, propChangeListener);

        autoIngestMonitor = new AutoIngestMonitor();
        autoIngestMonitor.addObserver(this);
        autoIngestMonitor.startUp();
    }

    @Override
    public void update(Observable observable, Object arg) {
         EventQueue.invokeLater(new RefreshComponentsTask((JobsSnapshot) arg));
    }

    /**
     * Reloads the table models using a jobs snapshot and refreshes the JTables
     * that use the models.
     *
     * @param jobsSnapshot The jobs snapshot.
     */
    private void refreshTables(JobsSnapshot jobsSnapshot) {
        pendingJobsPanel.refresh(jobsSnapshot);
        runningJobsPanel.refresh(jobsSnapshot);
        finishedJobsPanel.refresh(jobsSnapshot);
    }
    
    /**
     * Exception type thrown when there is an error completing an auto ingest
     * dashboard operation.
     */
    static final class AutoIngestDashboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         */
        private AutoIngestDashboardException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestDashboardException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        pendingScrollPane = new javax.swing.JScrollPane();
        runningScrollPane = new javax.swing.JScrollPane();
        completedScrollPane = new javax.swing.JScrollPane();
        lbPending = new javax.swing.JLabel();
        lbRunning = new javax.swing.JLabel();
        lbCompleted = new javax.swing.JLabel();
        refreshButton = new javax.swing.JButton();
        lbServicesStatus = new javax.swing.JLabel();
        tbServicesStatusMessage = new javax.swing.JTextField();
        prioritizeJobButton = new javax.swing.JButton();
        prioritizeCaseButton = new javax.swing.JButton();
        clusterMetricsButton = new javax.swing.JButton();
        deprioritizeJobButton = new javax.swing.JButton();
        deprioritizeCaseButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.jButton1.text")); // NOI18N

        pendingScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pendingScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        pendingScrollPane.setOpaque(false);
        pendingScrollPane.setPreferredSize(new java.awt.Dimension(2, 215));

        lbPending.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbPending.text")); // NOI18N

        lbRunning.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbRunning.text")); // NOI18N

        lbCompleted.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbCompleted.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.text")); // NOI18N
        refreshButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.toolTipText")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        lbServicesStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbServicesStatus.text")); // NOI18N

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(prioritizeJobButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeJobButton.text")); // NOI18N
        prioritizeJobButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeJobButton.toolTipText")); // NOI18N
        prioritizeJobButton.setEnabled(false);
        prioritizeJobButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prioritizeJobButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(prioritizeCaseButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeCaseButton.text")); // NOI18N
        prioritizeCaseButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.prioritizeCaseButton.toolTipText")); // NOI18N
        prioritizeCaseButton.setEnabled(false);
        prioritizeCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prioritizeCaseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(clusterMetricsButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.clusterMetricsButton.text")); // NOI18N
        clusterMetricsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clusterMetricsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deprioritizeJobButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.deprioritizeJobButton.text")); // NOI18N
        deprioritizeJobButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.deprioritizeJobButton.toolTipText")); // NOI18N
        deprioritizeJobButton.setEnabled(false);
        deprioritizeJobButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deprioritizeJobButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deprioritizeCaseButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.deprioritizeCaseButton.text")); // NOI18N
        deprioritizeCaseButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.deprioritizeCaseButton.toolTipText")); // NOI18N
        deprioritizeCaseButton.setEnabled(false);
        deprioritizeCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deprioritizeCaseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pendingScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(runningScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(completedScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(refreshButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(prioritizeJobButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deprioritizeJobButton, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(prioritizeCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deprioritizeCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(clusterMetricsButton))
                            .addComponent(lbPending, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbCompleted, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbRunning, javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(lbServicesStatus)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 861, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {clusterMetricsButton, prioritizeCaseButton, prioritizeJobButton, refreshButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbServicesStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbPending, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(1, 1, 1)
                .addComponent(pendingScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbRunning)
                .addGap(1, 1, 1)
                .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbCompleted)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(prioritizeJobButton)
                    .addComponent(prioritizeCaseButton)
                    .addComponent(clusterMetricsButton)
                    .addComponent(deprioritizeJobButton)
                    .addComponent(deprioritizeCaseButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles a click on the Refresh button. Requests a refreshed jobs snapshot
     * from the auto ingest monitor and uses it to refresh the UI components of
     * the panel.
     *
     * @param evt The button click event.
     */
    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        JobsSnapshot jobsSnapshot = autoIngestMonitor.refreshJobsSnapshot();
        refreshTables(jobsSnapshot);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_refreshButtonActionPerformed

    @Messages({"AutoIngestDashboard.errorMessage.jobPrioritization=Failed to prioritize job \"%s\"."})
    private void prioritizeJobButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prioritizeJobButtonActionPerformed
        AutoIngestJob job = pendingJobsPanel.getSelectedAutoIngestJob();
        if (job != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            JobsSnapshot jobsSnapshot;
            try {
                jobsSnapshot = autoIngestMonitor.prioritizeJob(job);
                refreshTables(jobsSnapshot);
            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                String errorMessage = String.format(Bundle.AutoIngestDashboard_errorMessage_jobPrioritization(), job.getManifest().getFilePath());
                LOGGER.log(Level.SEVERE, errorMessage, ex);
                MessageNotifyUtil.Message.error(errorMessage);
            }
            setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_prioritizeJobButtonActionPerformed

    @Messages({"AutoIngestDashboard.errorMessage.casePrioritization=Failed to prioritize case \"%s\"."})
    private void prioritizeCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prioritizeCaseButtonActionPerformed
        AutoIngestJob job = pendingJobsPanel.getSelectedAutoIngestJob();
        if (job != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String caseName = job.getManifest().getCaseName();
            JobsSnapshot jobsSnapshot;
            try {
                jobsSnapshot = autoIngestMonitor.prioritizeCase(caseName);
                refreshTables(jobsSnapshot);
            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                String errorMessage = String.format(Bundle.AutoIngestDashboard_errorMessage_casePrioritization(), caseName);
                LOGGER.log(Level.SEVERE, errorMessage, ex);
                MessageNotifyUtil.Message.error(errorMessage);
            }
            setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_prioritizeCaseButtonActionPerformed

    private void clusterMetricsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clusterMetricsButtonActionPerformed
        new AutoIngestMetricsDialog(this.getTopLevelAncestor());
    }//GEN-LAST:event_clusterMetricsButtonActionPerformed

    @Messages({"AutoIngestDashboard.errorMessage.jobDeprioritization=Failed to deprioritize job \"%s\"."})
    private void deprioritizeJobButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deprioritizeJobButtonActionPerformed
        AutoIngestJob job = pendingJobsPanel.getSelectedAutoIngestJob();
        if (job != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            JobsSnapshot jobsSnapshot;
            try {
                jobsSnapshot = autoIngestMonitor.deprioritizeJob(job);
                refreshTables(jobsSnapshot);
            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                String errorMessage = String.format(Bundle.AutoIngestDashboard_errorMessage_jobDeprioritization(), job.getManifest().getFilePath());
                LOGGER.log(Level.SEVERE, errorMessage, ex);
                MessageNotifyUtil.Message.error(errorMessage);
            }
            setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_deprioritizeJobButtonActionPerformed

    @Messages({"AutoIngestDashboard.errorMessage.caseDeprioritization=Failed to deprioritize case \"%s\"."})
    private void deprioritizeCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deprioritizeCaseButtonActionPerformed
        AutoIngestJob job = pendingJobsPanel.getSelectedAutoIngestJob();
        if (job != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String caseName = job.getManifest().getCaseName();
            JobsSnapshot jobsSnapshot;
            try {
                jobsSnapshot = autoIngestMonitor.deprioritizeCase(caseName);
                refreshTables(jobsSnapshot);
            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                String errorMessage = String.format(Bundle.AutoIngestDashboard_errorMessage_caseDeprioritization(), caseName);
                LOGGER.log(Level.SEVERE, errorMessage, ex);
                MessageNotifyUtil.Message.error(errorMessage);
            }
            setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_deprioritizeCaseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton clusterMetricsButton;
    private javax.swing.JScrollPane completedScrollPane;
    private javax.swing.JButton deprioritizeCaseButton;
    private javax.swing.JButton deprioritizeJobButton;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel lbCompleted;
    private javax.swing.JLabel lbPending;
    private javax.swing.JLabel lbRunning;
    private javax.swing.JLabel lbServicesStatus;
    private javax.swing.JScrollPane pendingScrollPane;
    private javax.swing.JButton prioritizeCaseButton;
    private javax.swing.JButton prioritizeJobButton;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane runningScrollPane;
    private javax.swing.JTextField tbServicesStatusMessage;
    // End of variables declaration//GEN-END:variables
    /**
     * A task that refreshes the UI components on this panel to reflect a
     * snapshot of the pending, running and completed auto ingest jobs lists of
     * an auto ingest cluster.
     */
    private class RefreshComponentsTask implements Runnable {

        private final JobsSnapshot jobsSnapshot;

        /**
         * Constructs a task that refreshes the UI components on this panel to
         * reflect a snapshot of the pending, running and completed auto ingest
         * jobs lists of an auto ingest cluster.
         *
         * @param jobsSnapshot The jobs snapshot.
         */
        RefreshComponentsTask(JobsSnapshot jobsSnapshot) {
            this.jobsSnapshot = jobsSnapshot;
        }

        @Override
        public void run() {
            refreshTables(jobsSnapshot);
        }
    }
}
