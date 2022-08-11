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
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SlackConnectorRequestTest extends BaseTest {

  private SlackRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new SlackRequest();
    validator = new Validator();
    context =
        ConnectorContextBuilder.create()
            .secret(TOKEN_KEY, ACTUAL_TOKEN)
            .secret(METHOD, ACTUAL_METHOD)
            .secret(CHANNEL_KEY, ACTUAL_CHANNEL)
            .secret(TEXT_KEY, ACTUAL_TEXT)
            .build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    request.setToken(TOKEN);
    request.setMethod(METHOD);
    request.setData(null);
    request.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage())
        .isEqualTo("Evaluation failed with following errors: Property required: Slack API - Data");
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMethod() {
    request.setMethod(ACTUAL_METHOD);
    ConnectorContext context =
        ConnectorContextBuilder.create().secret(ACTUAL_METHOD, METHOD).build();
    context.replaceSecrets(request);
    assertThat(request.getMethod()).isEqualTo(ACTUAL_METHOD);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    ChatPostMessageData chatPostMessageData = new ChatPostMessageData();
    chatPostMessageData.setChannel(SECRETS + CHANNEL_KEY);
    chatPostMessageData.setText(SECRETS + TEXT_KEY);
    request.setToken(SECRETS + TOKEN_KEY);
    request.setMethod(ACTUAL_METHOD);
    request.setData(chatPostMessageData);
    context.replaceSecrets(request);
    assertThat(request.getMethod()).isEqualTo(ACTUAL_METHOD);
    assertThat(request.getToken()).isEqualTo(ACTUAL_TOKEN);
    assertThat(request.getData()).isInstanceOf(ChatPostMessageData.class);
  }
}
