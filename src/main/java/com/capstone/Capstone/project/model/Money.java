package com.capstone.Capstone.project.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

	public static final String INR = "INR";

	public static Money inr(long rupees) {
		return new Money(BigDecimal.valueOf(rupees).setScale(2), INR);
	}

	public static Money zero() {
		return inr(0);
	}

	public Money times(int quantity) {
		return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
	}

	public Money plus(Money other) {
		return new Money(amount.add(other.amount), currency);
	}
}
