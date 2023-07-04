package nl.ing.lovebird.clientproxy.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.errorhandling.ErrorInfo;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class ExceptionMapping {
    private final ErrorInfo errorInfo;
    private final HttpStatus httpStatus;
}
