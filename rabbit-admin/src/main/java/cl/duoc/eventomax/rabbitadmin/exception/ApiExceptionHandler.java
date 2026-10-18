package cl.duoc.eventomax.rabbitadmin.exception;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AdminException.class)
    public ResponseEntity<ProblemDetail> admin(AdminException ex) {
        return ResponseEntity.status(ex.status()).body(ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid request: check resource names, required fields, allowed properties and value ranges"));
    }
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class, NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> mvcError(ErrorResponse ex) {
        HttpStatusCode status = ex.getStatusCode();
        String detail = switch (status.value()) {
            case 404 -> "Requested administrative endpoint does not exist";
            case 405 -> "HTTP method is not supported for this endpoint";
            case 406 -> "Requested media type is not acceptable";
            case 415 -> "Content-Type is not supported; use application/json";
            default -> "Request cannot be processed";
        };
        return ResponseEntity.status(status).headers(ex.getHeaders())
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}

