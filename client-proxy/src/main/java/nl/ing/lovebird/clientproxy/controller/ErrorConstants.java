package nl.ing.lovebird.clientproxy.controller;

import nl.ing.lovebird.errorhandling.ErrorInfo;

public enum ErrorConstants implements ErrorInfo {

    INVALID_JWT("001", "Invalid JWT"),
    NO_ACCESS_TOKEN("002", "No Access Token on request"),
    UNKNOWN_CLIENT_USER_PROFILE("003", "Unknown client-user"),
    ILLEGAL_ARGUMENT("005", "Invalid parameter in call"),
    DESTINATION_NOT_AVAILABLE("007", "Destination not available"),
    DESTINATION_NOT_AVAILABLE_FOR_METHOD("008", "Method not available for this destination"),
    TIMEOUT("009", "Timeout occurred to upstream service"),
    UNKNOWN_HOST("010", "Unknown host"),
    NOT_FOUND("011", "Not found"),
    EXPIRED_TOKEN("012", "Access token is expired"),
    INCORRECT_CLIENT_USER_ID_FORMAT("013", "Expected a UUID for the client-user-id header"),
    BLOCKED_CLIENT_USER("014", "Client user is blocked"),
    UNKNOWN_GENERIC("999", "Unknown error");

    private final String code;
    private final String message;

    ErrorConstants(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
