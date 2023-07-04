package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.exception.UnknownClientUserException;
import nl.ing.lovebird.clientproxy.service.ClientUserService;
import nl.ing.lovebird.clientproxy.service.ClientUsersClient;
import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import nl.ing.lovebird.clienttokens.ClientUserToken;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientUserToUserMappingFilterTest {

    private static final UUID CLIENT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");
    private static final UUID CLIENT_USER_ID = UUID.fromString("22223333-4444-5555-6666-777788889999");
    private static final String UNKNOWN_PROFILE_ID = "24243535-4444-5555-6666-777788889999";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-1111-0000-000000000000");
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private ClientUserToUserMappingFilter clientUserToUserMappingFilter;

    @Mock
    private ClientUserService usersService;
    @Mock
    private ClientTokenRequesterService clientTokenRequesterService;

    @BeforeEach
    public void setup() {
        clientUserToUserMappingFilter = new ClientUserToUserMappingFilter(empty(), usersService, new ZuulHeadersHelper(), clientTokenRequesterService);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);
    }

    @AfterEach
    public void after() {
        RequestContext.getCurrentContext().clear();
    }

    @Test
    void shouldRunFilterWhenClientIdAndProfileIdArePresent() {
        RequestContext.getCurrentContext().addZuulRequestHeader(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, CLIENT_USER_ID);

        boolean shouldFilter = clientUserToUserMappingFilter.shouldFilter();

        assertTrue(shouldFilter);
    }

    @Test
    void shouldMapClientIdAndClientUserIdToUserId() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ClientUserToken token = getToken();

        when(usersService.getUser(any(), any(), any())).thenReturn(new UserDTO(CLIENT_ID, CLIENT_USER_ID, USER_ID, false, null, null, null, false, LocalDateTime.now(), null));
        when(clientTokenRequesterService.getClientUserToken(CLIENT_ID, USER_ID)).thenReturn(token);
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, CLIENT_USER_ID.toString());
        ctx.addZuulRequestHeader(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, UUID.randomUUID().toString());

        clientUserToUserMappingFilter.run();

        String userIdInHeader = ctx.getZuulRequestHeaders().get(ApplicationConfiguration.USER_ID_HEADER_NAME);
        assertEquals(USER_ID.toString(), userIdInHeader);

        String clientTokenInHeader = ctx.getZuulRequestHeaders().get(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME);
        assertEquals(token.getSerialized(), clientTokenInHeader);
        assertEquals(token, ctx.get(ApplicationConfiguration.CLIENT_TOKEN_HEADER_NAME));
    }

    @Test
    void shouldRejectRequestIfClientUserIdIsIncorrectlyFormatted() {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, String.format("{:client_user_id=>\"%s\"", CLIENT_USER_ID));
        ctx.addZuulRequestHeader(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, UUID.randomUUID().toString());

        clientUserToUserMappingFilter.run();

        assertFalse(ctx.sendZuulResponse());
        assertThat(ctx.getResponseStatusCode()).isEqualTo(400);
        assertThat(ctx.getResponseBody()).isEqualTo("{\"code\":\"CP013\",\"message\":\"Expected a UUID for the client-user-id header\"}");

        verify(usersService, never()).getUser(any(), any(), any());
    }

    @Test
    void shouldRejectRequestIfClientUserIsBlocked() {
        ClientUsersClient clientUsersClient = mock(ClientUsersClient.class);
        ClientUserService usersService = new ClientUserService(clientUsersClient);
        ClientUserToUserMappingFilter clientUserToUserMappingFilter = new ClientUserToUserMappingFilter(empty(), usersService, new ZuulHeadersHelper(), clientTokenRequesterService);

        UUID clientUserId = UUID.randomUUID();
        UUID requestTraceId = UUID.randomUUID();

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, clientUserId.toString());

        ctx.addZuulRequestHeader(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, requestTraceId.toString());

        UserDTO user = new UserDTO(CLIENT_ID, CLIENT_USER_ID, USER_ID, true, "jupiter", "test", LocalDateTime.now(), false, LocalDateTime.now(), null);
        when(clientUsersClient.getClientUser(CLIENT_ID, clientUserId, requestTraceId)).thenReturn(Optional.of(user));

        clientUserToUserMappingFilter.run();

        assertFalse(ctx.sendZuulResponse());
        assertThat(ctx.getResponseStatusCode()).isEqualTo(423);
        assertThat(ctx.getResponseBody()).isEqualTo("{\"code\":\"CP014\",\"message\":\"Client user is blocked\"}");
    }

    @Test
    void shouldNotAddUserIdIfClientIdIsMissing() {
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, CLIENT_USER_ID);

        boolean shouldFilter = clientUserToUserMappingFilter.shouldFilter();

        assertFalse(shouldFilter);
    }

    @Test
    void shouldNotAddUserIdIfProfileIdIsMissing() {
        RequestContext.getCurrentContext().addZuulRequestHeader(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());

        boolean shouldFilter = clientUserToUserMappingFilter.shouldFilter();

        assertFalse(shouldFilter);
    }

    @Test
    void shouldThrowExceptionIfProfileIdIsUnknown() {
        doThrow(new UnknownClientUserException("")).when(usersService).getUser(any(), any(), any());

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set(ApplicationConfiguration.CLIENT_ID_HEADER_NAME, CLIENT_ID.toString());
        request.addHeader(ApplicationConfiguration.CLIENT_USER_ID_HEADER_NAME, UNKNOWN_PROFILE_ID);
        ctx.addZuulRequestHeader(ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME, UUID.randomUUID().toString());

        assertThrows(UnknownClientUserException.class, () -> clientUserToUserMappingFilter.run());
    }

    private ClientUserToken getToken() {
        UUID clientGroupId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims();
        claims.setClaim("sub", "client:" + CLIENT_ID);
        claims.setClaim("client-id", CLIENT_ID.toString());
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
        claims.setClaim("user-id", USER_ID.toString());
        claims.setClaim("client-user-id", CLIENT_USER_ID.toString());
        String serialized = Base64.encodeBase64String(String.format("fake-client-token-for-%s", CLIENT_ID).getBytes());
        return new ClientUserToken(serialized, claims);
    }
}
