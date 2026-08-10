package com.project.pantau.common.exception;

import com.project.pantau.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Dummy method used purely as a source of a real {@link MethodParameter}
     * for exceptions that require one.
     */
    @SuppressWarnings("unused")
    private void dummyMethod(String value, String other) {
    }

    private MethodParameter methodParameter(int index) throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class, String.class);
        return new MethodParameter(method, index);
    }

    // ---- @ExceptionHandler(ApiException.class) ----

    @Test
    @DisplayName("handleApiException maps status/message from the exception")
    void handleApiException() {
        ApiException ex = new BadRequestException("bad request message");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("bad request message");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("handleApiException reflects the concrete subclass status (CONFLICT)")
    void handleApiExceptionWithConflictStatus() {
        ApiException ex = new EmailAlreadyExistsException("email taken");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("email taken");
    }

    // ---- @ExceptionHandler(BadCredentialsException.class) ----

    @Test
    @DisplayName("handleBadCredentials returns 401 with a fixed generic message")
    void handleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("wrong password");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentials(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
        assertThat(response.getBody().data()).isNull();
    }

    // ---- @ExceptionHandler(DataIntegrityViolationException.class) ----

    @Test
    @DisplayName("handleDataIntegrityViolation returns 409 with a fixed generic message")
    void handleDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("The request could not be completed due to a data conflict (e.g. duplicate or referenced record)");
    }

    // ---- @ExceptionHandler(AccessDeniedException.class) ----

    @Test
    @DisplayName("handleAccessDenied returns 403 with a fixed generic message")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("no permission");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to perform this action");
    }

    // ---- @ExceptionHandler(AuthenticationException.class) ----

    @Test
    @DisplayName("handleAuthentication returns 401 with a fixed generic message")
    void handleAuthentication() {
        AuthenticationException ex = new InsufficientAuthenticationException("no auth");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Authentication failed");
    }

    // ---- @ExceptionHandler(IllegalArgumentException.class) ----

    @Test
    @DisplayName("handleIllegalArgument returns 400 and relays the exception message")
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("bad argument");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("bad argument");
    }

    // ---- @ExceptionHandler(MethodArgumentTypeMismatchException.class) ----

    @Test
    @DisplayName("handleTypeMismatch returns 400 naming the parameter and its required type")
    void handleTypeMismatchWithKnownType() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "age", methodParameter(0), null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Parameter 'age' should be of type Integer");
    }

    @Test
    @DisplayName("handleTypeMismatch falls back to 'unknown' when required type is null")
    void handleTypeMismatchWithUnknownType() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", null, "age", methodParameter(0), null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Parameter 'age' should be of type unknown");
    }

    // ---- @ExceptionHandler(Exception.class) ----

    @Test
    @DisplayName("handleGenericException returns 500 with a fixed generic message, not the raw exception message")
    void handleGenericException() {
        Exception ex = new RuntimeException("some internal secret detail");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("An unexpected error occurred. Please contact support if the problem persists.")
                .doesNotContain("some internal secret detail");
    }

    // ---- handleMethodArgumentNotValid (override) ----

    @Test
    @DisplayName("handleMethodArgumentNotValid returns 422 with field errors from the binding result")
    void handleMethodArgumentNotValidOverride() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "field1", "must not be blank"));
        bindingResult.addError(new FieldError("target", "field2", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter(0), bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        @SuppressWarnings("unchecked")
        ApiResponse<List<FieldErrorItem>> body = (ApiResponse<List<FieldErrorItem>>) response.getBody();
        assertThat(body.status()).isFalse();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.data()).containsExactly(
                new FieldErrorItem("field1", "must not be blank"),
                new FieldErrorItem("field2", "must be positive"));
    }

    // ---- handleHttpMessageNotReadable (override) ----

    @Test
    @DisplayName("handleHttpMessageNotReadable returns 400 with a fixed generic message")
    void handleHttpMessageNotReadableOverride() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isFalse();
        assertThat(body.message()).isEqualTo("The request body is missing or could not be parsed");
        assertThat(body.data()).isNull();
    }

    // ---- handleMissingServletRequestParameter (override) ----

    @Test
    @DisplayName("handleMissingServletRequestParameter returns 400 naming the missing parameter")
    void handleMissingServletRequestParameterOverride() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("id", "Long");

        ResponseEntity<Object> response = handler.handleMissingServletRequestParameter(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Required parameter 'id' is missing");
        assertThat(body.data()).isNull();
    }

    // ---- handleMissingServletRequestPart (override) ----

    @Test
    @DisplayName("handleMissingServletRequestPart returns 400 naming the missing part")
    void handleMissingServletRequestPartOverride() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

        ResponseEntity<Object> response = handler.handleMissingServletRequestPart(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Required part 'file' is missing");
        assertThat(body.data()).isNull();
    }

    // ---- handleHttpRequestMethodNotSupported (override) ----

    @Test
    @DisplayName("handleHttpRequestMethodNotSupported returns 405 and relays the exception message")
    void handleHttpRequestMethodNotSupportedOverride() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<Object> response = handler.handleHttpRequestMethodNotSupported(
                ex, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo(ex.getMessage());
        assertThat(body.data()).isNull();
    }

    // ---- handleHttpMediaTypeNotSupported (override) ----

    @Test
    @DisplayName("handleHttpMediaTypeNotSupported returns 415 and relays the exception message")
    void handleHttpMediaTypeNotSupportedOverride() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("unsupported media type");

        ResponseEntity<Object> response = handler.handleHttpMediaTypeNotSupported(
                ex, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo(ex.getMessage());
        assertThat(body.data()).isNull();
    }

    // ---- handleNoResourceFoundException (override) ----

    @Test
    @DisplayName("handleNoResourceFoundException returns 404 with a fixed generic message")
    void handleNoResourceFoundExceptionOverride() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");

        ResponseEntity<Object> response = handler.handleNoResourceFoundException(
                ex, new HttpHeaders(), HttpStatus.NOT_FOUND, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("No endpoint matches this request");
        assertThat(body.data()).isNull();
    }

    // ---- handleMaxUploadSizeExceededException (override) ----

    @Test
    @DisplayName("handleMaxUploadSizeExceededException returns 413 with a fixed generic message")
    void handleMaxUploadSizeExceededExceptionOverride() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5_242_880L);

        ResponseEntity<Object> response = handler.handleMaxUploadSizeExceededException(
                ex, new HttpHeaders(), HttpStatus.CONTENT_TOO_LARGE, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Uploaded file exceeds the maximum allowed size (5 MB).");
        assertThat(body.data()).isNull();
    }

    // ---- handleHandlerMethodValidationException (override) ----

    @Test
    @DisplayName("handleHandlerMethodValidationException returns 422 with field errors flattened from all parameters")
    void handleHandlerMethodValidationExceptionOverride() throws NoSuchMethodException {
        MethodParameter param0 = methodParameter(0);
        MethodParameter param1 = methodParameter(1);

        MessageSourceResolvable error1 = new DefaultMessageSourceResolvable(new String[]{"code1"}, "must not be blank");
        MessageSourceResolvable error2 = new DefaultMessageSourceResolvable(new String[]{"code2"}, "must be positive");
        MessageSourceResolvable error3 = new DefaultMessageSourceResolvable(new String[]{"code3"}, "must be present");

        ParameterValidationResult result0 = new ParameterValidationResult(
                param0, null, List.of(error1, error2), null, null, null, (err, type) -> null);
        ParameterValidationResult result1 = new ParameterValidationResult(
                param1, null, List.of(error3), null, null, null, (err, type) -> null);

        Method targetMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class, String.class);
        MethodValidationResult validationResult = MethodValidationResult.create(
                new Object(), targetMethod, List.of(result0, result1));
        HandlerMethodValidationException ex = new HandlerMethodValidationException(validationResult);

        ResponseEntity<Object> response = handler.handleHandlerMethodValidationException(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        @SuppressWarnings("unchecked")
        ApiResponse<List<FieldErrorItem>> body = (ApiResponse<List<FieldErrorItem>>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.data()).containsExactly(
                new FieldErrorItem(param0.getParameterName(), "must not be blank"),
                new FieldErrorItem(param0.getParameterName(), "must be positive"),
                new FieldErrorItem(param1.getParameterName(), "must be present"));
    }
}
