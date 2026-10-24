package com.capstone.Capstone.project.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Book;
import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CartItem;
import com.capstone.Capstone.project.model.Money;

@Service
public class CartService {

	static final int MAX_QUANTITY = 20;

	private final CatalogService catalog;
	private final Map<String, Map<UUID, Integer>> carts = new ConcurrentHashMap<>();

	public CartService(CatalogService catalog) {
		this.catalog = catalog;
	}

	public synchronized Cart get(String userId) {
		return view(items(userId));
	}

	public synchronized Cart add(String userId, UUID bookId, int quantity) {
		Book book = catalog.getBook(bookId);
		if (!book.inStock()) {
			throw new ConflictException("Book is out of stock");
		}
		Map<UUID, Integer> items = items(userId);
		items.merge(bookId, quantity, (current, added) -> Math.min(MAX_QUANTITY, current + added));
		return view(items);
	}

	public synchronized Cart update(String userId, UUID bookId, int quantity) {
		Map<UUID, Integer> items = items(userId);
		if (!items.containsKey(bookId)) {
			throw new NotFoundException("Book is not in the cart");
		}
		items.put(bookId, quantity);
		return view(items);
	}

	public synchronized Cart remove(String userId, UUID bookId) {
		Map<UUID, Integer> items = items(userId);
		if (items.remove(bookId) == null) {
			throw new NotFoundException("Book is not in the cart");
		}
		return view(items);
	}

	public synchronized void clear(String userId) {
		carts.remove(userId);
	}

	private Map<UUID, Integer> items(String userId) {
		return carts.computeIfAbsent(userId, k -> new LinkedHashMap<>());
	}

	private Cart view(Map<UUID, Integer> items) {
		List<CartItem> lines = items.entrySet().stream().map(e -> {
			Book book = catalog.getBook(e.getKey());
			return new CartItem(book.toSummary(), e.getValue(), book.price().times(e.getValue()));
		}).toList();
		Money subtotal = lines.stream().map(CartItem::lineTotal).reduce(Money.zero(), Money::plus);
		int count = lines.stream().mapToInt(CartItem::quantity).sum();
		return new Cart(lines, count, subtotal);
	}
}
