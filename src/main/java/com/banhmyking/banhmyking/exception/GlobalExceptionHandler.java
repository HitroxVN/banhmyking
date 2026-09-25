package com.banhmyking.banhmyking.exception;

import com.banhmyking.banhmyking.dto.common.ErrorResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Nơi duy nhất dịch exception → envelope JSON. Controller không try-catch.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ErrorResponse.of(ex.getMessage(), ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(ex.getMessage(), ex.getErrorCode()));
    }

    /** @Valid fail — gom field lỗi thành map {field: message}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ErrorResponse.of("Dữ liệu không hợp lệ", ErrorCode.VALIDATION_ERROR, errors));
    }

    /** JSON parse error, cú pháp sai hoặc thiếu body. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ErrorResponse.of("Định dạng dữ liệu JSON không hợp lệ hoặc sai cú pháp (vui lòng kiểm tra dấu phẩy thừa)", ErrorCode.VALIDATION_ERROR));
    }

    /** @PreAuthorize từ chối — 403, không phải 500. Bắt cả AuthorizationDeniedException (subclass). */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ErrorResponse.of("Bạn không có quyền thực hiện thao tác này", ErrorCode.FORBIDDEN));
    }

    /** Tham số request sai kiểu (enum không tồn tại, id không phải số) — 400. */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on param '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ErrorResponse.of("Tham số '" + ex.getName() + "' không hợp lệ", ErrorCode.VALIDATION_ERROR));
    }

    /** Vi phạm ràng buộc DB (unique email, FK...) — 409, không lộ chi tiết schema. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ErrorResponse.of("Dữ liệu xung đột (có thể đã tồn tại hoặc vi phạm ràng buộc)", ErrorCode.CONFLICT));
    }

    /** Không khớp route/handler nào (đường dẫn sai hoặc đã bị gỡ) — 404, không phải 500. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Không có handler cho request: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ErrorResponse.of("Không tìm thấy đường dẫn yêu cầu", ErrorCode.NOT_FOUND));
    }

    /** Lỗi khác — log đầy đủ, trả message chung (không lộ stack trace ra ngoài). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ErrorResponse.of("Lỗi hệ thống, vui lòng thử lại sau", ErrorCode.INTERNAL_ERROR));
    }
}
