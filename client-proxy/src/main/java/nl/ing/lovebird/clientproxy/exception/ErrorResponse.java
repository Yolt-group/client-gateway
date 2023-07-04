package nl.ing.lovebird.clientproxy.exception;

import lombok.Getter;
import nl.ing.lovebird.errorhandling.ErrorDTO;


@Getter
public class ErrorResponse {

    private ErrorDTO errorDTO;
    private int httpStatus;

    public ErrorResponse(ErrorDTO errorDTO, int httpStatus) {
        this.errorDTO = errorDTO;
        this.httpStatus = httpStatus;
    }
}
