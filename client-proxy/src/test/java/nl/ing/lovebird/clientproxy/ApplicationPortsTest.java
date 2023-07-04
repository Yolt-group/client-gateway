package nl.ing.lovebird.clientproxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {ClientProxyApplication.class})
@DirtiesContext
@ActiveProfiles("test")
class ApplicationPortsTest {

    @LocalServerPort
    Integer serverPort;

    @LocalManagementPort
    Integer managementPort;

    TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
    }

    @Test
    void actuatorEndpointsShouldNotBeMappedToAppContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(serverPort, "/actuator"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void infoEndpointShouldNotBeMappedToAppContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(serverPort, "/actuator/info"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthEndpointShouldNotBeMappedToAppContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(serverPort, "/actuator/health"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void metricsEndpointShouldNotBeMappedToAppContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(serverPort, "/actuator/metrics"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void prometheusEndpointShouldNotBeMappedToAppContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(serverPort, "/actuator/prometheus"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actuatorEndpointsShouldBeMappedToManagementContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(managementPort, "/actuator"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void infoEndpointShouldBeMappedToManagementContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(managementPort, "/actuator/info"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthEndpointShouldBeMappedToManagementContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(managementPort, "/actuator/health"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void metricsEndpointShouldBeMappedToManagementContext() {
        ResponseEntity<Void> entity = restTemplate.getForEntity(buildUrl(managementPort, "/actuator/metrics"), Void.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheusEndpointShouldBeMappedToManagementContext() {
        ResponseEntity<String> entity = restTemplate.getForEntity(buildUrl(managementPort, "/actuator/prometheus"), String.class);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static String buildUrl(Integer port, String path) {
        return "http://localhost:" + port + path;
    }
}
