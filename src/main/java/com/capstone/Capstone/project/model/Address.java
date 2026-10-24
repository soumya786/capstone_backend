package com.capstone.Capstone.project.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Address(
		@NotBlank @Size(max = 100) String fullName,
		@NotBlank @Size(max = 200) String line1,
		@Size(max = 200) String line2,
		@NotBlank @Size(max = 100) String city,
		@NotBlank @Size(max = 100) String state,
		@NotBlank @Pattern(regexp = "^[0-9]{6}$") String postalCode,
		@NotBlank @Pattern(regexp = "^[0-9]{10}$") String phone) {
}
