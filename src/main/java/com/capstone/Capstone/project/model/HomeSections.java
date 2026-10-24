package com.capstone.Capstone.project.model;

import java.util.List;

public record HomeSections(
		List<BookSummary> recommendedForYou,
		List<BookSummary> bestsellersThisMonth,
		List<BookSummary> newLaunches) {
}
