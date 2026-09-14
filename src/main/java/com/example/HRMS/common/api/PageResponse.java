package com.example.HRMS.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Consistent paginated response envelope for list endpoints.
 *
 * <p>Governance requires "pagination from day one" with an explicit page/size
 * contract. This is the single shared shape so list responses stay consistent
 * across modules and clients never parse ad-hoc structures. It exposes only
 * pagination metadata and the page content (which must itself be DTOs).
 *
 * @param <T> the response DTO type for a single item
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /** Build from a Spring Data {@link Page} whose content is already mapped to DTOs. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
