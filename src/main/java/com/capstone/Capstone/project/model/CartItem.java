package com.capstone.Capstone.project.model;

public record CartItem(BookSummary book, int quantity, Money lineTotal) {
}
