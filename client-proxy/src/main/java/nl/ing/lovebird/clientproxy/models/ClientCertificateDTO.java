package nl.ing.lovebird.clientproxy.models;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
public class ClientCertificateDTO {
    UUID clientId;
    String certificateFingerprint;
    String certificate;
    LocalDateTime seen;
}
