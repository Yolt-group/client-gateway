package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration;
import nl.ing.lovebird.clientproxy.configuration.ZuulProperties;
import nl.ing.lovebird.clientproxy.filter.filters.FilterType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SetDestinationTypeFilterTest {

    SetDestinationTypeFilter setDestinationTypeFilter;

    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setup() {
        Map<String, ZuulProperties.ZuulRoute> routes = new HashMap<>();
        routes.put("tokens", tokensRoute());
        routes.put("transactions", transactionsRoute());
        routes.put("accounts", accountsRoute());
        routes.put("site-management", siteManagementRoute());
        routes.put("/v1/users", clientUsersRoute());

        ZuulProperties zuulProperties = new ZuulProperties();
        zuulProperties.setRoutes(routes);

        setDestinationTypeFilter = new SetDestinationTypeFilter(zuulProperties);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);
    }

    @AfterEach
    void after() {
        RequestContext.getCurrentContext().clear();
    }

    private static ZuulProperties.ZuulRoute tokensRoute() {
        Set<ZuulProperties.ZuulSubPath> tokensSubPaths = new HashSet<>();
        tokensSubPaths.add(open("/tokens"));

        ZuulProperties.ZuulRoute tokensRoute = new ZuulProperties.ZuulRoute();
        tokensRoute.setSubPaths(tokensSubPaths);
        return tokensRoute;
    }

    private static ZuulProperties.ZuulRoute transactionsRoute() {
        Set<ZuulProperties.ZuulSubPath> transactionsSubPaths = new HashSet<>();
        transactionsSubPaths.add(authenticated("/transactions-by-account/me", HttpMethod.GET, HttpMethod.POST));
        transactionsSubPaths.add(authenticated("/transactions-by-date/me", HttpMethod.GET, HttpMethod.POST));

        ZuulProperties.ZuulRoute transactionsRoute = new ZuulProperties.ZuulRoute();
        transactionsRoute.setSubPaths(transactionsSubPaths);
        return transactionsRoute;
    }

    private static ZuulProperties.ZuulRoute accountsRoute() {
        Set<ZuulProperties.ZuulSubPath> accountsSubPaths = new HashSet<>();
        accountsSubPaths.add(authenticated("/**", HttpMethod.GET, HttpMethod.POST));

        ZuulProperties.ZuulRoute accountsRoute = new ZuulProperties.ZuulRoute();
        accountsRoute.setSubPaths(accountsSubPaths);
        return accountsRoute;
    }

    private static ZuulProperties.ZuulRoute siteManagementRoute() {
        Set<ZuulProperties.ZuulSubPath> siteManagementSubPaths = new HashSet<>();
        siteManagementSubPaths.add(authenticated("/sites/*/enable", HttpMethod.POST));
        siteManagementSubPaths.add(authenticated("/sites/*/disable", HttpMethod.POST));
        siteManagementSubPaths.add(authenticated("/sites/**", HttpMethod.GET));

        ZuulProperties.ZuulRoute siteManagementRoute = new ZuulProperties.ZuulRoute();
        siteManagementRoute.setSubPaths(siteManagementSubPaths);
        siteManagementRoute.setPath("/site-management/**");
        return siteManagementRoute;
    }

    private static ZuulProperties.ZuulRoute clientUsersRoute() {
        Set<ZuulProperties.ZuulSubPath> clientUsersSubPaths = new HashSet<>();
        clientUsersSubPaths.add(authenticated("", HttpMethod.POST));
        ZuulProperties.ZuulSubPath putClientUsersKyc = authenticated("/*", HttpMethod.DELETE, HttpMethod.PUT);
        putClientUsersKyc.setOverrideHttpMethods(Collections.singleton(HttpMethod.PUT.name()));
        putClientUsersKyc.setOverrideUrl("http://client-users-kyc/client-users-kyc/v1/users");
        clientUsersSubPaths.add(putClientUsersKyc);

        ZuulProperties.ZuulRoute clientUsersRoute = new ZuulProperties.ZuulRoute();
        clientUsersRoute.setSubPaths(clientUsersSubPaths);
        clientUsersRoute.setPath("/v1/users/**");
        return clientUsersRoute;
    }

    private static ZuulProperties.ZuulSubPath open(String subPath) {
        ZuulProperties.ZuulSubPath openSubPath = new ZuulProperties.ZuulSubPath();
        openSubPath.setSubPath(subPath);
        openSubPath.setOpenHttpMethods(Collections.singleton(HttpMethod.POST.name()));
        return openSubPath;
    }

    private static ZuulProperties.ZuulSubPath authenticated(String subPath, HttpMethod... httpMethods) {
        ZuulProperties.ZuulSubPath authenticatedSubPath = new ZuulProperties.ZuulSubPath();
        authenticatedSubPath.setSubPath(subPath);
        List<String> methods = Arrays.stream(httpMethods).map(Enum::name).collect(Collectors.toList());
        authenticatedSubPath.setAuthenticatedHttpMethods(new HashSet<>(methods));
        return authenticatedSubPath;
    }

    @Test
    void shouldHaveFilterTypePre() {
        assertEquals(FilterType.PRE.getType(), setDestinationTypeFilter.filterType());
    }

    @Test
    void shouldHaveFilterOrder() {
        assertEquals(ApplicationConfiguration.FILTER_ORDER_SET_DESTINATION_TYPE_FILTER, setDestinationTypeFilter.filterOrder());
    }

    @Test
    void shouldBlockUnknownRoute() throws Exception {
        runTest("GET", "unknown", null, null);
        assertRejectedRequest("{\"code\":\"CP007\",\"message\":\"Destination not available\"}", HttpStatus.NOT_FOUND);

    }

    @Test
    void shouldBlockUnknownSubPath() throws Exception {
        runTest("GET", "tokens", "/unknown", null);
        assertRejectedRequest("{\"code\":\"CP007\",\"message\":\"Destination not available\"}", HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSetOpenDestinationByExactURL() throws Exception {
        runTest("POST", "tokens", "/tokens", false);
        assertForwardedRequest();
    }

    @Test
    void shouldBlockDestinationByNonMatchingExactURL() throws Exception {
        runTest("POST", "tokens", "/tokens/subpath", null);
        assertRejectedRequest("{\"code\":\"CP007\",\"message\":\"Destination not available\"}", HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSetAuthenticatedDestinationByExactURL() throws Exception {
        runTest("GET", "transactions", "/transactions-by-account/me", true);
        assertForwardedRequest();
    }

    @Test
    void shouldBlockSiteManagementSubPathPost() throws Exception {
        runTest("POST", "site-management", "/sites/abc123", null);
        assertRejectedRequest("{\"code\":\"CP008\",\"message\":\"Method not available for this destination\"}", HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldRequireAuthenticationSiteEnable() throws Exception {
        runTest("POST", "site-management", "/sites/abc123/enable", true);
        assertForwardedRequest();
    }

    @Test
    void shouldRequireAuthenticationSiteDisable() throws Exception {
        runTest("POST", "site-management", "/sites/abc123/disable", true);
        assertForwardedRequest();
    }

    @Test
    void shouldBlockSiteDisableGetMethod() throws Exception {
        runTest("GET", "site-management", "/sites/abc123/enable", null);
        assertRejectedRequest("{\"code\":\"CP008\",\"message\":\"Method not available for this destination\"}", HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldBlockOpenDestinationByExactURLButNotMatchingMethod() throws Exception {
        runTest("GET", "tokens", "/tokens", null);
        assertRejectedRequest("{\"code\":\"CP008\",\"message\":\"Method not available for this destination\"}", HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldSetAuthenticatedDestinationByNotExactURL() throws Exception {
        runTest("GET", "accounts", "/anything", true);
        assertForwardedRequest();
    }

    @Test
    void shouldNotOverrideDestinationByURL() throws Exception {
        runTest("DELETE", "/v1/users", "/user-id", true);
        assertForwardedRequest();
        RequestContext ctx = RequestContext.getCurrentContext();
        assertThat(ctx.getRouteHost(), is(URI.create("http://localhost").toURL()));
    }

    @Test
    void shouldOverrideDestinationByURL() throws Exception {
        runTest("PUT", "/v1/users", "/user-id", true);
        assertForwardedRequest();
        RequestContext ctx = RequestContext.getCurrentContext();
        assertThat(ctx.getRouteHost(), is(URI.create("http://client-users-kyc/client-users-kyc/v1/users").toURL()));
    }

    private void runTest(String method, String proxy, String requestURI, Boolean authenticationRequired) throws Exception {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.set("proxy", proxy);
        ctx.set("requestURI", requestURI);
        ctx.setRouteHost(URI.create("http://localhost").toURL());
        request.setMethod(method);

        setDestinationTypeFilter.run();

        assertEquals(authenticationRequired, ctx.get(ApplicationConfiguration.AUTHENTICATION_REQUIRED));
    }

    private void assertForwardedRequest() {
        RequestContext ctx = RequestContext.getCurrentContext();
        assertThat(ctx.sendZuulResponse(), is(true));
    }

    private void assertRejectedRequest(String body, HttpStatus expectedStatus) {
        RequestContext ctx = RequestContext.getCurrentContext();

        assertThat(ctx.sendZuulResponse(), is(false));
        assertThat(ctx.getResponseStatusCode(), is(expectedStatus.value()));
        assertThat(ctx.getResponseBody(), is(body));
    }
}
