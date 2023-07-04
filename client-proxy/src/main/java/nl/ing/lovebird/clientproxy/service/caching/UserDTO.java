package nl.ing.lovebird.clientproxy.service.caching;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserDTO {

    private final UUID clientId;
    private UUID clientUserId;
    private final UUID userId;
    private boolean blocked;
    private String blockedBy;
    private String blockedReason;
    private LocalDateTime blockedAt;
    private boolean oneOffAis;
    private LocalDateTime created;
    private LocalDateTime deleted;
}
