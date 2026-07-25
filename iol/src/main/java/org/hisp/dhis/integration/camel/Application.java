/*
 * Copyright (c) 2004-2025, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.camel;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class Application extends SpringBootServletInitializer implements CamelContextConfiguration {

  protected static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

  @Value("${fhirServerUrl}")
  private String fhirServerUrl;

  @Value("${dhis2.apiUrl}")
  private String dhis2ApiUrl;

  @Value("${dhis2.username:#{null}}")
  private String dhis2ApiUsername;

  @Value("${dhis2.password:#{null}}")
  private String dhis2ApiPassword;

  @Value("${dhis2.pat:#{null}}")
  private String dhis2Pat;

  @Value("${dhis2.timeout.read:10000}")
  private int dhis2ReadTimeoutMs;

  @Autowired private ConfigurableApplicationContext applicationContext;

  public static void main(String[] args) {
    SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.run(args);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return JsonMapper.builder().findAndAddModules().build();
  }

  @Bean
  public IGenericClient fhirClient() {
    FhirContext fhirContext = FhirVersionEnum.R4.newContext();
    fhirContext.getRestfulClientFactory().setSocketTimeout(50000);
    return fhirContext.newRestfulGenericClient(fhirServerUrl);
  }

  @Bean
  public Dhis2Client dhis2Client() {
    if (!StringUtils.hasText(dhis2ApiUrl)) {
      terminate("Missing DHIS2 API URL. Are you sure that you set `dhis2.api.url`?");
    }

    if (dhis2Pat != null && (dhis2ApiUsername != null || dhis2ApiPassword != null)) {
      terminate(
          "Bad DHIS2 authentication configuration: PAT authentication and basic authentication are mutually exclusive. Either set `dhis2.api.pat` or both `dhis2.api.username` and `dhis2.api.password`");
    }

    Dhis2ClientBuilder dhis2ClientBuilder = null;
    if (StringUtils.hasText(dhis2Pat)) {
      dhis2ClientBuilder = Dhis2ClientBuilder.newClient(dhis2ApiUrl, dhis2Pat);
    } else if (StringUtils.hasText(dhis2ApiUsername) && StringUtils.hasText(dhis2ApiPassword)) {
      dhis2ClientBuilder =
          Dhis2ClientBuilder.newClient(dhis2ApiUrl, dhis2ApiUsername, dhis2ApiPassword);
    } else {
      terminate(
          "Missing DHIS2 authentication details. Are you sure that you set `dhis2.api.pat` or both `dhis2.api.username` and `dhis2.api.password`?");
    }

    return dhis2ClientBuilder.withReadTimeout(dhis2ReadTimeoutMs, TimeUnit.MILLISECONDS).build();
  }

  @Override
  public void beforeApplicationStart(CamelContext camelContext) {
    camelContext.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
    camelContext.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
  }

  @Override
  public void afterApplicationStart(CamelContext camelContext) {}

  protected void terminate(String shutdownMessage) {
    LOGGER.error("TERMINATING!!! " + shutdownMessage);
    applicationContext.close();
    System.exit(1);
  }
}
