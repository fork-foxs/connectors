package io.camunda.connector.sendgrid;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class SendGridFunction implements HttpFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendGridFunction.class);
  private static final Gson GSON = new GsonBuilder().setVersion(0.1).create();

  @Override
  public void service(final HttpRequest httpRequest, final HttpResponse httpResponse)
      throws Exception {
    final var request = GSON.fromJson(httpRequest.getReader(), SendGridRequest.class);
    LOGGER.info("Received request from cluster {}", request.getClusterId());

    final var secretStore = new SecretStore(GSON, request.getClusterId());
    request.replaceSecrets(secretStore);

    final var mail = createEmail(request);
    final Response response = sendEmail(request.getApiKey(), mail);
    LOGGER.info("Received response from SendGrid with code {}", response.getStatusCode());

    httpResponse.setStatusCode(response.getStatusCode());
    Optional.ofNullable(response.getHeaders().get("Content-Type"))
        .ifPresent(httpResponse::setContentType);
    httpResponse.getWriter().write(response.getBody());
  }

  private Mail createEmail(final SendGridRequest request) {
    final var mail = new Mail();

    mail.setFrom(toEmail(request.getFrom()));
    addContentIfPresent(mail, request);
    addTemplateIfPresent(mail, request);

    return mail;
  }

  private void addTemplateIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasTemplate()) {
      mail.setTemplateId(request.getTemplate().getId());

      final var personalization = new Personalization();
      personalization.addTo(toEmail(request.getTo()));
      request.getTemplate().getData().forEach(personalization::addDynamicTemplateData);
      mail.addPersonalization(personalization);
    }
  }

  private void addContentIfPresent(final Mail mail, final SendGridRequest request) {
    if (request.hasContent()) {
      final SendGridContent content = request.getContent();
      mail.setSubject(content.getSubject());
      mail.addContent(
          new com.sendgrid.helpers.mail.objects.Content(content.getType(), content.getValue()));
      final Personalization personalization = new Personalization();
      personalization.addTo(toEmail(request.getTo()));
      mail.addPersonalization(personalization);
    }
  }

  private Response sendEmail(final String apiKey, final Mail mail) throws IOException {
    final SendGrid sg = new SendGrid(apiKey);

    final com.sendgrid.Request request = new com.sendgrid.Request();
    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());
    return sg.api(request);
  }

  private Email toEmail(final SendGridEmail email) {
    return new Email(email.getEmail(), email.getName());
  }
}
