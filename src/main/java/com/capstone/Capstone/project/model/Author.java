package com.capstone.Capstone.project.model;

import java.util.UUID;

public record Author(UUID id, String name, String bio, String photoUrl, Boolean following) {

	public Author withFollowing(Boolean value) {
		return new Author(id, name, bio, photoUrl, value);
	}

	public AuthorSummary toSummary() {
		return new AuthorSummary(id, name);
	}
}
