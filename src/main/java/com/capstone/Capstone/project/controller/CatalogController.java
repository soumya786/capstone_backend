package com.capstone.Capstone.project.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.Capstone.project.model.Book;
import com.capstone.Capstone.project.model.BookFormat;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Category;
import com.capstone.Capstone.project.model.HomeSections;
import com.capstone.Capstone.project.model.PageResponse;
import com.capstone.Capstone.project.service.CatalogService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping
public class CatalogController {

	private final CatalogService catalog;

	public CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/catalog/home")
	public HomeSections home(@RequestParam(defaultValue = "6") @Min(1) @Max(50) int limit) {
		return catalog.home(limit);
	}

	@GetMapping("/categories")
	public List<Category> categories() {
		return catalog.categories();
	}

	@GetMapping("/books")
	public PageResponse<BookSummary> search(
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) UUID authorId,
			@RequestParam(required = false) @Size(max = 50) String language,
			@RequestParam(required = false) BookFormat format,
			@RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
			@RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
			@RequestParam(defaultValue = "relevance")
			@Pattern(regexp = "relevance|price_asc|price_desc|newest|bestselling") String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return catalog.search(q, categoryId, authorId, language, format, minPrice, maxPrice, sort, page, size);
	}

	@GetMapping("/books/{bookId}")
	public Book book(@PathVariable UUID bookId) {
		return catalog.getBook(bookId);
	}

	@GetMapping("/books/{bookId}/related")
	public List<BookSummary> related(@PathVariable UUID bookId,
			@RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
		return catalog.related(bookId, limit);
	}
}
