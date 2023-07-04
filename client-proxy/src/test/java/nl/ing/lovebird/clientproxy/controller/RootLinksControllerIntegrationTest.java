package nl.ing.lovebird.clientproxy.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("test")
class RootLinksControllerIntegrationTest {
    @Autowired
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void rootlinksOnPath() {
        ResponseEntity<String> rootlinks = this.restTemplate.getForEntity("/rootlinks.json", String.class);
        Assertions.assertEquals(HttpStatus.OK, rootlinks.getStatusCode());
        assertThat(rootlinks.getBody(), hasJsonPath("$._links.sitesV2.href", equalTo("/v2/sites")));
        assertEquals("application/hal+json;charset=UTF-8", rootlinks.getHeaders().getContentType().toString());
    }

    @Test
    void rootlinksOnRoot() {
        ResponseEntity<String> rootlinks = this.restTemplate.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, rootlinks.getStatusCode());
        assertThat(rootlinks.getBody(), hasJsonPath("$._links.sitesV2.href", equalTo("/v2/sites")));
        assertEquals("application/hal+json;charset=UTF-8", rootlinks.getHeaders().getContentType().toString());
    }
}
