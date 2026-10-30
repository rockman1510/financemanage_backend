package com.financemanage.ledger.infrastructure.web;

import com.financemanage.ledger.application.CategoryService;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.infrastructure.web.CategoryDtos.CategoryResponse;
import com.financemanage.ledger.infrastructure.web.CategoryDtos.CreateCategoryRequest;
import com.financemanage.ledger.infrastructure.web.CategoryDtos.UpdateCategoryRequest;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

    private final CategoryService service;

    CategoryController(CategoryService service) {
        this.service = service;
    }

    /** Flat list, each parent followed by its sub-categories ({@code parentId} set). */
    @GetMapping
    List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) CategoryType type) {
        return service.list(CurrentUser.id(jwt), type).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(service.create(CurrentUser.id(jwt), request.name(), request.type(),
                request.parentId(), request.icon()));
    }

    @PatchMapping("/{id}")
    CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(service.update(id, CurrentUser.id(jwt), request.name(), request.icon()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }
}
