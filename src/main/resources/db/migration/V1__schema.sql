-- Book Worm e-bookstore schema.
-- Portable SQL: runs on PostgreSQL (production) and H2 in PostgreSQL mode (tests).

CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL
);

CREATE TABLE authors (
    id        UUID          PRIMARY KEY,
    name      VARCHAR(100)  NOT NULL UNIQUE,
    bio       VARCHAR(1000),
    photo_url VARCHAR(500)
);

CREATE TABLE books (
    id                UUID           PRIMARY KEY,
    title             VARCHAR(200)   NOT NULL,
    short_description VARCHAR(500)   NOT NULL,
    description       VARCHAR(4000),
    cover_image_url   VARCHAR(500),
    book_format       VARCHAR(20)    NOT NULL,
    price             NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'INR',
    language          VARCHAR(50)    NOT NULL,
    isbn              VARCHAR(20),
    publisher         VARCHAR(200),
    published_date    DATE,
    page_count        INTEGER,
    in_stock          BOOLEAN        NOT NULL DEFAULT TRUE,
    rating            NUMERIC(2, 1),
    catalog_rank      INTEGER        NOT NULL,
    CONSTRAINT ck_books_format CHECK (book_format IN ('PAPERBACK', 'HARDCOVER', 'EBOOK')),
    CONSTRAINT ck_books_price  CHECK (price >= 0),
    CONSTRAINT ck_books_pages  CHECK (page_count IS NULL OR page_count > 0),
    CONSTRAINT ck_books_rating CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);
CREATE INDEX idx_books_catalog_rank ON books (catalog_rank);

CREATE TABLE book_authors (
    book_id    UUID    NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    author_id  UUID    NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (book_id, author_id)
);
CREATE INDEX idx_book_authors_author ON book_authors (author_id);

CREATE TABLE book_categories (
    book_id     UUID NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, category_id)
);
CREATE INDEX idx_book_categories_category ON book_categories (category_id);

CREATE TABLE book_genres (
    book_id    UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    genre      VARCHAR(50) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (book_id, genre)
);

-- Landing page sections: Recommended for You, Bestsellers this Month, New Launches
CREATE TABLE home_section_books (
    section    VARCHAR(30) NOT NULL,
    book_id    UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    sort_order INTEGER     NOT NULL,
    PRIMARY KEY (section, book_id),
    CONSTRAINT ck_home_section CHECK (section IN ('RECOMMENDED', 'BESTSELLERS', 'NEW_LAUNCHES'))
);

-- Per-user data. user_id is the JWT "sub" claim; there is no local users table.
CREATE TABLE wishlist_items (
    user_id    VARCHAR(255)             NOT NULL,
    book_id    UUID                     NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, book_id)
);

CREATE TABLE followed_authors (
    user_id    VARCHAR(255)             NOT NULL,
    author_id  UUID                     NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, author_id)
);

CREATE TABLE cart_items (
    user_id  VARCHAR(255)             NOT NULL,
    book_id  UUID                     NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    quantity INTEGER                  NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT ck_cart_quantity CHECK (quantity >= 1 AND quantity <= 20)
);

CREATE TABLE orders (
    id                      UUID                     PRIMARY KEY,
    user_id                 VARCHAR(255)             NOT NULL,
    idempotency_key         VARCHAR(64)              NOT NULL,
    status                  VARCHAR(20)              NOT NULL,
    ship_full_name          VARCHAR(100)             NOT NULL,
    ship_line1              VARCHAR(200)             NOT NULL,
    ship_line2              VARCHAR(200),
    ship_city               VARCHAR(100)             NOT NULL,
    ship_state              VARCHAR(100)             NOT NULL,
    ship_postal_code        VARCHAR(10)              NOT NULL,
    ship_phone              VARCHAR(15)              NOT NULL,
    total_amount            NUMERIC(12, 2)           NOT NULL,
    currency                VARCHAR(3)               NOT NULL,
    estimated_delivery_date DATE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT uq_orders_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);

-- Title and prices are copied at purchase time so history survives catalogue changes.
CREATE TABLE order_items (
    order_id        UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_no         INTEGER        NOT NULL,
    book_id         UUID           NOT NULL REFERENCES books (id),
    title           VARCHAR(200)   NOT NULL,
    cover_image_url VARCHAR(500),
    quantity        INTEGER        NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    line_total      NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT ck_order_items_quantity CHECK (quantity >= 1)
);
CREATE INDEX idx_order_items_book ON order_items (book_id);

CREATE TABLE payments (
    id              UUID                     PRIMARY KEY,
    order_id        UUID                     NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(64)              NOT NULL,
    method          VARCHAR(20)              NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    amount          NUMERIC(12, 2)           NOT NULL,
    currency        VARCHAR(3)               NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_payments_method CHECK (method IN ('CARD', 'UPI', 'NET_BANKING', 'WALLET', 'CASH_ON_DELIVERY')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    CONSTRAINT uq_payments_idempotency UNIQUE (order_id, idempotency_key)
);
CREATE INDEX idx_payments_order ON payments (order_id);
