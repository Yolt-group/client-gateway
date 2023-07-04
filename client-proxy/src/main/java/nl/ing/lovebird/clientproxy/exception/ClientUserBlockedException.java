package nl.ing.lovebird.clientproxy.exception;

import java.util.UUID;

public class ClientUserBlockedException extends RuntimeException {
    public ClientUserBlockedException(final UUID clientId, final UUID clientUserId, final UUID userId) {
        super(String.format("Client user is blocked: clientId=%s, clientUserId=%s, userId=%s", clientId, clientUserId, userId));
    }
}
