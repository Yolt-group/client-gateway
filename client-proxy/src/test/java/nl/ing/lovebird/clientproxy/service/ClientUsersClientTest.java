package nl.ing.lovebird.clientproxy.service;

import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(ClientUsersClient.class)
class ClientUsersClientTest {

    final UUID clientUserId = UUID.randomUUID();
    final UUID clientId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @Autowired
    private ClientUsersClient clientUsersClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    void shouldGetClientUserHappyFlow() {
        UUID requestTraceId = UUID.randomUUID();
        mockServer.expect(requestTo("/internal/client-users/" + clientId + "/" + clientUserId))
                .andExpect(header("client-id", clientId.toString()))
                .andExpect(header("request_trace_id", requestTraceId.toString()))
                .andRespond(withSuccess(
                                "{" +
                                "\"clientId\":\"" + clientId + "\", " +
                                "\"clientUserId\":\"" + clientUserId + "\", " +
                                "\"userId\":\"" + userId + "\", " +
                                "\"blocked\":\"" + true + "\"" +
                                "}",
                        APPLICATION_JSON));

        Optional<UserDTO> clientUser = clientUsersClient.getClientUser(clientId, clientUserId, requestTraceId);
        assertThat(clientUser).isPresent();
        clientUser.ifPresent(clientUserDto -> {
            assertThat(clientUserDto.getUserId()).isEqualTo(userId);
            assertThat(clientUserDto.getClientId()).isEqualTo(clientId);
            assertThat(clientUserDto.getClientUserId()).isEqualTo(clientUserId);
            assertThat(clientUserDto.isBlocked()).isTrue();
        });
    }


    @Test
    void shouldReturnEmptyOptionalForUserNotFound() {
        UUID requestTraceId = UUID.randomUUID();
        mockServer.expect(requestTo("/internal/client-users/" + clientId + "/" + clientUserId))
                .andExpect(header("client-id", clientId.toString()))
                .andExpect(header("request_trace_id", requestTraceId.toString()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UserDTO> clientUser = clientUsersClient.getClientUser(clientId, clientUserId, requestTraceId);
        assertThat(clientUser).isEmpty();
    }

    @Test
    void shouldThrowAnExceptionForInternalServerError() {
        UUID requestTraceId = UUID.randomUUID();
        mockServer.expect(requestTo("/internal/client-users/" + clientId + "/" + clientUserId))
                .andExpect(header("client-id", clientId.toString()))
                .andExpect(header("request_trace_id", requestTraceId.toString()))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(InternalServerError.class, () -> clientUsersClient.getClientUser(clientId, clientUserId, requestTraceId));
    }
}
