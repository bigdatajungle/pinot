/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.server.starter;

import com.linkedin.pinot.common.metrics.MetricsHelper;
import com.linkedin.pinot.common.metrics.ServerMetrics;
import com.yammer.metrics.core.MetricsRegistry;
import java.io.File;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.common.data.DataManager;
import com.linkedin.pinot.common.query.QueryExecutor;
import com.linkedin.pinot.server.conf.NettyServerConfig;
import com.linkedin.pinot.server.conf.ServerConf;
import com.linkedin.pinot.server.request.SimpleRequestHandlerFactory;
import com.linkedin.pinot.transport.netty.NettyServer;
import com.linkedin.pinot.transport.netty.NettyServer.RequestHandlerFactory;
import com.linkedin.pinot.transport.netty.NettyTCPServer;


/**
 * Initialize a ServerBuilder with serverConf file.
 *
 *
 */
public class ServerBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServerBuilder.class);
  public static final String PINOT_PROPERTIES = "pinot.properties";

  private final ServerConf _serverConf;

  private ServerMetrics _serverMetrics;

  private MetricsRegistry metricsRegistry;

  public ServerConf getConfiguration() {
    return _serverConf;
  }

  /**
   * Construct from config file path
   * @param configFilePath Path to the config file
   * @param metricsRegistry
   * @throws Exception
   */
  public ServerBuilder(File configFilePath, MetricsRegistry metricsRegistry) throws Exception {
    this.metricsRegistry = metricsRegistry;
    if (!configFilePath.exists()) {
      LOGGER.error("configuration file: " + configFilePath.getAbsolutePath() + " does not exist.");
      throw new ConfigurationException("configuration file: " + configFilePath.getAbsolutePath() + " does not exist.");
    }

    // build _serverConf
    PropertiesConfiguration serverConf = new PropertiesConfiguration();
    serverConf.setDelimiterParsingDisabled(false);
    serverConf.load(configFilePath);
    _serverConf = new ServerConf(serverConf);

    initMetrics();
  }

  /**
   * Construct from config directory and a config file which resides under it
   * @param confDir Directory under which config file is present
   * @param file Config File
   * @param metricsRegistry
   * @throws Exception
   */
  public ServerBuilder(String confDir, String file, MetricsRegistry metricsRegistry) throws Exception {
    this(new File(confDir, file), metricsRegistry);
  }

  /**
   * Construct from config directory and default config file
   * @param confDir Directory under which pinot.properties file is present
   * @param metricsRegistry
   * @throws Exception
   */
  public ServerBuilder(String confDir, MetricsRegistry metricsRegistry) throws Exception {
    this(new File(confDir, PINOT_PROPERTIES), metricsRegistry);
  }

  /**
   * Initialize with Configuration file
   * @param config object
   * @param metricsRegistry
   */
  public ServerBuilder(Configuration config, MetricsRegistry metricsRegistry) {
    this.metricsRegistry = metricsRegistry;
    _serverConf = new ServerConf(config);
    initMetrics();
  }

  /**
   * Initialize with ServerConf object
   * @param serverConf object
   * @param metricsRegistry
   */
  public ServerBuilder(ServerConf serverConf, MetricsRegistry metricsRegistry) {
    _serverConf = serverConf;
    this.metricsRegistry = metricsRegistry;
    initMetrics();
  }

  /**
   * Build Instance DataManager
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws ClassNotFoundException
   */
  public DataManager buildInstanceDataManager() throws InstantiationException, IllegalAccessException,
      ClassNotFoundException {
    String className = _serverConf.getInstanceDataManagerClassName();
    LOGGER.info("Trying to Load Instance DataManager by Class : " + className);
    DataManager instanceDataManager = (DataManager) Class.forName(className).newInstance();
    instanceDataManager.init(_serverConf.getInstanceDataManagerConfig());
    return instanceDataManager;
  }

  /**
   * Build QueryExecutor
   * @param instanceDataManager
   * @return
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws ClassNotFoundException
   * @throws ConfigurationException
   */
  public QueryExecutor buildQueryExecutor(DataManager instanceDataManager) throws InstantiationException,
      IllegalAccessException, ClassNotFoundException, ConfigurationException {
    String className = _serverConf.getQueryExecutorClassName();
    LOGGER.info("Trying to Load Query Executor by Class : " + className);
    QueryExecutor queryExecutor = (QueryExecutor) Class.forName(className).newInstance();
    queryExecutor.init(_serverConf.getQueryExecutorConfig(), instanceDataManager, _serverMetrics);
    return queryExecutor;
  }

  /**
   * Build RequestHandlerFactory
   * @param queryExecutor
   * @return
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws ClassNotFoundException
   */
  public RequestHandlerFactory buildRequestHandlerFactory(QueryExecutor queryExecutor) throws InstantiationException,
      IllegalAccessException, ClassNotFoundException {
    String className = _serverConf.getRequestHandlerFactoryClassName();
    LOGGER.info("Trying to Load Request Handler Factory by Class : " + className);
    RequestHandlerFactory requestHandlerFactory = new SimpleRequestHandlerFactory(queryExecutor, _serverMetrics);
    return requestHandlerFactory;
  }

  public NettyServer buildNettyServer(NettyServerConfig nettyServerConfig, RequestHandlerFactory requestHandlerFactory) {
    LOGGER.info("Trying to build NettyTCPServer with port : " + nettyServerConfig.getPort());
    NettyServer nettyServer = new NettyTCPServer(nettyServerConfig.getPort(), requestHandlerFactory, null);
    return nettyServer;
  }

  private void initMetrics() {
    MetricsHelper.initializeMetrics(_serverConf.getMetricsConfig());
    MetricsHelper.registerMetricsRegistry(metricsRegistry);
    _serverMetrics = new ServerMetrics(metricsRegistry);
    _serverMetrics.initializeGlobalMeters();
  }
}
