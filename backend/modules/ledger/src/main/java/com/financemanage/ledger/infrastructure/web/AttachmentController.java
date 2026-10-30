package com.financemanage.ledger.infrastructure.web;

import com.financemanage.ledger.application.AttachmentContent;
import com.financemanage.ledger.application.AttachmentService;
import com.financemanage.ledger.domain.model.Attachment;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.AttachmentResponse;
import com.financemanage.shared.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Receipt photos and documents of transactions (JPEG, PNG, WebP, HEIC or PDF). */
@RestController
class AttachmentController {

    private final AttachmentService service;

    AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/v1/transactions/{transactionId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId,
                              @RequestParam("file") MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return AttachmentResponse.from(service.upload(CurrentUser.id(jwt), transactionId,
                    file.getOriginalFilename(), file.getContentType(), file.getSize(), content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/api/v1/attachments/{id}")
    ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AttachmentContent content = service.open(id, CurrentUser.id(jwt));
        Attachment attachment = content.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(attachment.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.content()));
    }

    @DeleteMapping("/api/v1/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }
}
