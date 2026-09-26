/*
 * Copyright (c) 2004-2026, University of Oslo
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

import java.time.Duration;

import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamelSpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
public class AbstractFunctionalTestCase {

  @Container public static GenericContainer<?> DHIS2_CONTAINER;

  @Container public static GenericContainer<?> DHIS2_DB_CONTAINER;

  private static GenericContainer<?> newDhis2Container() {
    Network.NetworkImpl dhis2Network = Network.builder().build();

    DHIS2_DB_CONTAINER = newPostgreSQLContainer("dhis", "dhis", "dhis", dhis2Network);
    DHIS2_DB_CONTAINER.start();
    System.setProperty("dhis2DatabasePort", DHIS2_DB_CONTAINER.getFirstMappedPort().toString());

    return new GenericContainer<>("dhis2/core:42.5.1")
        .withClasspathResourceMapping("dhis.conf", "/opt/dhis2/dhis.conf", BindMode.READ_WRITE)
        .withNetwork(dhis2Network)
        .withExposedPorts(8080)
        .dependsOn(DHIS2_DB_CONTAINER)
        .waitingFor(
            new HttpWaitStrategy().forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)))
        .withEnv("WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0");
  }

  private static PostgreSQLContainer<?> newPostgreSQLContainer(
      String databaseName, String username, String password, Network network) {
    return new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:12-3.2-alpine")
                .asCompatibleSubstituteFor("postgres"))
        .withFileSystemBind("../data/dhis2/db-dump", "/docker-entrypoint-initdb.d/", BindMode.READ_ONLY)
        .withExposedPorts(5432)
        .withDatabaseName(databaseName)
        .withNetworkAliases("db")
        .withUsername(username)
        .withPassword(password)
        .withNetwork(network);
  }

  @BeforeAll
  public static void beforeAll() {
    if (DHIS2_CONTAINER == null) {
      DHIS2_CONTAINER = newDhis2Container();
      DHIS2_CONTAINER.start();
    }
    System.setProperty("lis.api.url", "http://localhost:8081/fhir");
    String dhis2ApiUrl =
            String.format(
                    "http://%s:%s/api", DHIS2_CONTAINER.getHost(), DHIS2_CONTAINER.getFirstMappedPort());
    System.setProperty("dhis2.api.url", dhis2ApiUrl);
  }
}
