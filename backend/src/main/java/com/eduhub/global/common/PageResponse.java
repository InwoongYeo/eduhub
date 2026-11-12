package com.eduhub.global.common;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * 목록 API의 페이지 응답 형식 (NFR-02). Page 객체를 그대로 노출하지 않는다.
 */
public record PageResponse<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> content,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}
