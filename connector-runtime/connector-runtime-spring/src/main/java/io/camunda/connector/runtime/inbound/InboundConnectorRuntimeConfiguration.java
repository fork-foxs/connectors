/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package io.camunda.connector.runtime.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.DefaultProcessElementContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.ProcessElementContextFactory;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.controller.InboundConnectorRestController;
import io.camunda.connector.runtime.inbound.controller.InboundInstancesRestController;
import io.camunda.connector.runtime.inbound.controller.exception.GlobalExceptionHandler;
import io.camunda.connector.runtime.inbound.executable.BatchExecutableProcessor;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistryImpl;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImportConfiguration;
import io.camunda.connector.runtime.inbound.search.ProcessInstanceClientConfiguration;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.search.SearchQueryClientImpl;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.connector.runtime.inbound.state.TenantAwareProcessStateStoreImpl;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  ProcessDefinitionImportConfiguration.class,
  ProcessInstanceClientConfiguration.class,
  InboundConnectorRestController.class,
  InboundInstancesRestController.class,
  GlobalExceptionHandler.class,
})
public class InboundConnectorRuntimeConfiguration {
  @Value("${camunda.connector.inbound.message.ttl:PT1H}")
  private Duration messageTtl;

  @Bean
  public static InboundConnectorBeanDefinitionProcessor inboundConnectorBeanDefinitionProcessor() {
    return new InboundConnectorBeanDefinitionProcessor();
  }

  @Bean
  public ProcessElementContextFactory processElementContextFactory(
      ObjectMapper objectMapper,
      @Autowired(required = false) ValidationProvider validationProvider,
      SecretProviderAggregator secretProviderAggregator) {
    return new DefaultProcessElementContextFactory(
        secretProviderAggregator, validationProvider, objectMapper);
  }

  @Bean
  public InboundCorrelationHandler inboundCorrelationHandler(
      final CamundaClient camundaClient,
      final FeelEngineWrapper feelEngine,
      final MetricsRecorder metricsRecorder,
      final ProcessElementContextFactory elementContextFactory) {
    return new MeteredInboundCorrelationHandler(
        camundaClient, feelEngine, metricsRecorder, elementContextFactory, messageTtl);
  }

  @Bean
  public InboundConnectorContextFactory springInboundConnectorContextFactory(
      ObjectMapper mapper,
      InboundCorrelationHandler correlationHandler,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      ProcessInstanceClient processInstanceClient,
      DocumentFactory documentFactory) {
    return new DefaultInboundConnectorContextFactory(
        mapper,
        correlationHandler,
        secretProviderAggregator,
        validationProvider,
        processInstanceClient,
        documentFactory);
  }

  @Bean
  public InboundConnectorFactory springInboundConnectorFactory() {
    return new DefaultInboundConnectorFactory();
  }

  @Bean
  public BatchExecutableProcessor batchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      MetricsRecorder metricsRecorder,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry) {
    return new BatchExecutableProcessor(
        connectorFactory, connectorContextFactory, metricsRecorder, webhookConnectorRegistry);
  }

  @Bean
  public InboundExecutableRegistry inboundExecutableRegistry(
      InboundConnectorFactory inboundConnectorFactory,
      BatchExecutableProcessor batchExecutableProcessor) {
    return new InboundExecutableRegistryImpl(inboundConnectorFactory, batchExecutableProcessor);
  }

  @Bean
  SearchQueryClient searchQueryClient(CamundaClient camundaClient) {
    return new SearchQueryClientImpl(camundaClient);
  }

  @Bean
  public ProcessDefinitionInspector processDefinitionInspector(
      SearchQueryClient searchQueryClient) {
    return new ProcessDefinitionInspector(searchQueryClient);
  }

  @Bean
  public ProcessStateStore processStateStore(
      InboundExecutableRegistry registry, ProcessDefinitionInspector inspector) {
    return new TenantAwareProcessStateStoreImpl(inspector, registry);
  }
}
