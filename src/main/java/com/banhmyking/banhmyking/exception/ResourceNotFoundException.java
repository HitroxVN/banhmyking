package com.banhmyking.banhmyking.exception;

/**
 * Không tìm thấy resource — trả 404.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
