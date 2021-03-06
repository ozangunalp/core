/*
 * #%L
 * OW2 Chameleon - Core
 * %%
 * Copyright (C) 2009 - 2014 OW2 Chameleon
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.ow2.chameleon.core;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.ow2.chameleon.core.activators.*;
import org.ow2.chameleon.core.hook.HookManager;
import org.ow2.chameleon.core.services.Stability;
import org.ow2.chameleon.core.utils.FrameworkManager;
import org.ow2.chameleon.core.utils.LogbackUtil;
import org.ow2.chameleon.core.utils.jul.JulLogManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chameleon main entry point.
 *
 * @author The OW2 Chameleon Team
 * @version $Id: 1.0.4 $Id
 */
public class Chameleon {

    /**
     * A system property to set the chameleon location externally.
     */
    public static final String CHAMELEON_BASEDIR = "chameleon.base";

    /**
     * The framework manager holding the OSGi framework instance.
     */
    private final FrameworkManager manager;

    /**
     * Chameleon Logger.
     * This logger is not shared among instances.
     */
    private final Logger logger; //NOSONAR

    /**
     * List of activator to start during framework startup.
     */
    private List<BundleActivator> activators = new ArrayList<BundleActivator>();

    /**
     * The hook manager.
     */
    private final HookManager hooks;

    /**
     * Creates a chameleon instance.
     *
     * @param basedir        the base directory
     * @param interactive    if the interactive mode enabled.
     * @param userProperties the system properties provided by the user in command line
     * @throws java.io.IOException if the chameleon instance cannot be created.
     */
    public Chameleon(File basedir, boolean interactive, Map<String, Object> userProperties) throws IOException {
        // Configure the log manager.
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", JulLogManager.class.getName());
        }

        hooks = new HookManager();
        hooks.load();
        hooks.fireInitializing();


        ChameleonConfiguration configuration = new ChameleonConfiguration(basedir);
        configuration.setInteractiveModeEnabled(interactive);
        configuration.initialize(userProperties);
        configuration.initFrameworkConfiguration();


        logger = initializeLoggingSystem(configuration);

        initializeActivatorList(configuration);

        manager = new FrameworkManager(this, configuration);
        manager.addActivators(activators);
    }

    /**
     * Creates a chameleon instance.
     *
     * @param configuration the configuration to use
     * @throws java.io.IOException if the chameleon instance cannot be created.
     */
    public Chameleon(ChameleonConfiguration configuration) throws IOException {
        // Configure the log manager.
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", JulLogManager.class.getName());
        }

        hooks = new HookManager();
        hooks.load();
        hooks.fireInitializing();


        configuration.setInteractiveModeEnabled(false);
        configuration.initialize(null);
        configuration.initFrameworkConfiguration();

        logger = initializeLoggingSystem(configuration);

        initializeActivatorList(configuration);

        manager = new FrameworkManager(this, configuration);
        manager.addActivators(activators);
    }

    /**
     * Creates a Chameleon instance. This constructor does not allows to set the
     * core directory (so, uses 'core'), nor the chameleon properties.
     * <p>
     * Notice that if the 'chameleon.base' system property is set, it uses this location.
     *
     * @param interactive is the debug mode enabled.
     * @throws java.io.IOException something wrong happens.
     */
    public Chameleon(boolean interactive) throws IOException {
        this(
                System.getProperty(CHAMELEON_BASEDIR) != null ? new File(System.getProperty(CHAMELEON_BASEDIR)) :
                        new File(""), interactive, null
        );
    }

    /**
     * Creates a Chameleon instance. This constructor does not allows to set the
     * core directory (so, uses 'core'), nor the chameleon properties.
     *
     * @param basedir     the chameleon's base directory
     * @param interactive is the debug mode enabled.
     * @throws java.io.IOException something wrong happens.
     */
    public Chameleon(String basedir, boolean interactive) throws IOException {
        this(new File(basedir), interactive, null);
    }

    /**
     * Creates a Chameleon instance. This constructor does not allows to set the
     * core directory (so, uses 'core'), nor the chameleon properties, but support user properties specified by the
     * user using <tt>-Dxxx=yyy</tt> in the command line arguments.
     * <p>
     * Notice that if the 'chameleon.base' system property is set, it uses this location.
     *
     * @param interactive    is the debug mode enabled.
     * @param userProperties the user properties
     * @throws java.io.IOException something wrong happens.
     */
    public Chameleon(boolean interactive, Map<String, Object> userProperties) throws IOException {
        this(System.getProperty(CHAMELEON_BASEDIR) != null ? new File(System.getProperty(CHAMELEON_BASEDIR)) :
                new File(""), interactive, userProperties);
    }

    /**
     * Initialized the logging framework (backend).
     *
     * @param configuration chameleon's configuration.
     * @return the chameleon logger
     */
    public static Logger initializeLoggingSystem(ChameleonConfiguration configuration) {
        Logger log = LogbackUtil.configure(configuration); //NOSONAR ignore name issue has we are building the instance.

        if (configuration.isInteractiveModeEnabled()) {
            log.debug("interactive mode enabled");
        }

        return log;
    }

    private void initializeActivatorList(ChameleonConfiguration configuration) {
        File core = configuration.getDirectory(Constants.CHAMELEON_CORE_PROPERTY, true);
        if (core == null) {
            throw new IllegalArgumentException("The " + Constants.CHAMELEON_CORE_PROPERTY + " property is missing in " +
                    "the " + Constants.CHAMELEON_PROPERTIES_FILE + " file.");
        }

        File runtime = configuration.getDirectory(Constants.CHAMELEON_RUNTIME_PROPERTY, true);
        if (runtime == null) {
            throw new IllegalArgumentException("The " + Constants.CHAMELEON_RUNTIME_PROPERTY + " property is missing in " +
                    "the " + Constants.CHAMELEON_PROPERTIES_FILE + " file.");
        }

        File application = configuration.getDirectory(Constants.CHAMELEON_APPLICATION_PROPERTY, true);
        if (application == null) {
            throw new IllegalArgumentException("The " + Constants.CHAMELEON_APPLICATION_PROPERTY + " property is missing in " +
                    "the " + Constants.CHAMELEON_PROPERTIES_FILE + " file.");
        }

        activators.add(new LogActivator(logger));
        activators.add(new CoreActivator(core, configuration.isInteractiveModeEnabled()));

        // The main watcher.
        DirectoryMonitor monitor = new DirectoryMonitor();
        activators.add(monitor);

        boolean monitoringRuntime = configuration.getBoolean(Constants.CHAMELEON_RUNTIME_MONITORING_PROPERTY, false);
        boolean monitoringApplication = configuration.getBoolean(Constants.CHAMELEON_APPLICATION_MONITORING_PROPERTY, true);
        int monitoringPeriod = configuration.getInt(Constants.CHAMELEON_MONITORING_PERIOD_PROPERTY, 2000);
        boolean autoRefresh = configuration.getBoolean(Constants.CHAMELEON_AUTO_REFRESH, true);

        if (monitoringRuntime) {
            monitor.add(runtime, monitoringPeriod);
        } else {
            monitor.add(runtime, false);
        }

        if (monitoringApplication) {
            monitor.add(application, monitoringPeriod);
        } else {
            monitor.add(application, false);
        }

        // The deployers
        activators.add(new BundleDeployer(false, autoRefresh));
        activators.add(new ConfigDeployer());

        // Stability checker
        activators.add(new StabilityComputation());
    }

    /**
     * Initializes and Starts the Chameleon frameworks. It configures the
     * embedded OSGi framework and deploys bundles.
     *
     * @return the current Chameleon instance
     * @throws org.osgi.framework.BundleException if a bundle cannot be installed or started
     *                                            correctly.
     */
    public Chameleon start() throws BundleException {
        hooks.fireConfigured(manager.configuration());
        manager.start();
        return this;
    }

    /**
     * Waits for the stability to be reached.
     *
     * @return the current Chameleon instance
     * @throws IllegalStateException if the stability cannot be reached
     * @since 1.10.6
     */
    public Chameleon waitForStability() {
        ServiceReference<Stability> reference
                = context().getServiceReference(Stability.class);
        if (reference == null) {
            throw new IllegalStateException("Cannot reach stability - stability service missing");
        }
        Stability stability = context().getService(reference);

        if (! stability.waitForStability()) {
            throw new IllegalStateException("Cannot reach stability");
        }
        return this;
    }

    /**
     * Stops the underlying framework.
     *
     * @return the current Chameleon instance
     * @throws org.osgi.framework.BundleException should not happen.
     * @throws java.lang.InterruptedException     if the method is interrupted during the
     *                                            waiting time.
     */
    public Chameleon stop() throws BundleException, InterruptedException {
        logger.info("Stopping Chameleon");
        manager.stop();
        logger.info("Chameleon stopped");
        hooks.fireShuttingDown();
        return this;
    }

    /**
     * Retrieves the bundle context of the underlying framework.
     * The framework must have been successfully started first.
     *
     * @return the bundle context
     */
    public BundleContext context() {
        return manager.get().getBundleContext();
    }

    /**
     * Retrieves the underlying framework.
     * The framework must have been successfully started first.
     *
     * @return the bundle context
     */
    public Framework framework() {
        return manager.get();
    }

}
