package com.capstone.Capstone.project.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Book;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CartItem;
import com.capstone.Capstone.project.model.Money;

/** Shopping cart stored in the {@code cart_items} table, one row per user and book. */
@Service
public class CartService {

	static final int MAX_QUANTITY = 20;

	private record Line(UUID bookId, int quantity) {
	}

	private final JdbcClient jdbc;
	private final CatalogService catalog;

	public CartService(JdbcClient jdbc, CatalogService catalog) {
		this.jdbc = jdbc;
		this.catalog = catalog;
	}

	public Cart get(String userId) {
		List<Line> lines = jdbc.sql("SELECT book_id, quantity FROM cart_items WHERE user_id = :u"
				+ " ORDER BY added_at, book_id")
				.param("u", userId)
				.query((rs, n) -> new Line(rs.getObject("book_id", UUID.class), rs.getInt("quantity")))
				.list();
		Map<UUID, BookSummary> books = catalog.summariesById(lines.stream().map(Line::bookId).toList());

		List<CartItem> items = lines.stream().filter(l -> books.containsKey(l.bookId())).map(l -> {
			BookSummary book = books.get(l.bookId());
			return new CartItem(book, l.quantity(), book.price().times(l.quantity()));
		}).toList();
		Money subtotal = items.stream().map(CartItem::lineTotal).reduce(Money.zero(), Money::plus);
		int count = items.stream().mapToInt(CartItem::quantity).sum();
		return new Cart(items, count, subtotal);
	}

	public Cart add(String userId, UUID bookId, int quantity) {
		Book book = catalog.getBook(bookId);
		if (!book.inStock()) {
			throw new ConflictException("Book is out of stock");
		}
		if (increase(userId, bookId, quantity) == 0) {
			try {
				jdbc.sql("INSERT INTO cart_items (user_id, book_id, quantity) VALUES (:u, :b, :q)")
						.param("u", userId).param("b", bookId).param("q", Math.min(quantity, MAX_QUANTITY))
						.update();
			} catch (DuplicateKeyException raced) {
				// Another request created the row between our UPDATE and INSERT; add to it instead.
				increase(userId, bookId, quantity);
			}
		}
		return get(userId);
	}

	public Cart update(String userId, UUID bookId, int quantity) {
		int updated = jdbc.sql("UPDATE cart_items SET quantity = :q WHERE user_id = :u AND book_id = :b")
				.param("q", quantity).param("u", userId).param("b", bookId).update();
		if (updated == 0) {
			throw new NotFoundException("Book is not in the cart");
		}
		return get(userId);
	}

	public Cart remove(String userId, UUID bookId) {
		int removed = jdbc.sql("DELETE FROM cart_items WHERE user_id = :u AND book_id = :b")
				.param("u", userId).param("b", bookId).update();
		if (removed == 0) {
			throw new NotFoundException("Book is not in the cart");
		}
		return get(userId);
	}

	public void clear(String userId) {
		jdbc.sql("DELETE FROM cart_items WHERE user_id = :u").param("u", userId).update();
	}

	private int increase(String userId, UUID bookId, int quantity) {
		return jdbc.sql("UPDATE cart_items SET quantity = LEAST(:max, quantity + :q)"
				+ " WHERE user_id = :u AND book_id = :b")
				.param("max", MAX_QUANTITY).param("q", quantity).param("u", userId).param("b", bookId)
				.update();
	}
}
