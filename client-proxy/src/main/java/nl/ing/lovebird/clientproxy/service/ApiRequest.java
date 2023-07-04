package nl.ing.lovebird.clientproxy.service;

import lombok.Builder;
import lombok.Data;
import nl.ing.lovebird.clientproxy.models.ClientCertificateDTO;

import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Builder
public class ApiRequest {

    private final UUID clientGroupId;
    private final UUID clientId;
    private final UUID clientUserId;
    private final UUID userId;
    private final String method;
    private final String endpoint;
    private final Integer httpResponseCode;
    private final LocalDateTime loggedAt;
    private final ClientCertificateDTO clientCertificateDTO;
}
