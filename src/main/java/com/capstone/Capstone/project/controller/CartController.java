package com.capstone.Capstone.project.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CartItemRequest;
import com.capstone.Capstone.project.model.UpdateQuantityRequest;
import com.capstone.Capstone.project.service.CartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/cart")
public class CartController {

	private final CartService cart;

	public CartController(CartService cart) {
		this.cart = cart;
	}

	@GetMapping
	public Cart get(@AuthenticationPrincipal Jwt jwt) {
		return cart.get(jwt.getSubject());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clear(@AuthenticationPrincipal Jwt jwt) {
		cart.clear(jwt.getSubject());
	}

	@PostMapping("/items")
	public Cart add(@Valid @RequestBody CartItemRequest request, @AuthenticationPrincipal Jwt jwt) {
		return cart.add(jwt.getSubject(), request.bookId(), request.quantity());
	}

	@PatchMapping("/items/{bookId}")
	public Cart update(@PathVariable UUID bookId, @Valid @RequestBody UpdateQuantityRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return cart.update(jwt.getSubject(), bookId, request.quantity());
	}

	@DeleteMapping("/items/{bookId}")
	public Cart remove(@PathVariable UUID bookId, @AuthenticationPrincipal Jwt jwt) {
		return cart.remove(jwt.getSubject(), bookId);
	}
}
