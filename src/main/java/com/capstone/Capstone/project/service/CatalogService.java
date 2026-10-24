package com.capstone.Capstone.project.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.BadRequestException;
import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Author;
import com.capstone.Capstone.project.model.AuthorSummary;
import com.capstone.Capstone.project.model.Book;
import com.capstone.Capstone.project.model.BookFormat;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Category;
import com.capstone.Capstone.project.model.HomeSections;
import com.capstone.Capstone.project.model.Money;
import com.capstone.Capstone.project.model.PageResponse;

/**
 * Read-only book catalogue backed by in-memory seed data taken from the wireframes.
 * Ids are derived from names so they stay stable across restarts.
 */
@Service
public class CatalogService {

	private static final String ENGLISH = "English";
	private static final int DELIVERY_DAYS = 3;

	private final List<Category> categories = new ArrayList<>();
	private final Map<UUID, Author> authorsById = new LinkedHashMap<>();
	private final Map<UUID, Book> booksById = new LinkedHashMap<>();
	private final List<UUID> recommended = new ArrayList<>();
	private final List<UUID> bestsellers = new ArrayList<>();
	private final List<UUID> newLaunches = new ArrayList<>();

	public CatalogService() {
		for (String name : List.of("Romance", "Mystery", "Science Fiction", "Fantasy", "Historical", "Biography",
				"Self-help", "Memoir", "Travel", "Cooking", "Children's", "Young Adult", "Comics & Graphic Novels",
				"Poetry", "Drama", "Science", "Philosophy", "Religion", "Language Learning")) {
			categories.add(new Category(stableId("category", name), name));
		}

		addAuthor("Arjun Patel", "Writes about focus, habits and deliberate work.");
		addAuthor("Raj Patel", "Educator and writer on lifelong learning.");
		addAuthor("James Wright", "Author of practical guides to goal setting.");
		addAuthor("James Adams", "Writer of atmospheric thrillers.");
		addAuthor("Jessica Martin", "Romance and drama novelist.");
		addAuthor("Laura Mitchell", "Science fiction author.");
		addAuthor("Daniel Reed", "Minimalism advocate and essayist.");
		addAuthor("Clara Nelson", "Author of mystery and horror fiction.");
		addAuthor("Emily Parker", "Children's author.");

		recommended.add(addBook("The Art of Focus", "Arjun Patel",
				"Practical guide to mastering focus & boosting productivity every day.",
				BookFormat.PAPERBACK, List.of("Non-fiction", "Self Help"), 399, List.of("Self-help"),
				LocalDate.of(2024, 3, 12), 240, 4.5));
		recommended.add(addBook("The Art of Learning", "Raj Patel",
				"Master the mindset and methods for effective lifelong learning.",
				BookFormat.PAPERBACK, List.of("Non-fiction", "Self Help"), 259, List.of("Self-help"),
				LocalDate.of(2023, 8, 1), 210, 4.3));
		recommended.add(addBook("The Path to Success", "James Wright",
				"A practical guide to achieving goals with clarity and confidence.",
				BookFormat.PAPERBACK, List.of("Non-fiction", "Self Help"), 359, List.of("Self-help"),
				LocalDate.of(2022, 11, 20), 280, 4.1));

		bestsellers.add(addBook("The Midnight Hour", "James Adams",
				"Haunting tale of a man's journey & the shadows of a forgotten past.",
				BookFormat.PAPERBACK, List.of("Fiction", "Thriller", "Horror"), 299, List.of("Mystery"),
				LocalDate.of(2024, 6, 5), 320, 4.7));
		bestsellers.add(addBook("Beneath the Stars", "Jessica Martin",
				"A heartwarming tale, where two souls discover who you need.",
				BookFormat.HARDCOVER, List.of("Fiction", "Love", "Drama"), 499, List.of("Romance", "Drama"),
				LocalDate.of(2024, 2, 14), 360, 4.6));
		bestsellers.add(addBook("The Final Frontier", "Laura Mitchell",
				"A mission to space secrets to change humanity forever.",
				BookFormat.PAPERBACK, List.of("Fiction", "Thriller"), 359, List.of("Science Fiction"),
				LocalDate.of(2024, 1, 9), 400, 4.4));

		newLaunches.add(addBook("Joy of Minimalism", "Daniel Reed",
				"Declutter your life to uncover peace, clarity, and joy.",
				BookFormat.PAPERBACK, List.of("Non-fiction", "Self Help"), 149, List.of("Self-help"),
				LocalDate.of(2025, 7, 1), 180, 4.0));
		newLaunches.add(addBook("The Vanishing House", "Clara Nelson",
				"A chilling mystery unfolds within a house that disappears.",
				BookFormat.EBOOK, List.of("Fiction", "Horror"), 99, List.of("Mystery"),
				LocalDate.of(2025, 7, 10), 260, 4.2));
		newLaunches.add(addBook("The Lost Kitten", "Emily Parker",
				"A heartwarming tale of courage, friendship, and feline adventure.",
				BookFormat.HARDCOVER, List.of("Fiction", "Children"), 339, List.of("Children's"),
				LocalDate.of(2025, 7, 15), 120, 4.8));
	}

	// ---- catalogue -------------------------------------------------------------------------

	public HomeSections home(int limit) {
		return new HomeSections(summaries(recommended, limit), summaries(bestsellers, limit),
				summaries(newLaunches, limit));
	}

	public List<Category> categories() {
		return List.copyOf(categories);
	}

	public PageResponse<BookSummary> search(String q, UUID categoryId, UUID authorId, String language,
			BookFormat format, BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size) {
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BadRequestException("minPrice must not exceed maxPrice");
		}
		String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);

		List<Book> matches = booksById.values().stream()
				.filter(b -> needle == null || matchesText(b, needle))
				.filter(b -> categoryId == null || b.categories().stream().anyMatch(c -> c.id().equals(categoryId)))
				.filter(b -> authorId == null || b.authors().stream().anyMatch(a -> a.id().equals(authorId)))
				.filter(b -> language == null || b.language().equalsIgnoreCase(language))
				.filter(b -> format == null || b.format() == format)
				.filter(b -> minPrice == null || b.price().amount().compareTo(minPrice) >= 0)
				.filter(b -> maxPrice == null || b.price().amount().compareTo(maxPrice) <= 0)
				.sorted(comparator(sort))
				.toList();

		List<BookSummary> summaries = matches.stream().map(b -> view(b).toSummary()).toList();
		return PageResponse.of(summaries, page, size);
	}

	public Book getBook(UUID id) {
		Book book = booksById.get(id);
		if (book == null) {
			throw new NotFoundException("Book not found");
		}
		return view(book);
	}

	public List<BookSummary> related(UUID id, int limit) {
		Book base = booksById.get(id);
		if (base == null) {
			throw new NotFoundException("Book not found");
		}
		Set<UUID> categoryIds = base.categories().stream().map(Category::id).collect(Collectors.toSet());
		Set<UUID> authorIds = base.authors().stream().map(AuthorSummary::id).collect(Collectors.toSet());
		return booksById.values().stream()
				.filter(b -> !b.id().equals(id))
				.filter(b -> b.categories().stream().anyMatch(c -> categoryIds.contains(c.id()))
						|| b.authors().stream().anyMatch(a -> authorIds.contains(a.id())))
				.limit(limit)
				.map(b -> view(b).toSummary())
				.toList();
	}

	// ---- authors ---------------------------------------------------------------------------

	public PageResponse<Author> authors(String q, int page, int size) {
		String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
		List<Author> matches = authorsById.values().stream()
				.filter(a -> needle == null || a.name().toLowerCase(Locale.ROOT).contains(needle))
				.toList();
		return PageResponse.of(matches, page, size);
	}

	public Author getAuthor(UUID id) {
		Author author = authorsById.get(id);
		if (author == null) {
			throw new NotFoundException("Author not found");
		}
		return author;
	}

	public PageResponse<BookSummary> booksByAuthor(UUID authorId, int page, int size) {
		getAuthor(authorId);
		List<BookSummary> books = booksById.values().stream()
				.filter(b -> b.authors().stream().anyMatch(a -> a.id().equals(authorId)))
				.map(b -> view(b).toSummary())
				.toList();
		return PageResponse.of(books, page, size);
	}

	// ---- helpers ---------------------------------------------------------------------------

	private List<BookSummary> summaries(List<UUID> ids, int limit) {
		return ids.stream().limit(limit).map(id -> view(booksById.get(id)).toSummary()).toList();
	}

	private static boolean matchesText(Book b, String needle) {
		return b.title().toLowerCase(Locale.ROOT).contains(needle)
				|| b.shortDescription().toLowerCase(Locale.ROOT).contains(needle)
				|| b.authors().stream().anyMatch(a -> a.name().toLowerCase(Locale.ROOT).contains(needle));
	}

	/** "relevance" keeps catalogue order; "bestselling" ranks by rating in this seeded catalogue. */
	private static Comparator<Book> comparator(String sort) {
		if (sort == null) {
			return (a, b) -> 0;
		}
		return switch (sort) {
			case "price_asc" -> Comparator.comparing(b -> b.price().amount());
			case "price_desc" -> Comparator.comparing((Book b) -> b.price().amount()).reversed();
			case "newest" -> Comparator.comparing(Book::publishedDate).reversed();
			case "bestselling" -> Comparator.comparing(Book::rating).reversed();
			default -> (a, b) -> 0;
		};
	}

	/** eBooks are delivered instantly; physical books get a rolling estimate. */
	private static Book view(Book book) {
		LocalDate eta = book.format() == BookFormat.EBOOK ? null : LocalDate.now().plusDays(DELIVERY_DAYS);
		return book.withEstimatedDeliveryDate(eta);
	}

	private void addAuthor(String name, String bio) {
		UUID id = stableId("author", name);
		authorsById.put(id, new Author(id, name, bio, null, null));
	}

	private UUID addBook(String title, String authorName, String shortDescription, BookFormat format,
			List<String> genres, long priceInr, List<String> categoryNames, LocalDate published, int pages,
			double rating) {
		Author author = authorsById.get(stableId("author", authorName));
		List<Category> bookCategories = categories.stream().filter(c -> categoryNames.contains(c.name())).toList();
		UUID id = stableId("book", title);
		booksById.put(id, new Book(id, title, List.of(author.toSummary()), shortDescription, null, format, genres,
				Money.inr(priceInr), null, shortDescription, bookCategories, ENGLISH, null, null, published, pages,
				true, rating));
		return id;
	}

	private static UUID stableId(String kind, String name) {
		return UUID.nameUUIDFromBytes((kind + ":" + name).getBytes(StandardCharsets.UTF_8));
	}
}
