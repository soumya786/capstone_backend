package com.capstone.Capstone.project;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BookstoreApiTests {

	private static final String ADDRESS = """
			{"fullName":"Asha Rao","line1":"12 MG Road","city":"Bengaluru","state":"Karnataka",
			 "postalCode":"560001","phone":"9876543210"}""";

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	private static RequestPostProcessor user(String id) {
		return jwt().jwt(j -> j.subject(id).claim("name", "Test User").claim("email", id + "@example.com"));
	}

	private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
		return json.readTree(r.andReturn().getResponse().getContentAsString());
	}

	private String firstBookId(String query) throws Exception {
		return body(mvc.perform(get("/books" + query))).get("content").get(0).get("id").asString();
	}

	// ---- public catalogue ------------------------------------------------------------------

	@Test
	void homeReturnsThreeSectionsWithoutAuthentication() throws Exception {
		mvc.perform(get("/catalog/home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendedForYou", hasSize(3)))
				.andExpect(jsonPath("$.bestsellersThisMonth", hasSize(3)))
				.andExpect(jsonPath("$.newLaunches", hasSize(3)))
				.andExpect(jsonPath("$.recommendedForYou[0].title", is("The Art of Focus")))
				.andExpect(jsonPath("$.recommendedForYou[0].price.currency", is("INR")));
	}

	@Test
	void categoriesAreListed() throws Exception {
		mvc.perform(get("/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(19)));
	}

	@Test
	void searchFiltersByFormatPriceAndSorts() throws Exception {
		mvc.perform(get("/books?format=EBOOK"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].title", is("The Vanishing House")))
				.andExpect(jsonPath("$.content[0].estimatedDeliveryDate").doesNotExist());

		mvc.perform(get("/books?minPrice=300&maxPrice=400&sort=price_asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].price.amount", is(339.0)))
				.andExpect(jsonPath("$.content[1].price.amount", is(359.0)));

		mvc.perform(get("/books?q=focus"))
				.andExpect(jsonPath("$.totalElements", is(1)));
	}

	@Test
	void searchFiltersByCategory() throws Exception {
		JsonNode categories = body(mvc.perform(get("/categories")));
		String mystery = null;
		for (JsonNode c : categories) {
			if (c.get("name").asString().equals("Mystery")) {
				mystery = c.get("id").asString();
			}
		}
		mvc.perform(get("/books?categoryId=" + mystery))
				.andExpect(jsonPath("$.totalElements", is(2)));
	}

	@Test
	void invalidSearchParametersAreRejectedWithGenericError() throws Exception {
		mvc.perform(get("/books?sort=bogus"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").exists())
				.andExpect(jsonPath("$.message", is("Invalid request")));
		mvc.perform(get("/books?size=1000")).andExpect(status().isBadRequest());
		mvc.perform(get("/books?minPrice=500&maxPrice=100")).andExpect(status().isBadRequest());
		mvc.perform(get("/books?format=VINYL")).andExpect(status().isBadRequest());
	}

	@Test
	void bookDetailAndRelated() throws Exception {
		String id = firstBookId("?q=midnight");
		mvc.perform(get("/books/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title", is("The Midnight Hour")))
				.andExpect(jsonPath("$.inStock", is(true)));
		mvc.perform(get("/books/" + id + "/related"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(org.hamcrest.Matchers.hasItem(id))));
	}

	@Test
	void unknownBookReturnsGenericNotFound() throws Exception {
		mvc.perform(get("/books/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void malformedIdIsABadRequestNotAServerError() throws Exception {
		mvc.perform(get("/books/not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void authorsCanBeBrowsed() throws Exception {
		mvc.perform(get("/authors?q=patel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content[0].following").doesNotExist());
		String authorId = body(mvc.perform(get("/authors?q=Arjun"))).get("content").get(0).get("id").asString();
		mvc.perform(get("/authors/" + authorId + "/books"))
				.andExpect(jsonPath("$.content[0].title", is("The Art of Focus")));
	}

	// ---- authentication --------------------------------------------------------------------

	@Test
	void protectedEndpointsRejectAnonymousCallers() throws Exception {
		for (String path : new String[] { "/cart", "/orders", "/me", "/me/wishlist", "/me/writers" }) {
			mvc.perform(get(path))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
		}
		mvc.perform(post("/cart/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void garbageBearerTokenIsRejected() throws Exception {
		mvc.perform(get("/cart").header("Authorization", "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void profileComesFromTokenClaims() throws Exception {
		mvc.perform(get("/me").with(user("profile-user")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is("profile-user")))
				.andExpect(jsonPath("$.email", is("profile-user@example.com")));
	}

	// ---- wishlist and writers --------------------------------------------------------------

	@Test
	void wishlistAddListRemove() throws Exception {
		String book = firstBookId("?q=lost");
		RequestPostProcessor u = user("wish-user");

		mvc.perform(put("/me/wishlist/" + book).with(u)).andExpect(status().isNoContent());
		mvc.perform(put("/me/wishlist/" + book).with(u)).andExpect(status().isNoContent());
		mvc.perform(get("/me/wishlist").with(u))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title", is("The Lost Kitten")));
		mvc.perform(get("/me/wishlist").with(user("someone-else"))).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(delete("/me/wishlist/" + book).with(u)).andExpect(status().isNoContent());
		mvc.perform(delete("/me/wishlist/" + book).with(u)).andExpect(status().isNotFound());
		mvc.perform(put("/me/wishlist/" + UUID.randomUUID()).with(u)).andExpect(status().isNotFound());
	}

	@Test
	void followingAnAuthorIsReflectedInAuthorViews() throws Exception {
		String authorId = body(mvc.perform(get("/authors?q=Daniel"))).get("content").get(0).get("id").asString();
		RequestPostProcessor u = user("follow-user");

		mvc.perform(put("/me/writers/" + authorId).with(u)).andExpect(status().isNoContent());
		mvc.perform(get("/me/writers").with(u))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].following", is(true)));
		mvc.perform(get("/authors/" + authorId).with(u)).andExpect(jsonPath("$.following", is(true)));
		mvc.perform(get("/authors/" + authorId).with(user("other"))).andExpect(jsonPath("$.following", is(false)));
		mvc.perform(delete("/me/writers/" + authorId).with(u)).andExpect(status().isNoContent());
		mvc.perform(delete("/me/writers/" + authorId).with(u)).andExpect(status().isNotFound());
	}

	// ---- cart ------------------------------------------------------------------------------

	@Test
	void cartAddUpdateRemove() throws Exception {
		String focus = firstBookId("?q=focus");
		RequestPostProcessor u = user("cart-user");

		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + focus + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itemCount", is(1)));
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + focus + "\",\"quantity\":2}"))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].quantity", is(3)))
				.andExpect(jsonPath("$.subtotal.amount", is(1197.0)));
		mvc.perform(patch("/cart/items/" + focus).with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"quantity\":1}"))
				.andExpect(jsonPath("$.subtotal.amount", is(399.0)));
		mvc.perform(delete("/cart/items/" + focus).with(u))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itemCount", is(0)));
		mvc.perform(delete("/cart/items/" + focus).with(u)).andExpect(status().isNotFound());
	}

	@Test
	void cartRejectsInvalidInput() throws Exception {
		RequestPostProcessor u = user("cart-invalid");
		String focus = firstBookId("?q=focus");
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + focus + "\",\"quantity\":0}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + focus + "\",\"quantity\":21}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void cartsAreIsolatedPerUser() throws Exception {
		String focus = firstBookId("?q=focus");
		mvc.perform(post("/cart/items").with(user("owner")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + focus + "\"}")).andExpect(status().isOk());
		mvc.perform(get("/cart").with(user("stranger"))).andExpect(jsonPath("$.itemCount", is(0)));
	}

	// ---- orders and payments ---------------------------------------------------------------

	private String addToCart(RequestPostProcessor u, String bookId, int qty) throws Exception {
		mvc.perform(post("/cart/items").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + bookId + "\",\"quantity\":" + qty + "}")).andExpect(status().isOk());
		return bookId;
	}

	private String placeOrder(RequestPostProcessor u, String key) throws Exception {
		var r = mvc.perform(post("/orders").with(u).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":" + ADDRESS + "}"))
				.andExpect(status().isCreated());
		return body(r).get("id").asString();
	}

	@Test
	void checkoutPaymentAndBuyAgainFlow() throws Exception {
		RequestPostProcessor u = user("flow-user");
		String focus = addToCart(u, firstBookId("?q=focus"), 2);

		String orderId = placeOrder(u, "order-key-0001");

		mvc.perform(get("/cart").with(u)).andExpect(jsonPath("$.itemCount", is(0)));
		mvc.perform(get("/orders/" + orderId).with(u))
				.andExpect(jsonPath("$.status", is("PENDING_PAYMENT")))
				.andExpect(jsonPath("$.total.amount", is(798.0)))
				.andExpect(jsonPath("$.items[0].quantity", is(2)))
				.andExpect(jsonPath("$.estimatedDeliveryDate").exists());

		// A retried order request with the same key must not create a second order.
		mvc.perform(get("/orders").with(u)).andExpect(jsonPath("$.totalElements", is(1)));

		mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "pay-key-00001")
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
				.andExpect(status().isBadRequest());

		String payment = "{\"method\":\"CARD\",\"providerToken\":\"tok_test_123\"}";
		var paid = mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "pay-key-00002")
				.contentType(MediaType.APPLICATION_JSON).content(payment))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("SUCCEEDED")))
				.andExpect(jsonPath("$.amount.amount", is(798.0)));
		String paymentId = body(paid).get("id").asString();

		mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "pay-key-00002")
				.contentType(MediaType.APPLICATION_JSON).content(payment))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(paymentId)));
		mvc.perform(get("/orders/" + orderId + "/payments").with(u)).andExpect(jsonPath("$", hasSize(1)));

		mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "pay-key-00003")
				.contentType(MediaType.APPLICATION_JSON).content(payment))
				.andExpect(status().isConflict());
		mvc.perform(get("/orders/" + orderId).with(u)).andExpect(jsonPath("$.status", is("PAID")));

		mvc.perform(get("/orders/buy-again").with(u))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id", is(focus)));
		mvc.perform(post("/orders/" + orderId + "/buy-again").with(u))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].quantity", is(2)));
	}

	@Test
	void sameIdempotencyKeyReturnsTheOriginalOrder() throws Exception {
		RequestPostProcessor u = user("idem-user");
		addToCart(u, firstBookId("?q=focus"), 1);
		String first = placeOrder(u, "idem-key-0001");
		// Cart is now empty, so only idempotency can explain a successful second call.
		String second = placeOrder(u, "idem-key-0001");
		org.junit.jupiter.api.Assertions.assertEquals(first, second);
	}

	@Test
	void cashOnDeliveryStaysPendingAndOrderStaysAwaitingPayment() throws Exception {
		RequestPostProcessor u = user("cod-user");
		addToCart(u, firstBookId("?q=lost"), 1);
		String orderId = placeOrder(u, "cod-order-001");
		mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "cod-pay-0001")
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH_ON_DELIVERY\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING")));
		mvc.perform(get("/orders/" + orderId).with(u)).andExpect(jsonPath("$.status", is("PENDING_PAYMENT")));
	}

	@Test
	void ordersCannotBeReadOrChangedByOtherUsers() throws Exception {
		RequestPostProcessor owner = user("order-owner");
		addToCart(owner, firstBookId("?q=focus"), 1);
		String orderId = placeOrder(owner, "owner-order-01");

		RequestPostProcessor attacker = user("attacker");
		mvc.perform(get("/orders/" + orderId).with(attacker)).andExpect(status().isNotFound());
		mvc.perform(post("/orders/" + orderId + "/cancel").with(attacker)).andExpect(status().isNotFound());
		mvc.perform(post("/orders/" + orderId + "/buy-again").with(attacker)).andExpect(status().isNotFound());
		mvc.perform(get("/orders/" + orderId + "/payments").with(attacker)).andExpect(status().isNotFound());
		mvc.perform(post("/orders/" + orderId + "/payments").with(attacker).header("Idempotency-Key", "attack-key-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"CARD\",\"providerToken\":\"tok\"}")).andExpect(status().isNotFound());
		mvc.perform(get("/orders").with(attacker)).andExpect(jsonPath("$.totalElements", is(0)));
	}

	@Test
	void cancelRules() throws Exception {
		RequestPostProcessor u = user("cancel-user");
		addToCart(u, firstBookId("?q=lost"), 1);
		String orderId = placeOrder(u, "cancel-order-1");
		mvc.perform(post("/orders/" + orderId + "/cancel").with(u))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CANCELLED")));
		mvc.perform(post("/orders/" + orderId + "/cancel").with(u)).andExpect(status().isConflict());
		mvc.perform(post("/orders/" + orderId + "/payments").with(u).header("Idempotency-Key", "cancel-pay-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"CARD\",\"providerToken\":\"tok\"}")).andExpect(status().isConflict());
		mvc.perform(get("/orders/buy-again").with(u)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void orderValidation() throws Exception {
		RequestPostProcessor u = user("order-invalid");

		// Empty cart
		mvc.perform(post("/orders").with(u).header("Idempotency-Key", "empty-cart-001")
				.contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":" + ADDRESS + "}"))
				.andExpect(status().isConflict());

		addToCart(u, firstBookId("?q=focus"), 1);
		// Missing idempotency header
		mvc.perform(post("/orders").with(u).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shippingAddress\":" + ADDRESS + "}")).andExpect(status().isBadRequest());
		// Too-short idempotency key
		mvc.perform(post("/orders").with(u).header("Idempotency-Key", "short")
				.contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":" + ADDRESS + "}"))
				.andExpect(status().isBadRequest());
		// Bad postal code
		mvc.perform(post("/orders").with(u).header("Idempotency-Key", "bad-address-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"shippingAddress\":" + ADDRESS.replace("560001", "12") + "}"))
				.andExpect(status().isBadRequest());
		// Missing address
		mvc.perform(post("/orders").with(u).header("Idempotency-Key", "no-address-001")
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void errorResponsesNeverLeakInternals() throws Exception {
		String response = mvc.perform(get("/books/not-a-uuid")).andReturn().getResponse().getContentAsString();
		org.junit.jupiter.api.Assertions.assertFalse(response.contains("Exception"), response);
		org.junit.jupiter.api.Assertions.assertFalse(response.contains("at com."), response);
		mvc.perform(get("/does-not-exist").with(user("x")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message", not(containsString("/does-not-exist"))));
	}
}
