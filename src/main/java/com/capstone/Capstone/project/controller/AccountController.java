package com.capstone.Capstone.project.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.User;
import com.capstone.Capstone.project.service.LibraryService;

@RestController
@RequestMapping("/me")
public class AccountController {

	private final LibraryService library;

	public AccountController(LibraryService library) {
		this.library = library;
	}

	@GetMapping
	public User me(@AuthenticationPrincipal Jwt jwt) {
		return new User(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
	}

	@GetMapping("/wishlist")
	public List<BookSummary> wishlist(@AuthenticationPrincipal Jwt jwt) {
		return library.wishlist(jwt.getSubject());
	}

	@PutMapping("/wishlist/{bookId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void addToWishlist(@PathVariable UUID bookId, @AuthenticationPrincipal Jwt jwt) {
		library.addToWishlist(jwt.getSubject(), bookId);
	}

	@DeleteMapping("/wishlist/{bookId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeFromWishlist(@PathVariable UUID bookId, @AuthenticationPrincipal Jwt jwt) {
		library.removeFromWishlist(jwt.getSubject(), bookId);
	}
}
