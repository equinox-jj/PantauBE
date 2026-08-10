package com.project.pantau.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.project.pantau.common.exception.BadRequestException;
import com.project.pantau.common.exception.ValidationException;
import com.project.pantau.dto.upload.UploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Cloudinary#uploader()} always constructs a brand-new {@code new Uploader(this, ...)}
 * instance internally (see cloudinary-core source), so it cannot be stubbed to hand back a mock
 * on a *real* Cloudinary object. Since {@code cloudinary} itself is a Mockito mock here, its
 * {@code uploader()} method never runs that real body - Mockito intercepts the call and returns
 * whatever we stub (a mocked {@link Uploader}). Neither {@link Cloudinary} nor {@link Uploader}
 * (nor their upload/destroy methods) are final, so this mocking seam works cleanly with plain
 * Mockito mocks - no deep stubs or inline-mock-maker tricks required.
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private UploadServiceImpl uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadServiceImpl(cloudinary);
    }

    private byte[] validPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // ---------- upload ----------

    @Test
    @DisplayName("upload() returns a populated UploadResponse when Cloudinary upload succeeds")
    void upload_shouldReturnUploadResponse_whenValidImageProvided() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        Map<String, Object> cloudinaryResult = new HashMap<>();
        cloudinaryResult.put("public_id", "abc123");
        cloudinaryResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/abc123.png");
        cloudinaryResult.put("url", "http://res.cloudinary.com/demo/image/upload/abc123.png");

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(cloudinaryResult);

        LocalDateTime before = LocalDateTime.now();
        UploadResponse response = uploadService.upload(file);
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.id()).isEqualTo("abc123");
        assertThat(response.url()).isEqualTo("https://res.cloudinary.com/demo/image/upload/abc123.png");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("upload() sends the raw file bytes and expected upload options to Cloudinary")
    void upload_shouldSendExpectedBytesAndOptions_toCloudinary() throws IOException {
        byte[] bytes = validPngBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);

        Map<String, Object> cloudinaryResult = new HashMap<>();
        cloudinaryResult.put("public_id", "abc123");
        cloudinaryResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/abc123.png");

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(cloudinaryResult);

        uploadService.upload(file);

        ArgumentCaptor<Object> fileCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(fileCaptor.capture(), optionsCaptor.capture());

        assertThat(fileCaptor.getValue()).isEqualTo(bytes);

        Map<?, ?> options = optionsCaptor.getValue();
        assertThat(options.get("folder")).isEqualTo("temp");
        assertThat(options.get("resource_type")).isEqualTo("image");
        assertThat(options.get("overwrite")).isEqualTo(false);
        assertThat(options.get("public_id")).isInstanceOf(String.class);
        assertThat((String) options.get("public_id")).isNotBlank();
    }

    @Test
    @DisplayName("upload() throws BadRequestException when Cloudinary upload raises an IOException")
    void upload_shouldThrowBadRequestException_whenCloudinaryUploadThrowsIOException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Failed to upload image.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when file is null")
    void upload_shouldThrowValidationException_whenFileIsNull() {
        assertThatThrownBy(() -> uploadService.upload(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Image is required.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when file is empty")
    void upload_shouldThrowValidationException_whenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Image is required.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when file exceeds the maximum size")
    void upload_shouldThrowValidationException_whenFileExceedsMaxSize() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", tooBig);

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Maximum image size is 5MB.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when content type is not an allowed image type")
    void upload_shouldThrowValidationException_whenContentTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Only JPEG, PNG, WEBP, or GIF images are allowed.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when content type is missing")
    void upload_shouldThrowValidationException_whenContentTypeIsMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", null, "hello".getBytes());

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Only JPEG, PNG, WEBP, or GIF images are allowed.");
    }

    @Test
    @DisplayName("upload() throws ValidationException when the file content is not a decodable image")
    void upload_shouldThrowValidationException_whenFileIsNotDecodableImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "not-a-real-image".getBytes());

        assertThatThrownBy(() -> uploadService.upload(file))
                .isInstanceOf(ValidationException.class)
                .hasMessage("The uploaded file is not a valid image.");
    }

    @Test
    @DisplayName("upload() never calls Cloudinary when validation fails")
    void upload_shouldNotCallCloudinary_whenValidationFails() {
        assertThatThrownBy(() -> uploadService.upload(null)).isInstanceOf(ValidationException.class);

        verify(cloudinary, never()).uploader();
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete() completes without error when Cloudinary reports 'ok'")
    void delete_shouldCompleteSuccessfully_whenStatusIsOk() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("abc123"), anyMap())).thenReturn(Map.of("result", "ok"));

        uploadService.delete("abc123");

        verify(uploader).destroy(eq("abc123"), anyMap());
    }

    @Test
    @DisplayName("delete() completes without error when Cloudinary reports 'not_found'")
    void delete_shouldCompleteSuccessfully_whenStatusIsNotFound() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("missing-id"), anyMap())).thenReturn(Map.of("result", "not_found"));

        uploadService.delete("missing-id");

        verify(uploader).destroy(eq("missing-id"), anyMap());
    }

    @Test
    @DisplayName("delete() throws RuntimeException when Cloudinary reports a failure status")
    void delete_shouldThrowRuntimeException_whenCloudinaryReportsFailureStatus() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("abc123"), anyMap())).thenReturn(Map.of("result", "error"));

        assertThatThrownBy(() -> uploadService.delete("abc123"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(BadRequestException.class)
                .hasMessage("Cloudinary deletion failed with status: error");
    }

    @Test
    @DisplayName("delete() throws BadRequestException when Cloudinary destroy raises an IOException")
    void delete_shouldThrowBadRequestException_whenCloudinaryDestroyThrowsIOException() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        doThrow(new IOException("network down")).when(uploader).destroy(eq("abc123"), anyMap());

        assertThatThrownBy(() -> uploadService.delete("abc123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Failed to delete image.");
    }

    @Test
    @DisplayName("delete() throws ValidationException when id is null")
    void delete_shouldThrowValidationException_whenIdIsNull() {
        assertThatThrownBy(() -> uploadService.delete(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("ID cannot be null");
    }

    @Test
    @DisplayName("delete() never calls Cloudinary when id is null")
    void delete_shouldNotCallCloudinary_whenIdIsNull() {
        assertThatThrownBy(() -> uploadService.delete(null)).isInstanceOf(ValidationException.class);

        verify(cloudinary, never()).uploader();
    }
}
