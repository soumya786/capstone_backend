package com.capstone.Capstone.project.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Author;
import com.capstone.Capstone.project.model.BookSummary;

/** Per-user wishlist and followed authors ("My Wishlist" / "My Writers"). */
@Service
public class LibraryService {

	private final CatalogService catalog;
	private final Map<String, CopyOnWriteArraySet<UUID>> wishlists = new ConcurrentHashMap<>();
	private final Map<String, CopyOnWriteArraySet<UUID>> writers = new ConcurrentHashMap<>();

	public LibraryService(CatalogService catalog) {
		this.catalog = catalog;
	}

	public List<BookSummary> wishlist(String userId) {
		return set(wishlists, userId).stream().map(id -> catalog.getBook(id).toSummary()).toList();
	}

	public void addToWishlist(String userId, UUID bookId) {
		catalog.getBook(bookId);
		set(wishlists, userId).add(bookId);
	}

	public void removeFromWishlist(String userId, UUID bookId) {
		if (!set(wishlists, userId).remove(bookId)) {
			throw new NotFoundException("Book is not in the wishlist");
		}
	}

	public List<Author> writers(String userId) {
		return set(writers, userId).stream().map(id -> catalog.getAuthor(id).withFollowing(true)).toList();
	}

	public void follow(String userId, UUID authorId) {
		catalog.getAuthor(authorId);
		set(writers, userId).add(authorId);
	}

	public void unfollow(String userId, UUID authorId) {
		if (!set(writers, userId).remove(authorId)) {
			throw new NotFoundException("Author is not followed");
		}
	}

	public boolean isFollowing(String userId, UUID authorId) {
		return set(writers, userId).contains(authorId);
	}

	private static CopyOnWriteArraySet<UUID> set(Map<String, CopyOnWriteArraySet<UUID>> store, String userId) {
		return store.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
	}
}
