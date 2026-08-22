package learning.store.http;

import learning.store.storage.InfraiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public final class StorageErrorAdvice {
    @ExceptionHandler(InfraiException.class)
    public ResponseEntity<Map<String, String>> handleStorageRejection(InfraiException error) {
        int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(HttpStatusCode.valueOf(status))
                .body(Map.of("code", error.code(), "message", error.getMessage()));
    }
}
