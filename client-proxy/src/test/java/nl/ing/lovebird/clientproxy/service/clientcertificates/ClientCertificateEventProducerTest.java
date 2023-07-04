package nl.ing.lovebird.clientproxy.service.clientcertificates;

import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clientproxy.service.kafka.ClientCertificateEventProducer;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.TestConfiguration.FIXED_CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientCertificateEventProducerTest {
    private ClientCertificateEventProducer clientCertificateEventProducer;

    @Mock
    private KafkaTemplate<String, ClientCertificateDTO> kafkaTemplate;
    private String topic = "topic";
    private Duration lastSendTimeout = Duration.ofMinutes(5);

    private UUID clientId1;
    private UUID clientId2;
    private String certificateFingerprint;
    private String certificate;
    private LocalDateTime seen;
    @Mock
    private ClientToken clientToken1;
    private String serializedClientToken1;
    @Mock
    private ClientToken clientToken2;
    private String serializedClientToken2;

    @Captor
    private ArgumentCaptor<Message<ClientCertificateDTO>> messageArgumentCaptor;

    @BeforeEach
    void setUp() {
        clientCertificateEventProducer = new ClientCertificateEventProducer(
                kafkaTemplate,
                topic,
                lastSendTimeout
        );

        clientId1 = UUID.randomUUID();
        clientId2 = UUID.randomUUID();
        serializedClientToken1 = "CT-" + clientId1;
        serializedClientToken2 = "CT-" + clientId2;
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
        seen = LocalDateTime.now(FIXED_CLOCK);
    }

    @Test
    void sendMessage() {
        ClientCertificateDTO clientCertificateDTO1 = new ClientCertificateDTO(
                clientId1,
                certificateFingerprint,
                certificate,
                seen
        );
        ClientCertificateDTO clientCertificateDTO2 = new ClientCertificateDTO(
                clientId1,
                certificateFingerprint,
                certificate,
                seen.plusMinutes(1)
        );

        ClientCertificateDTO clientCertificateDTO3 = new ClientCertificateDTO(
                clientId2,
                certificateFingerprint,
                certificate,
                seen.plusMinutes(1)
        );

        ClientCertificateDTO clientCertificateDTO4 = new ClientCertificateDTO(
                clientId1,
                certificateFingerprint,
                certificate,
                seen.plusMinutes(6)
        );
        when(clientToken1.getSerialized()).thenReturn(serializedClientToken1);
        when(clientToken2.getSerialized()).thenReturn(serializedClientToken2);

        clientCertificateEventProducer.sendMessage(clientToken1, clientCertificateDTO1);
        clientCertificateEventProducer.sendMessage(clientToken1, clientCertificateDTO2);
        clientCertificateEventProducer.sendMessage(clientToken2, clientCertificateDTO3);
        clientCertificateEventProducer.sendMessage(clientToken1, clientCertificateDTO4);

        verify(kafkaTemplate, times(3)).send(messageArgumentCaptor.capture());

        Message<ClientCertificateDTO> message1 = messageArgumentCaptor.getAllValues().get(0);
        assertThat(message1.getPayload()).isEqualTo(clientCertificateDTO1);
        assertThat(message1.getHeaders()).contains(
                entry("kafka_topic", topic),
                entry("client-token", serializedClientToken1),
                entry("kafka_messageKey", clientId1.toString())
        );


        Message<ClientCertificateDTO> message3 = messageArgumentCaptor.getAllValues().get(1);
        assertThat(message3.getPayload()).isEqualTo(clientCertificateDTO3);
        assertThat(message3.getHeaders()).contains(
                entry("kafka_topic", topic),
                entry("client-token", serializedClientToken2),
                entry("kafka_messageKey", clientId2.toString())
        );

        Message<ClientCertificateDTO> message4 = messageArgumentCaptor.getAllValues().get(2);
        assertThat(message4.getPayload()).isEqualTo(clientCertificateDTO4);
        assertThat(message4.getHeaders()).contains(
                entry("kafka_topic", topic),
                entry("client-token", serializedClientToken1),
                entry("kafka_messageKey", clientId1.toString())
        );

        assertThat(messageArgumentCaptor.getAllValues()).hasSize(3);
        verifyNoMoreInteractions(kafkaTemplate);
    }
}
