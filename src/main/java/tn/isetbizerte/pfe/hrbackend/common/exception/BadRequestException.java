package tn.isetbizerte.pfe.hrbackend.common.exception;

/**
 * Exception thrown when a bad request is made
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

