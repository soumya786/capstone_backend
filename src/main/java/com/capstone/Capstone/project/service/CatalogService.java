package com.capstone.Capstone.project.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
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
 * Read-only book catalogue stored in PostgreSQL (tables created and seeded by Flyway).
 * Every value reaching SQL is a bind parameter; the only concatenated fragments are fixed
 * strings chosen by this class, never user input.
 */
@Service
public class CatalogService {

	private static final int DELIVERY_DAYS = 3;
	private static final char LIKE_ESCAPE = '!';

	private static final String BOOK_COLUMNS = "b.id, b.title, b.short_description, b.description, "
			+ "b.cover_image_url, b.book_format, b.price, b.currency, b.language, b.isbn, b.publisher, "
			+ "b.published_date, b.page_count, b.in_stock, b.rating";

	private record BookRow(UUID id, String title, String shortDescription, String description,
			String coverImageUrl, BookFormat format, Money price, String language, String isbn,
			String publisher, LocalDate publishedDate, Integer pageCount, boolean inStock, Double rating) {
	}

	private record Link<T>(UUID bookId, T value) {
	}

	private static final RowMapper<BookRow> BOOK_ROW = (rs, n) -> new BookRow(
			rs.getObject("id", UUID.class),
			rs.getString("title"),
			rs.getString("short_description"),
			rs.getString("description"),
			rs.getString("cover_image_url"),
			BookFormat.valueOf(rs.getString("book_format")),
			new Money(rs.getBigDecimal("price"), rs.getString("currency")),
			rs.getString("language"),
			rs.getString("isbn"),
			rs.getString("publisher"),
			rs.getObject("published_date", LocalDate.class),
			rs.getObject("page_count", Integer.class),
			rs.getBoolean("in_stock"),
			toDouble(rs.getBigDecimal("rating")));

	private static final RowMapper<Author> AUTHOR_ROW = (rs, n) -> new Author(
			rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("bio"),
			rs.getString("photo_url"), null);

	private final JdbcClient jdbc;

	public CatalogService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ---- catalogue -------------------------------------------------------------------------

	public HomeSections home(int limit) {
		return new HomeSections(section("RECOMMENDED", limit), section("BESTSELLERS", limit),
				section("NEW_LAUNCHES", limit));
	}

	public List<Category> categories() {
		return jdbc.sql("SELECT id, name FROM categories ORDER BY sort_order")
				.query((rs, n) -> new Category(rs.getObject("id", UUID.class), rs.getString("name")))
				.list();
	}

	public PageResponse<BookSummary> search(String q, UUID categoryId, UUID authorId, String language,
			BookFormat format, BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size) {
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BadRequestException("minPrice must not exceed maxPrice");
		}

		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (q != null && !q.isBlank()) {
			where.append(" AND (LOWER(b.title) LIKE :q ESCAPE '").append(LIKE_ESCAPE).append("'")
					.append(" OR LOWER(b.short_description) LIKE :q ESCAPE '").append(LIKE_ESCAPE).append("'")
					.append(" OR EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.id = ba.author_id")
					.append(" WHERE ba.book_id = b.id AND LOWER(a.name) LIKE :q ESCAPE '")
					.append(LIKE_ESCAPE).append("'))");
			params.put("q", likePattern(q));
		}
		if (categoryId != null) {
			where.append(" AND EXISTS (SELECT 1 FROM book_categories bc WHERE bc.book_id = b.id"
					+ " AND bc.category_id = :categoryId)");
			params.put("categoryId", categoryId);
		}
		if (authorId != null) {
			where.append(" AND EXISTS (SELECT 1 FROM book_authors ba2 WHERE ba2.book_id = b.id"
					+ " AND ba2.author_id = :authorId)");
			params.put("authorId", authorId);
		}
		if (language != null && !language.isBlank()) {
			where.append(" AND LOWER(b.language) = :language");
			params.put("language", language.trim().toLowerCase(Locale.ROOT));
		}
		if (format != null) {
			where.append(" AND b.book_format = :format");
			params.put("format", format.name());
		}
		if (minPrice != null) {
			where.append(" AND b.price >= :minPrice");
			params.put("minPrice", minPrice);
		}
		if (maxPrice != null) {
			where.append(" AND b.price <= :maxPrice");
			params.put("maxPrice", maxPrice);
		}

		long total = jdbc.sql("SELECT COUNT(*) FROM books b" + where).params(params).query(Long.class).single();

		Map<String, Object> pageParams = new HashMap<>(params);
		pageParams.put("limit", size);
		pageParams.put("offset", (long) page * size);
		List<BookRow> rows = jdbc.sql("SELECT " + BOOK_COLUMNS + " FROM books b" + where
				+ " ORDER BY " + orderBy(sort) + " LIMIT :limit OFFSET :offset")
				.params(pageParams).query(BOOK_ROW).list();

		List<BookSummary> content = assemble(rows).stream().map(Book::toSummary).toList();
		return PageResponse.of(content, page, size, total);
	}

	public Book getBook(UUID id) {
		BookRow row = jdbc.sql("SELECT " + BOOK_COLUMNS + " FROM books b WHERE b.id = :id")
				.param("id", id).query(BOOK_ROW).optional()
				.orElseThrow(() -> new NotFoundException("Book not found"));
		return assemble(List.of(row)).get(0);
	}

	public List<BookSummary> related(UUID id, int limit) {
		getBook(id);
		List<BookRow> rows = jdbc.sql("SELECT " + BOOK_COLUMNS + " FROM books b WHERE b.id <> :id AND ("
				+ "EXISTS (SELECT 1 FROM book_categories c1 JOIN book_categories c2"
				+ " ON c1.category_id = c2.category_id WHERE c1.book_id = :id AND c2.book_id = b.id)"
				+ " OR EXISTS (SELECT 1 FROM book_authors a1 JOIN book_authors a2"
				+ " ON a1.author_id = a2.author_id WHERE a1.book_id = :id AND a2.book_id = b.id))"
				+ " ORDER BY b.catalog_rank LIMIT :limit")
				.param("id", id).param("limit", limit).query(BOOK_ROW).list();
		return assemble(rows).stream().map(Book::toSummary).toList();
	}

	/** Summaries for the given ids, keyed in the order supplied. Unknown ids are skipped. */
	public Map<UUID, BookSummary> summariesById(Collection<UUID> ids) {
		Map<UUID, BookSummary> found = new HashMap<>();
		if (!ids.isEmpty()) {
			List<BookRow> rows = jdbc.sql("SELECT " + BOOK_COLUMNS + " FROM books b WHERE b.id IN (:ids)")
					.param("ids", List.copyOf(ids)).query(BOOK_ROW).list();
			assemble(rows).forEach(b -> found.put(b.id(), b.toSummary()));
		}
		Map<UUID, BookSummary> ordered = new LinkedHashMap<>();
		for (UUID id : ids) {
			BookSummary summary = found.get(id);
			if (summary != null) {
				ordered.put(id, summary);
			}
		}
		return ordered;
	}

	public List<BookSummary> summaries(List<UUID> ids) {
		return List.copyOf(summariesById(ids).values());
	}

	// ---- authors ---------------------------------------------------------------------------

	public PageResponse<Author> authors(String q, int page, int size) {
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (q != null && !q.isBlank()) {
			where.append(" AND LOWER(a.name) LIKE :q ESCAPE '").append(LIKE_ESCAPE).append("'");
			params.put("q", likePattern(q));
		}
		long total = jdbc.sql("SELECT COUNT(*) FROM authors a" + where).params(params)
				.query(Long.class).single();

		Map<String, Object> pageParams = new HashMap<>(params);
		pageParams.put("limit", size);
		pageParams.put("offset", (long) page * size);
		List<Author> content = jdbc.sql("SELECT a.id, a.name, a.bio, a.photo_url FROM authors a" + where
				+ " ORDER BY a.name LIMIT :limit OFFSET :offset")
				.params(pageParams).query(AUTHOR_ROW).list();
		return PageResponse.of(content, page, size, total);
	}

	public Author getAuthor(UUID id) {
		return jdbc.sql("SELECT id, name, bio, photo_url FROM authors WHERE id = :id")
				.param("id", id).query(AUTHOR_ROW).optional()
				.orElseThrow(() -> new NotFoundException("Author not found"));
	}

	public PageResponse<BookSummary> booksByAuthor(UUID authorId, int page, int size) {
		getAuthor(authorId);
		String where = " WHERE EXISTS (SELECT 1 FROM book_authors ba WHERE ba.book_id = b.id"
				+ " AND ba.author_id = :authorId)";
		long total = jdbc.sql("SELECT COUNT(*) FROM books b" + where).param("authorId", authorId)
				.query(Long.class).single();
		List<BookRow> rows = jdbc.sql("SELECT " + BOOK_COLUMNS + " FROM books b" + where
				+ " ORDER BY b.catalog_rank LIMIT :limit OFFSET :offset")
				.param("authorId", authorId).param("limit", size).param("offset", (long) page * size)
				.query(BOOK_ROW).list();
		List<BookSummary> content = assemble(rows).stream().map(Book::toSummary).toList();
		return PageResponse.of(content, page, size, total);
	}

	// ---- helpers ---------------------------------------------------------------------------

	private List<BookSummary> section(String section, int limit) {
		List<UUID> ids = jdbc.sql("SELECT book_id FROM home_section_books WHERE section = :section"
				+ " ORDER BY sort_order LIMIT :limit")
				.param("section", section).param("limit", limit)
				.query((rs, n) -> rs.getObject("book_id", UUID.class)).list();
		return summaries(ids);
	}

	/** Allowlisted sort fragments; the client value only selects one, it is never concatenated. */
	private static String orderBy(String sort) {
		if (sort == null) {
			return "b.catalog_rank";
		}
		return switch (sort) {
			case "price_asc" -> "b.price ASC, b.catalog_rank";
			case "price_desc" -> "b.price DESC, b.catalog_rank";
			case "newest" -> "b.published_date DESC NULLS LAST, b.catalog_rank";
			case "bestselling" -> "b.rating DESC NULLS LAST, b.catalog_rank";
			default -> "b.catalog_rank";
		};
	}

	private static String likePattern(String text) {
		String escaped = text.trim().toLowerCase(Locale.ROOT)
				.replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
				.replace("%", LIKE_ESCAPE + "%")
				.replace("_", LIKE_ESCAPE + "_");
		return "%" + escaped + "%";
	}

	/** Attaches authors, genres and categories to the rows using one query per relation. */
	private List<Book> assemble(List<BookRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = rows.stream().map(BookRow::id).toList();

		Map<UUID, List<AuthorSummary>> authors = group(jdbc.sql(
				"SELECT ba.book_id, a.id, a.name FROM book_authors ba JOIN authors a ON a.id = ba.author_id"
						+ " WHERE ba.book_id IN (:ids) ORDER BY ba.sort_order, a.name")
				.param("ids", ids)
				.query((rs, n) -> new Link<AuthorSummary>(rs.getObject("book_id", UUID.class),
						new AuthorSummary(rs.getObject("id", UUID.class), rs.getString("name"))))
				.list());
		Map<UUID, List<String>> genres = group(jdbc.sql(
				"SELECT book_id, genre FROM book_genres WHERE book_id IN (:ids) ORDER BY sort_order")
				.param("ids", ids)
				.query((rs, n) -> new Link<String>(rs.getObject("book_id", UUID.class), rs.getString("genre")))
				.list());
		Map<UUID, List<Category>> categories = group(jdbc.sql(
				"SELECT bc.book_id, c.id, c.name FROM book_categories bc JOIN categories c"
						+ " ON c.id = bc.category_id WHERE bc.book_id IN (:ids) ORDER BY c.sort_order")
				.param("ids", ids)
				.query((rs, n) -> new Link<Category>(rs.getObject("book_id", UUID.class),
						new Category(rs.getObject("id", UUID.class), rs.getString("name"))))
				.list());

		return rows.stream().map(r -> new Book(
				r.id(), r.title(), authors.getOrDefault(r.id(), List.of()), r.shortDescription(),
				r.coverImageUrl(), r.format(), genres.getOrDefault(r.id(), List.of()), r.price(),
				estimatedDelivery(r.format()), r.description(), categories.getOrDefault(r.id(), List.of()),
				r.language(), r.isbn(), r.publisher(), r.publishedDate(), r.pageCount(), r.inStock(),
				r.rating())).toList();
	}

	/** eBooks are delivered instantly; physical books get a rolling estimate. */
	private static LocalDate estimatedDelivery(BookFormat format) {
		return format == BookFormat.EBOOK ? null : LocalDate.now().plusDays(DELIVERY_DAYS);
	}

	private static <T> Map<UUID, List<T>> group(List<Link<T>> links) {
		Map<UUID, List<T>> grouped = new HashMap<>();
		for (Link<T> link : links) {
			grouped.computeIfAbsent(link.bookId(), k -> new ArrayList<>()).add(link.value());
		}
		return grouped;
	}

	private static Double toDouble(BigDecimal value) {
		return Objects.isNull(value) ? null : value.doubleValue();
	}
}
