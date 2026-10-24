package com.capstone.Capstone.project.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CreateOrderRequest;
import com.capstone.Capstone.project.model.CreatePaymentRequest;
import com.capstone.Capstone.project.model.Order;
import com.capstone.Capstone.project.model.OrderStatus;
import com.capstone.Capstone.project.model.PageResponse;
import com.capstone.Capstone.project.model.Payment;
import com.capstone.Capstone.project.service.OrderService;
import com.capstone.Capstone.project.service.PaymentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderService orders;
	private final PaymentService payments;

	public OrderController(OrderService orders, PaymentService payments) {
		this.orders = orders;
		this.payments = payments;
	}

	@GetMapping
	public PageResponse<Order> list(@RequestParam(required = false) OrderStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return orders.list(jwt.getSubject(), status, page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Order create(@RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
		return orders.create(jwt.getSubject(), idempotencyKey, request.shippingAddress());
	}

	// Declared before /{orderId} for readability; Spring prefers the literal path either way.
	@GetMapping("/buy-again")
	public List<BookSummary> buyAgainBooks(@RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit,
			@AuthenticationPrincipal Jwt jwt) {
		return orders.buyAgain(jwt.getSubject(), limit);
	}

	@GetMapping("/{orderId}")
	public Order get(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
		return orders.get(jwt.getSubject(), orderId);
	}

	@PostMapping("/{orderId}/cancel")
	public Order cancel(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
		return orders.cancel(jwt.getSubject(), orderId);
	}

	@PostMapping("/{orderId}/buy-again")
	public Cart buyOrderAgain(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
		return orders.buyOrderAgain(jwt.getSubject(), orderId);
	}

	@PostMapping("/{orderId}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public Payment pay(@PathVariable UUID orderId,
			@RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey,
			@Valid @RequestBody CreatePaymentRequest request, @AuthenticationPrincipal Jwt jwt) {
		return payments.create(jwt.getSubject(), orderId, idempotencyKey, request);
	}

	@GetMapping("/{orderId}/payments")
	public List<Payment> listPayments(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
		return payments.list(jwt.getSubject(), orderId);
	}
}
