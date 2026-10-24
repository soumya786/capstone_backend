package com.capstone.Capstone.project.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Book(
		UUID id,
		String title,
		List<AuthorSummary> authors,
		String shortDescription,
		String coverImageUrl,
		BookFormat format,
		List<String> genres,
		Money price,
		LocalDate estimatedDeliveryDate,
		String description,
		List<Category> categories,
		String language,
		String isbn,
		String publisher,
		LocalDate publishedDate,
		Integer pageCount,
		boolean inStock,
		Double rating) {

	public Book withEstimatedDeliveryDate(LocalDate date) {
		return new Book(id, title, authors, shortDescription, coverImageUrl, format, genres, price, date,
				description, categories, language, isbn, publisher, publishedDate, pageCount, inStock, rating);
	}

	public BookSummary toSummary() {
		return new BookSummary(id, title, authors, shortDescription, coverImageUrl, format, genres, price,
				estimatedDeliveryDate);
	}
}
