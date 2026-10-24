package com.capstone.Capstone.project.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(@NotNull @Valid Address shippingAddress) {
}
