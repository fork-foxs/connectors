/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.awslambda.model.AwsLambdaResult;

@OutboundConnector(
    name = "AWS Lambda",
    inputVariables = {"authentication", "configuration", "awsFunction"},
    type = "io.camunda:aws-lambda:1")
public class LambdaConnectorFunction implements OutboundConnectorFunction {

  private final AwsLambdaSupplier awsLambdaSupplier;
  private final ObjectMapper objectMapper;

  public LambdaConnectorFunction() {
    this(new AwsLambdaSupplier(), ObjectMapperSupplier.getMapperInstance());
  }

  public LambdaConnectorFunction(
      final AwsLambdaSupplier awsLambdaSupplier, final ObjectMapper objectMapper) {
    this.awsLambdaSupplier = awsLambdaSupplier;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AwsLambdaRequest.class);
    return new AwsLambdaResult(invokeLambdaFunction(request), objectMapper);
  }

  private InvokeResult invokeLambdaFunction(AwsLambdaRequest request) {
    var region =
        AwsUtils.extractRegionOrDefault(
            request.getConfiguration(), request.getAwsFunction().getRegion());
    final AWSLambda awsLambda =
        awsLambdaSupplier.awsLambdaService(
            CredentialsProviderSupport.credentialsProvider(request), region);
    try {
      final InvokeRequest invokeRequest =
          new InvokeRequest()
              .withFunctionName(request.getAwsFunction().getFunctionName())
              .withPayload(objectMapper.writeValueAsString(request.getAwsFunction().getPayload()));
      return awsLambda.invoke(invokeRequest);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error mapping payload to json.");
    } finally {
      if (awsLambda != null) {
        awsLambda.shutdown();
      }
    }
  }
}
