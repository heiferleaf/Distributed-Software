package com.whu.serviceauth.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 400);
        resp.put("message", ex.getMessage());
        resp.put("data", null);
        return ResponseEntity.badRequest().body(resp);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 401);
        resp.put("message", ex.getMessage());
        resp.put("data", null);
        return ResponseEntity.badRequest().body(resp);
    }
}
