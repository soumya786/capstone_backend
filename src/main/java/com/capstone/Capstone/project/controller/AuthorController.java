package com.capstone.Capstone.project.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.Capstone.project.model.Author;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.PageResponse;
import com.capstone.Capstone.project.service.CatalogService;
import com.capstone.Capstone.project.service.LibraryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping
public class AuthorController {

	private final CatalogService catalog;
	private final LibraryService library;

	public AuthorController(CatalogService catalog, LibraryService library) {
		this.catalog = catalog;
		this.library = library;
	}

	@GetMapping("/authors")
	public PageResponse<Author> authors(@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		PageResponse<Author> result = catalog.authors(q, page, size);
		if (jwt == null) {
			return result;
		}
		Set<UUID> followed = library.followedIds(jwt.getSubject());
		List<Author> marked = result.content().stream()
				.map(a -> a.withFollowing(followed.contains(a.id()))).toList();
		return new PageResponse<>(marked, result.page(), result.size(), result.totalElements(),
				result.totalPages());
	}

	@GetMapping("/authors/{authorId}")
	public Author author(@PathVariable UUID authorId, @AuthenticationPrincipal Jwt jwt) {
		Author author = catalog.getAuthor(authorId);
		return jwt == null ? author : author.withFollowing(library.isFollowing(jwt.getSubject(), authorId));
	}

	@GetMapping("/authors/{authorId}/books")
	public PageResponse<BookSummary> books(@PathVariable UUID authorId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return catalog.booksByAuthor(authorId, page, size);
	}

	@GetMapping("/me/writers")
	public List<Author> myWriters(@AuthenticationPrincipal Jwt jwt) {
		return library.writers(jwt.getSubject());
	}

	@PutMapping("/me/writers/{authorId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void follow(@PathVariable UUID authorId, @AuthenticationPrincipal Jwt jwt) {
		library.follow(jwt.getSubject(), authorId);
	}

	@DeleteMapping("/me/writers/{authorId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unfollow(@PathVariable UUID authorId, @AuthenticationPrincipal Jwt jwt) {
		library.unfollow(jwt.getSubject(), authorId);
	}

}
