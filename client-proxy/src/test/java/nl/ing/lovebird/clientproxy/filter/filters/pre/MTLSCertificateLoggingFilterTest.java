package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clientproxy.service.kafka.ClientCertificateEventProducer;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.TestConfiguration.FIXED_CLOCK;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MTLSCertificateLoggingFilterTest {
    private MTLSCertificateLoggingFilter mtlsCertificateLoggingFilter;

    @Mock
    private ClientCertificateEventProducer clientCertificateEventProducer;
    private Clock clock = FIXED_CLOCK;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private ClientToken mockedClientToken;

    private UUID clientId;
    private String certificateFingerprint;
    private String certificate;
    private LocalDateTime seen;

    @BeforeEach
    void setUp() {
        mtlsCertificateLoggingFilter = new MTLSCertificateLoggingFilter(clientCertificateEventProducer, clock);

        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.clear();
        ctx.setRequest(request);
        ctx.setResponse(response);

        clientId = UUID.randomUUID();
        certificateFingerprint = "abcdef";
        certificate = """
                -----BEGIN CERTIFICATE-----
                MIIEsTCCA5mgAwIBAgIUNwhq5tOUHxIfT5FtQLdepxX4X7kwDQYJKoZIhvcNAQEL
                BQAwKzEpMCcGA1UEAxMgY2xpZW50LXByb3h5LnlmYi1zYW5kYm94LnlvbHQuaW8w
                HhcNMjEwODA1MTEyNzExWhcNMjIwODA1MTEyNzQxWjCBpTELMAkGA1UEBhMCTkwx
                EzARBgNVBAgTCkdlbGRlcmxhbmQxFTATBgNVBAcTDEdlbGRlcm1hbHNlbjETMBEG
                A1UEChMKSmFhcFN0ZWxtYTETMBEGA1UECxMKSmFhcFN0ZWxtYTEcMBoGA1UEAwwT
                amFhcC5zdGVsbWFAeW9sdC5ldTEiMCAGCSqGSIb3DQEJAQwTamFhcC5zdGVsbWFA
                eW9sdC5ldTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK688ozBZfOH
                qXzxqXRZBXWSpXBOu34TAqPYkanK3BPJOmQ/l2gUONOkPLDQ75FLA/Cwwpi0jrk2
                i+obiukBSjPbz8dtjEyp+ofBLwjUP0fB23o+hVGs4BrgAxxB49E5PyJDXy9Gz873
                g+2BtCdH5shKSlsnQbLZz3UqwOZNYB2BdJ9bi6wefalgLrqZZ1Fti5h4abKjZZ20
                ESkzb9rPw7uogLyOGjKEOHGX7odNOh5lLN5YHkV1f91mM/lYZ2TyJ3ao5BLbL6AA
                qEcH9HBBLj/vsPAX2XQw2o1HqAe1n+0/JBAdFOzm59VRPz5aO2W3RmKXzyMee8n2
                IVVuGm2Bcas1axwNiUO9Kq+DArSpbsp9BF9jh76EH1/09/6bA04rtkICIkIl5R/F
                blMrbXkvfLKngcB3eymPrtGo1W0t/qcaGalDFq6uzOX9gWWOZjQRXkzGv/qrzv3A
                eH/tHFVcaa2pf+y9K6c0A9rerPVZmplht4SR1ApI3+Bw47FElCoxgcTzhrBato2g
                ConsG+Zc++BE9pBQCLTQ/whbfE1nAeOl2l26gwH9kg5LD7Nn2S5BhxX1iHbrBeY2
                YlnsFdl7qxm+Mi93e+LxbbK+4EOoECH9cgqWhmg2UI1e/h/xRzV+Fq4CDmdjYWuy
                AaBUm1joqN/8NImeX4GkFnzWabub3FQ1AgMBAAGjUjBQMA4GA1UdDwEB/wQEAwID
                qDAdBgNVHQ4EFgQUk1P3xUKymzN1F2TAt1iCoT1ElvMwHwYDVR0jBBgwFoAUZzIb
                nKNta2buZAv1VB00pJiWa1EwDQYJKoZIhvcNAQELBQADggEBAHtuxV9hDMPq6EjI
                tGCGG05jmOS5KFdBXHh5Lu8AYS7KW8yyCWA5CkV0PoafSu7vjZbfj4QWFiSEESuw
                SdpLZAXsvc4DqPquNAMOyEallp5QcbWANjgBr2Pxc5UDciSlUOpY6vuss0TcUre8
                e8UTGgqUC8PdGbN8qtDi0ZPS3Ya8pEvsEFT+6HV19gAcDg/gkaCdfMW1Ar2wCfca
                zBujGXnMefdixp40mSxXI2lgT/GXDR11P5VMfT4YYvWu+ou8vbBFVEbDTt/PUAYI
                spnfsG176PmvEkGJa2hiTgCZUHwTQldimuM5P0WI5ltr7GbMLFNDuJ/w0EXVNeNq
                bEJbp+w=
                -----END CERTIFICATE-----
                """;
        seen = LocalDateTime.now(clock);
    }

    @Test
    void filterType() {
        assertThat(mtlsCertificateLoggingFilter.filterType()).isEqualTo("pre");
    }

    @Test
    void shouldFilter_true_if_client_token_present() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        assertThat(mtlsCertificateLoggingFilter.shouldFilter()).isTrue();
    }

    @Test
    void shouldFilter_false_if_client_token_not_present() {
        assertThat(mtlsCertificateLoggingFilter.shouldFilter()).isFalse();
    }

    @Test
    void shouldFilter_false_if_SendZuulResponse_is_false() {
        RequestContext.getCurrentContext().setSendZuulResponse(false);
        assertThat(mtlsCertificateLoggingFilter.shouldFilter()).isFalse();
    }

    @Test
    void filterOrder() {
        assertThat(mtlsCertificateLoggingFilter.filterOrder()).isEqualTo(12);
    }

    @Test
    void run_with_utf_8_charset_request_should_succeed() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        when(mockedClientToken.getClientIdClaim()).thenReturn(clientId);
        when(request.getHeader("X-SSL-Client-FINGERPRINT")).thenReturn(certificateFingerprint);
        when(request.getHeader("X-SSL-Client-CERTIFICATE")).thenReturn(URLEncoder.encode(certificate, StandardCharsets.UTF_8));
        when(request.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());

        mtlsCertificateLoggingFilter.run();

        verify(clientCertificateEventProducer).sendMessage(mockedClientToken, new ClientCertificateDTO(
                clientId,
                certificateFingerprint,
                certificate,
                seen
        ));
    }

    @Test
    void run_with_utf_8_charset_servlet_should_succeed() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        when(mockedClientToken.getClientIdClaim()).thenReturn(clientId);
        when(request.getHeader("X-SSL-Client-FINGERPRINT")).thenReturn(certificateFingerprint);
        when(request.getHeader("X-SSL-Client-CERTIFICATE")).thenReturn(URLEncoder.encode(certificate, StandardCharsets.UTF_8));
        when(request.getCharacterEncoding()).thenReturn(null);
        when(request.getServletContext().getRequestCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());

        mtlsCertificateLoggingFilter.run();

        verify(clientCertificateEventProducer).sendMessage(mockedClientToken, new ClientCertificateDTO(
                clientId,
                certificateFingerprint,
                certificate,
                seen
        ));
    }

    @Test
    void run_with_undefined_character_encoding_should_succeed_when_standard_encoding_is_used() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        when(mockedClientToken.getClientIdClaim()).thenReturn(clientId);
        when(request.getHeader("X-SSL-Client-FINGERPRINT")).thenReturn(certificateFingerprint);
        when(request.getHeader("X-SSL-Client-CERTIFICATE")).thenReturn(URLEncoder.encode(certificate, StandardCharsets.UTF_8));
        when(request.getCharacterEncoding()).thenReturn(null);
        when(request.getServletContext().getRequestCharacterEncoding()).thenReturn(null);

        mtlsCertificateLoggingFilter.run();

        verify(clientCertificateEventProducer).sendMessage(mockedClientToken, new ClientCertificateDTO(
                clientId,
                certificateFingerprint,
                certificate,
                seen
        ));
    }

    @Test
    void run_with_no_fingerprint_should_not_send_client_certificate() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        when(request.getHeader("X-SSL-Client-FINGERPRINT")).thenReturn(null);

        mtlsCertificateLoggingFilter.run();

        verifyNoInteractions(clientCertificateEventProducer);
    }

    @Test
    void run_with_no_certificate_should_not_send_client_certificate() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, mockedClientToken);
        when(request.getHeader("X-SSL-Client-FINGERPRINT")).thenReturn(certificateFingerprint);
        when(request.getHeader("X-SSL-Client-CERTIFICATE")).thenReturn(null);

        mtlsCertificateLoggingFilter.run();

        verifyNoInteractions(clientCertificateEventProducer);
    }

    @Test
    void run_with_client_token_should_not_send_client_certificate() {
        RequestContext.getCurrentContext().set(CLIENT_TOKEN_HEADER_NAME, null);

        mtlsCertificateLoggingFilter.run();

        verifyNoInteractions(clientCertificateEventProducer);
    }
}
