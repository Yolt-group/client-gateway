package nl.ing.lovebird.clientproxy.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigurationTest {

    @Test
    void shouldRunSetDestinationTypeFilterAfterPreDecorationFilter() {
        assertTrue(ApplicationConfiguration.FILTER_ORDER_SET_DESTINATION_TYPE_FILTER > 5, "SetDestinationTypeFilter must run after PreDecorationFilter");
    }

    @Test
    void shouldRunAccessTokenValidationFilterAfterSetDestinationTypeFilter() {
        assertTrue(ApplicationConfiguration.FILTER_ORDER_ACCESSTOKEN_FILTER > ApplicationConfiguration.FILTER_ORDER_SET_DESTINATION_TYPE_FILTER, "AccessTokenValidationFilter must run after SetDestinationFilter");
    }

    @Test
    void shouldRunMTLSCertificateLoggingFilterAfterAccessTokenValidationFilter() {
        assertTrue(ApplicationConfiguration.FILTER_ORDER_MTLS_FILTER > ApplicationConfiguration.FILTER_ORDER_ACCESSTOKEN_FILTER, "AccessTokenValidationFilter must run after SetDestinationFilter");
    }

}
