package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.requester.service.ClientTokenRequesterService;
import org.apache.tomcat.util.codec.binary.Base64;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTokenFilterTest {

    private ClientTokenFilter clientTokenFilter;
    @Mock
    private ClientTokenRequesterService clientTokenRequesterService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);
        clientTokenFilter = new ClientTokenFilter(new ZuulHeadersHelper(), clientTokenRequesterService);
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void filterType() {
        var result = clientTokenFilter.filterType();
        assertThat(result).isEqualTo("pre");
    }

    @Test
    void filterOrder() {
        var result = clientTokenFilter.filterOrder();
        assertThat(result).isEqualTo(11);
    }

    @Test
    void shouldFilter_withClientIdPresent_shouldReturnTrue() {
        RequestContext.getCurrentContext().addZuulRequestHeader(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, clientId.toString());

        var result = clientTokenFilter.shouldFilter();

        assertThat(result).isTrue();
    }

    @Test
    void shouldFilter_withClientIdNotPresent_shouldReturnFalse() {
        var result = clientTokenFilter.shouldFilter();

        assertThat(result).isFalse();
    }

    @Test
    void run() {
        RequestContext.getCurrentContext().addZuulRequestHeader(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, clientId.toString());
        UUID clientGroupId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims();
        claims.setClaim("sub", "client:" + clientId);
        claims.setClaim("client-id", clientId.toString());
        claims.setClaim("client-group-id", clientGroupId);
        claims.setClaim("isf", "site-management");
        claims.setClaim("psd2-licensed", false);
        claims.setClaim("ais", true);
        claims.setClaim("pis", true);
        claims.setClaim("cam", true);
        claims.setClaim("client-users-kyc-private-individuals", true);
        claims.setClaim("client-users-kyc-entities", false);
        claims.setClaim("data_enrichment_categorization", true);
        claims.setClaim("data_enrichment_merchant_recognition", true);
        claims.setClaim("data_enrichment_cycle_detection", false);
        claims.setClaim("data_enrichment_labels", false);
        claims.setClaim("one_off_ais", false);
        String serialized = Base64.encodeBase64String(String.format("fake-client-token-for-%s", clientId.toString()).getBytes());
        ClientToken clientToken = new ClientToken(serialized, claims);

        when(clientTokenRequesterService.getClientToken(clientId)).thenReturn(clientToken);

        clientTokenFilter.run();

        var ctx = RequestContext.getCurrentContext();
        assertThat(ctx.get("client-token")).isEqualTo(clientToken);
        assertThat(ctx.getZuulRequestHeaders()).containsEntry("client-token", serialized);
    }
}