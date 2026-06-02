package com.datamine.analysis.web.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleNotPermission(NotPermissionException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<Map<String, Object>> handleSaToken(SaTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (isClientStreamingDisconnect(request, ex)) {
            log.debug("Streaming request disconnected by client. uri={}", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unhandled web exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "系统处理请求时发生异常");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "success", false,
                "message", message == null || message.isBlank() ? status.getReasonPhrase() : message
        ));
    }

    private boolean isClientStreamingDisconnect(HttpServletRequest request, Throwable error) {
        return request != null
                && isStreamingEndpoint(request.getRequestURI())
                && isClientDisconnect(error);
    }

    private boolean isStreamingEndpoint(String requestUri) {
        return requestUri != null
                && (requestUri.equals("/api/chat/send") || requestUri.startsWith("/api/tables/scan/"));
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.endsWith("AsyncRequestNotUsableException") || className.contains("ClientAbortException")) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || normalized.contains("an established connection was aborted")
                        || normalized.contains("forcibly closed by the remote host")
                        || message.contains("你的主机中的软件中止了一个已建立的连接")
                        || message.contains("远程主机强迫关闭了一个现有的连接")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
