package com.rootscope.web;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail notFound(NoSuchElementException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail unreadable(HttpMessageNotReadableException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        "Malformed request body: " + rootMessage(e));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail invalid(MethodArgumentNotValidException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            f -> f.getField(),
            f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
            (a, b) -> a));
    problem.setProperty("errors", errors);
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Unhandled request failure", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
  }

  private static String rootMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String message = root.getMessage();
    return message == null ? t.toString() : message;
  }
}
