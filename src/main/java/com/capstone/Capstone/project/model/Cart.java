package com.capstone.Capstone.project.model;

import java.util.List;

public record Cart(List<CartItem> items, int itemCount, Money subtotal) {
}
