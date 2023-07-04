package nl.ing.lovebird.clientproxy.service.kafka;

import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;

@Service
@Slf4j
public class ClientCertificateEventProducer {

    private final KafkaTemplate<String, ClientCertificateDTO> kafkaTemplate;
    private final String topic;
    private final Duration lastSendTimeout;
    private final Map<CertificateIndex, LocalDateTime> lastSendMap;

    public ClientCertificateEventProducer(KafkaTemplate<String, ClientCertificateDTO> kafkaTemplate,
                                          @Value("${yolt.kafka.topics.client-mtls-certificate.topic-name}") String topic,
                                          @Value("${yolt.client-certificates.last-send-timeout}") Duration lastSendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.lastSendTimeout = lastSendTimeout;
        this.lastSendMap = new HashMap<>();
    }

    public void sendMessage(ClientToken clientToken, ClientCertificateDTO payload) {
        var certificateIndex = new CertificateIndex(payload.getClientId(), payload.getCertificateFingerprint());
        var lastSeen = lastSendMap.get(certificateIndex);
        if (lastSeen == null || payload.getSeen().isAfter(lastSeen.plus(lastSendTimeout))) {
            Message<ClientCertificateDTO> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(CLIENT_TOKEN_HEADER_NAME, clientToken.getSerialized())
                    .setHeader(KafkaHeaders.MESSAGE_KEY, payload.getClientId().toString())
                    .build();
            kafkaTemplate.send(message);
            lastSendMap.put(certificateIndex, payload.getSeen());
            log.debug("sending client certificate {} over kafka", payload.getCertificateFingerprint());
        }
    }

    @lombok.Value
    private static class CertificateIndex {
        UUID clientId;
        String certificateFingerprint;
    }
}
