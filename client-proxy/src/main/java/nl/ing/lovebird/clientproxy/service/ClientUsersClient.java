package nl.ing.lovebird.clientproxy.service;

import nl.ing.lovebird.clientproxy.service.caching.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.CLIENT_ID_HEADER_NAME;
import static nl.ing.lovebird.clientproxy.configuration.ApplicationConfiguration.REQUEST_TRACE_ID_HEADER_NAME;

@Service
public class ClientUsersClient {

    private final RestTemplate restTemplate;

    public ClientUsersClient(RestTemplateBuilder restTemplateBuilder,
                             @Value("${service.users.timeout-in-seconds:5}") Integer clientUsersServiceTimeout,
                             @Value("${service.users.url}") String usersUrl) {
        restTemplate = restTemplateBuilder.rootUri(usersUrl)
                .setConnectTimeout(Duration.ofSeconds(clientUsersServiceTimeout))
                .setReadTimeout(Duration.ofSeconds(clientUsersServiceTimeout))
                .build();

    }

    public Optional<UserDTO> getClientUser(final UUID clientId, final UUID clientUserId, final UUID requestTraceId) {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(CLIENT_ID_HEADER_NAME, clientId.toString());
            httpHeaders.add(REQUEST_TRACE_ID_HEADER_NAME, requestTraceId.toString());
            ResponseEntity<UserDTO> exchange = restTemplate.exchange(
                    "/internal/client-users/{clientId}/{clientUserId}",
                    HttpMethod.GET,
                    new HttpEntity<>(null, httpHeaders),
                    UserDTO.class,
                    clientId,
                    clientUserId
            );
            return Optional.ofNullable(exchange.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
