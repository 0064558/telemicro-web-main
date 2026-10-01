package br.com.telemicro.api.shared;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String field;

    public ApiException(HttpStatus status, String message) { this(status, message, null); }
    public ApiException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }
    public HttpStatus status() { return status; }
    public String field() { return field; }
}
