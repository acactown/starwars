package org.starwars;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.restassured.RestAssured;
import org.arquillian.cube.HostIp;
import org.arquillian.cube.HostPort;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

// IMPORTANT: You need to set starwars.planets var to docker image like starwars/planets:latest

@RunWith(Arquillian.class)
public class PlanetServiceAPIContainerTest {

    @HostIp
    private String ip;

    @HostPort(containerName = "planetstest", value = 8080)
    int planetsPort;

    @HostPort(containerName = "swapi", value = 80)
    int swapiPort;

    @Before
    public void setup() {
        WireMock.configureFor(ip, swapiPort);
    }

    @After
    public void clean() {
        WireMock.reset();
    }

    @Test
    public void shouldReturnTheAverage() throws IOException {

        // Prepare swapi recording
        File configFile = new File(Thread.currentThread().getContextClassLoader()
                .getResource("planets.json").getFile());

        stubFor(get(urlEqualTo("/api/planets/"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(Files.readAllBytes(configFile.toPath())))
        );

        // Launch our service which need swapi.
        URL url = new URL("http://" + ip + ":" + planetsPort + "/starwars/");
        final String average = RestAssured.get(url.toExternalForm() + "rest/planet/orbital/average").asString();
        assertThat(average, is("1699.42"));
    }
}
