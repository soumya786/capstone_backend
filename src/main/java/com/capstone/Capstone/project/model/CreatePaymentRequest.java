package com.capstone.Capstone.project.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Only a payment-provider token is accepted; raw card data must never reach this API. */
public record CreatePaymentRequest(@NotNull PaymentMethod method, @Size(max = 256) String providerToken) {
}
