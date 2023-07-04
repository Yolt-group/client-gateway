package nl.ing.lovebird.clientproxy.service.kafka;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;
import nl.ing.lovebird.clientproxy.service.ApiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class IncomingRequestEventProducer {

    private final KafkaTemplate<String, ClientCertificateDTO> kafkaTemplate;
    private final String topic;

    public IncomingRequestEventProducer(KafkaTemplate<String, ClientCertificateDTO> kafkaTemplate,
                                        @Value("${yolt.kafka.topics.incoming-request.topic-name}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendMessage(@NonNull final ApiRequest payload) {
        Message<ApiRequest> message = MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.MESSAGE_KEY, payload.getClientId() == null ? "" : payload.getClientId().toString())
                .build();
        kafkaTemplate.send(message)
                .addCallback(sendResult -> {
                    if (sendResult != null) {
                        log.debug("Successfully published to Kafka: {}", sendResult.getRecordMetadata().topic());
                    }
                }, throwable -> log.error("Failed to publish request to Kafka", throwable));
    }
}
