/*  ┌────────────────────────────────────────────────────────────────────┐
 *  │ RabbitMQToKairosDB                                                 │
 *  ├────────────────────────────────────────────────────────────────────┤
 *  │ Copyright © 2014 Hugo Sequeira (https://github.com/hugocore)       │
 *  ├────────────────────────────────────────────────────────────────────┤
 *  │ Licensed under the MIT license.                                    │
 *  ├────────────────────────────────────────────────────────────────────┤
 *  │ Plugin for KairosDB subscribe to RabbitMQ brokers.                 │
 *  └────────────────────────────────────────────────────────────────────┘
 */

package org.kairosdb.plugin.rabbitmq.core;

import javax.inject.Inject;

import org.kairosdb.core.KairosDBService;
import org.kairosdb.core.datastore.KairosDatastore;
import org.kairosdb.core.exception.KairosDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The Class RabbitmqService.
 */
public class RabbitmqService
        implements KairosDBService {

    /**
     * The Constant LOGGER.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(RabbitmqService.class);


    /**
     * The consumer thread.
     */
    private Thread consumerThread;

    /*
     * KairosDB variables
     */

    /**
     * The google injector.
     */
    @Inject
    private Injector googleInjector;

    /*
     * RabbitMQ variables
     */

    /**
     * The bindings file.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile")
    private String bindingsFile = "";

    /**
     * The rabbmitmq host.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.host")
    private String rabbmitmqHost = com.rabbitmq.client.ConnectionFactory.DEFAULT_HOST;


    /**
     * The rabbitmq virtual host.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.virtualhost")
    private String rabbitmqVirtualHost = com.rabbitmq.client.ConnectionFactory.DEFAULT_VHOST;


    /**
     * The rabbitmq user.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.username")
    private String rabbitmqUser = com.rabbitmq.client.ConnectionFactory.DEFAULT_USER;


    /**
     * The rabbitmq password.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.password")
    private String rabbitmqPassword = com.rabbitmq.client.ConnectionFactory.DEFAULT_PASS;


    /**
     * The rabbitmq port.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.port")
    private int rabbitmqPort = com.rabbitmq.client.ConnectionFactory.USE_DEFAULT_PORT;


    /**
     * The rabbitmq timeout.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.connectionTimeout")
    private int rabbitmqTimeout = com.rabbitmq.client.ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;


    /**
     * The rabbitmq channel max.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.requestedChannelMax")
    private int rabbitmqChannelMax = com.rabbitmq.client.ConnectionFactory.DEFAULT_CHANNEL_MAX;


    /**
     * The rabbitmq frame max.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.requestedFrameMax")
    private int rabbitmqFrameMax = com.rabbitmq.client.ConnectionFactory.DEFAULT_FRAME_MAX;


    /**
     * The rabbitmq hearbeat.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.requestedHeartbeat")
    private int rabbitmqHearbeat = com.rabbitmq.client.ConnectionFactory.DEFAULT_HEARTBEAT;


    /**
     * The configuration default content type.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.defaultContentType")
    private String configurationDefaultContentType = "JSON";


    /**
     * The configuration json field value.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.jsonfield.value")
    private String configurationJSONFieldValue = "value";


    /**
     * The configuration json time stamp.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.jsonfield.timestamp")
    private String configurationJSONTimeStamp = "timestamp";


    /**
     * The configuration json tags.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.jsonfield.tags")
    private String configurationJSONTags = "tags";


    /**
     * The configuration csv seperator.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.csv.seperator")
    private String configurationCSVSeperator = ",";

    /**
     * queueDurable - true if we are declaring a durable queue (the queue will survive a server restart)
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile.queue.properties.queueDurable")
    private boolean configurationDefaultQueueDurable = true;
    
    /**
     * queueExclusive - true if we are declaring an exclusive queue (restricted to this connection)
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile.queue.properties.queueExclusive")
    private boolean configurationDefaultQueueExclusive = false;
    
    /**
     * queueAutoDelete - true if we are declaring an auto-delete queue (server will delete it when no longer in use)
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile.queue.properties.queueAutoDelete")
    private boolean configurationDefaultQueueAutoDelete = false;

    /**
     * RabbitMQ auto-update bindings - true if the plugin should automatically update the queues and bindings when 
     * the bindingsfile changed (without restarting the KairosDB service)
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile.autoupdate")
    private boolean configurationAutoupdate = false;
    		
	/**
     * Timeout between checking for bindingsfile changes in seconds.
     */
    @Inject
    @Named("kairosdb.plugin.rabbitmq.bindingsfile.autoupdate.timeout")
    private long configurationAutoupdateTimeout = 300L;
    
    /*
     * (non-Javadoc)
     * 
     * @see org.kairosdb.core.KairosDBService#start()
     */
    @Override
    public void start() throws KairosDBException {

        try {
            LOGGER.info("[KRMQ] Starting to RabbitMQ consumer thread.");

            // Socket abstract connection with broker
            ConnectionFactory rabbitmqConnectionFactory = new ConnectionFactory();
            rabbitmqConnectionFactory.setHost(rabbmitmqHost);
            rabbitmqConnectionFactory.setVirtualHost(rabbitmqVirtualHost);
            rabbitmqConnectionFactory.setUsername(rabbitmqUser);
            rabbitmqConnectionFactory.setPassword(rabbitmqPassword);
            rabbitmqConnectionFactory.setPort(rabbitmqPort);
            rabbitmqConnectionFactory.setConnectionTimeout(rabbitmqTimeout);
            rabbitmqConnectionFactory.setRequestedChannelMax(rabbitmqChannelMax);
            rabbitmqConnectionFactory.setRequestedFrameMax(rabbitmqFrameMax);
            rabbitmqConnectionFactory.setRequestedHeartbeat(rabbitmqHearbeat);
            rabbitmqConnectionFactory.setAutomaticRecoveryEnabled(true);

            // Get KairosDatastore implementation
            KairosDatastore kairosDatabase = googleInjector.getInstance(KairosDatastore.class);

            // Create consumer thread
            RabbitmqConsumer consumer = new RabbitmqConsumer(kairosDatabase,
                    rabbitmqConnectionFactory, bindingsFile, configurationJSONFieldValue,
                    configurationJSONTimeStamp, configurationJSONTags, configurationCSVSeperator,
                    configurationDefaultContentType, configurationDefaultQueueDurable, 
                    configurationDefaultQueueExclusive, configurationDefaultQueueAutoDelete,
                    configurationAutoupdate, configurationAutoupdateTimeout);

            // Start consumer thread
            consumerThread = new Thread(consumer);
            consumerThread.start();

        } catch (Exception e) {
            LOGGER.error("[KRMQ] An error occurred: ", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.kairosdb.core.KairosDBService#stop()
     */
    @Override
    public void stop() {
        try {
            LOGGER.info("[KRMQ] Shutting down consumer thread.");
            consumerThread.interrupt();
        } catch (Exception e) {
            LOGGER.error("[KRMQ] An error occurred: ", e);
        }
    }

}
