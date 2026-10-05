package com.kerosene.auth.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;


/** HTTP error payload with timestamp, response status, safe message, and request path. */
public class ResponseError {

    /** Time when the error response was assembled. */
    private LocalDateTime timestamp;
    /** HTTP status associated with the failure. */
    private HttpStatus status;
    /** Short status label suitable for client-side categorization. */
    private String error;
    /** Safe explanatory text returned to the caller; must not expose secrets or stack traces. */
    private String message;
    /** Request path that produced the error. */
    private String path;

    /** Creates an error response from the request's resolved failure details.
     * @param timestamp response creation time
     * @param status HTTP response status
     * @param error short error category
     * @param message safe client-facing explanation
     * @param path request path
     */
    public ResponseError(LocalDateTime timestamp, HttpStatus status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    /**
     * Returns time when the error response was assembled.
     *
     * @return timestamp value
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets time when the error response was assembled.
     *
     * @param timestamp Time when the error response was assembled.
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns HTTP status associated with the failure.
     *
     * @return status value
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Sets HTTP status associated with the failure.
     *
     * @param status HTTP status associated with the failure.
     */
    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    /**
     * Returns short status label suitable for client-side categorization.
     *
     * @return error value
     */
    public String getError() {
        return error;
    }

    /**
     * Sets short status label suitable for client-side categorization.
     *
     * @param error Short status label suitable for client-side categorization.
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns safe explanatory text returned to the caller; must not expose secrets or stack traces.
     *
     * @return message value
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets safe explanatory text returned to the caller; must not expose secrets or stack traces.
     *
     * @param message Safe explanatory text returned to the caller; must not expose secrets or stack traces.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns request path that produced the error.
     *
     * @return path value
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets request path that produced the error.
     *
     * @param path Request path that produced the error.
     */
    public void setPath(String path) {
        this.path = path;
    }
}
