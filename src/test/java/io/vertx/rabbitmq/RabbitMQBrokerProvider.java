/*
 * Copyright 2021 Eclipse.
 *
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
 */
package io.vertx.rabbitmq;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

/**
 * For tests that just require a generic instance of RabbitMQ that they will not attempt to shutdown.
 * @author jtalbut
 */
public class RabbitMQBrokerProvider {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQBrokerProvider.class);
  public static final String IMAGE_NAME = "rabbitmq:3.9.8-management-alpine";
  
  private static final Object lock = new Object();
  private static Network network;
  private static GenericContainer rabbitmq;
  
  private static GenericContainer rabbitmqWithPeerValidation;

  public static Network getNetwork() {
    synchronized(lock) {
      if (network == null) {
        network = Network.newNetwork();        
      }
    }
    return network;
  }
  
  public static GenericContainer getRabbitMqContainer() {
    synchronized(lock) {
      if (network == null) {
        network = Network.newNetwork();        
      }
      if (rabbitmq == null) {
        rabbitmq = new GenericContainer(IMAGE_NAME)
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/rabbitmq.conf"), "/etc/rabbitmq/rabbitmq.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/ca/ca_certificate.pem"), "/etc/rabbitmq/ca_certificate.pem")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/server/server_certificate.pem"), "/etc/rabbitmq/server_certificate.pem")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/server/private_key.pem"), "/etc/rabbitmq/server_key.pem")
                .withExposedPorts(5671, 5672, 15672)
                .withNetwork(network)
                //.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("TestContainer")))
                      ;
      }
      if (!rabbitmq.isRunning()) {
        rabbitmq.start();
        logger.info("Started test instance of RabbitMQ with ports {}"
                , rabbitmq.getExposedPorts().stream().map(p -> Integer.toString((Integer) p) + ":" + Integer.toString(rabbitmq.getMappedPort((Integer) p))).collect(Collectors.toList())
        );
      }
    }
    return rabbitmq;
  }

  public static GenericContainer getRabbitMqContainerWithPeerValidation() {
    synchronized(lock) {
      if (rabbitmqWithPeerValidation == null) {
        rabbitmqWithPeerValidation = new GenericContainer("rabbitmq:3.9.8-management-alpine")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/rabbitmq-peer.conf"), "/etc/rabbitmq/rabbitmq.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/ca/ca_certificate.pem"), "/etc/rabbitmq/ca_certificate.pem")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/server/server_certificate.pem"), "/etc/rabbitmq/server_certificate.pem")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/ssl-server/server/private_key.pem"), "/etc/rabbitmq/server_key.pem")
                .withExposedPorts(5671, 5672, 15672)
                //.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("TestContainer")))
                ;
        if (!rabbitmqWithPeerValidation.isRunning()) {
          rabbitmqWithPeerValidation.start();
          logger.info("Started test instance of RabbitMQ with ports {}"
                , rabbitmqWithPeerValidation.getExposedPorts().stream().map(p -> Integer.toString((Integer) p) + ":" + Integer.toString(rabbitmqWithPeerValidation.getMappedPort((Integer) p))).collect(Collectors.toList())
          );
        }
      }
    }
    return rabbitmqWithPeerValidation;
  }
}
