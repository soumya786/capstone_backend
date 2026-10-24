package com.capstone.Capstone.project.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Author;
import com.capstone.Capstone.project.model.BookSummary;

/** Per-user wishlist and followed authors ("My Wishlist" / "My Writers"), stored in PostgreSQL. */
@Service
public class LibraryService {

	private final JdbcClient jdbc;
	private final CatalogService catalog;

	public LibraryService(JdbcClient jdbc, CatalogService catalog) {
		this.jdbc = jdbc;
		this.catalog = catalog;
	}

	public List<BookSummary> wishlist(String userId) {
		List<UUID> ids = jdbc.sql("SELECT book_id FROM wishlist_items WHERE user_id = :u"
				+ " ORDER BY created_at, book_id")
				.param("u", userId).query((rs, n) -> rs.getObject("book_id", UUID.class)).list();
		return catalog.summaries(ids);
	}

	/** Idempotent: adding a book that is already on the wishlist succeeds without change. */
	public void addToWishlist(String userId, UUID bookId) {
		catalog.getBook(bookId);
		try {
			jdbc.sql("INSERT INTO wishlist_items (user_id, book_id) VALUES (:u, :b)")
					.param("u", userId).param("b", bookId).update();
		} catch (DuplicateKeyException alreadyThere) {
			// idempotent
		}
	}

	public void removeFromWishlist(String userId, UUID bookId) {
		int removed = jdbc.sql("DELETE FROM wishlist_items WHERE user_id = :u AND book_id = :b")
				.param("u", userId).param("b", bookId).update();
		if (removed == 0) {
			throw new NotFoundException("Book is not in the wishlist");
		}
	}

	public List<Author> writers(String userId) {
		return jdbc.sql("SELECT a.id, a.name, a.bio, a.photo_url FROM followed_authors f"
				+ " JOIN authors a ON a.id = f.author_id WHERE f.user_id = :u ORDER BY f.created_at, a.name")
				.param("u", userId)
				.query((rs, n) -> new Author(rs.getObject("id", UUID.class), rs.getString("name"),
						rs.getString("bio"), rs.getString("photo_url"), true))
				.list();
	}

	/** Idempotent: following an author that is already followed succeeds without change. */
	public void follow(String userId, UUID authorId) {
		catalog.getAuthor(authorId);
		try {
			jdbc.sql("INSERT INTO followed_authors (user_id, author_id) VALUES (:u, :a)")
					.param("u", userId).param("a", authorId).update();
		} catch (DuplicateKeyException alreadyThere) {
			// idempotent
		}
	}

	public void unfollow(String userId, UUID authorId) {
		int removed = jdbc.sql("DELETE FROM followed_authors WHERE user_id = :u AND author_id = :a")
				.param("u", userId).param("a", authorId).update();
		if (removed == 0) {
			throw new NotFoundException("Author is not followed");
		}
	}

	public boolean isFollowing(String userId, UUID authorId) {
		return jdbc.sql("SELECT COUNT(*) FROM followed_authors WHERE user_id = :u AND author_id = :a")
				.param("u", userId).param("a", authorId).query(Long.class).single() > 0;
	}

	public Set<UUID> followedIds(String userId) {
		return new HashSet<>(jdbc.sql("SELECT author_id FROM followed_authors WHERE user_id = :u")
				.param("u", userId).query((rs, n) -> rs.getObject("author_id", UUID.class)).list());
	}
}
