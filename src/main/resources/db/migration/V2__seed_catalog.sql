-- Initial catalogue taken from the wireframes. Ids are fixed so they are identical in every environment.
--   categories  c0000000-0000-0000-0000-0000000000NN
--   authors     a0000000-0000-0000-0000-0000000000NN
--   books       b0000000-0000-0000-0000-0000000000NN

INSERT INTO categories (id, name, sort_order) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Romance', 1),
    ('c0000000-0000-0000-0000-000000000002', 'Mystery', 2),
    ('c0000000-0000-0000-0000-000000000003', 'Science Fiction', 3),
    ('c0000000-0000-0000-0000-000000000004', 'Fantasy', 4),
    ('c0000000-0000-0000-0000-000000000005', 'Historical', 5),
    ('c0000000-0000-0000-0000-000000000006', 'Biography', 6),
    ('c0000000-0000-0000-0000-000000000007', 'Self-help', 7),
    ('c0000000-0000-0000-0000-000000000008', 'Memoir', 8),
    ('c0000000-0000-0000-0000-000000000009', 'Travel', 9),
    ('c0000000-0000-0000-0000-000000000010', 'Cooking', 10),
    ('c0000000-0000-0000-0000-000000000011', 'Children''s', 11),
    ('c0000000-0000-0000-0000-000000000012', 'Young Adult', 12),
    ('c0000000-0000-0000-0000-000000000013', 'Comics & Graphic Novels', 13),
    ('c0000000-0000-0000-0000-000000000014', 'Poetry', 14),
    ('c0000000-0000-0000-0000-000000000015', 'Drama', 15),
    ('c0000000-0000-0000-0000-000000000016', 'Science', 16),
    ('c0000000-0000-0000-0000-000000000017', 'Philosophy', 17),
    ('c0000000-0000-0000-0000-000000000018', 'Religion', 18),
    ('c0000000-0000-0000-0000-000000000019', 'Language Learning', 19);

INSERT INTO authors (id, name, bio) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Arjun Patel', 'Writes about focus, habits and deliberate work.'),
    ('a0000000-0000-0000-0000-000000000002', 'Raj Patel', 'Educator and writer on lifelong learning.'),
    ('a0000000-0000-0000-0000-000000000003', 'James Wright', 'Author of practical guides to goal setting.'),
    ('a0000000-0000-0000-0000-000000000004', 'James Adams', 'Writer of atmospheric thrillers.'),
    ('a0000000-0000-0000-0000-000000000005', 'Jessica Martin', 'Romance and drama novelist.'),
    ('a0000000-0000-0000-0000-000000000006', 'Laura Mitchell', 'Science fiction author.'),
    ('a0000000-0000-0000-0000-000000000007', 'Daniel Reed', 'Minimalism advocate and essayist.'),
    ('a0000000-0000-0000-0000-000000000008', 'Clara Nelson', 'Author of mystery and horror fiction.'),
    ('a0000000-0000-0000-0000-000000000009', 'Emily Parker', 'Children''s author.');

INSERT INTO books (id, title, short_description, book_format, price, language, published_date, page_count, rating, catalog_rank) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'The Art of Focus',
     'Practical guide to mastering focus & boosting productivity every day.', 'PAPERBACK', 399, 'English', DATE '2024-03-12', 240, 4.5, 1),
    ('b0000000-0000-0000-0000-000000000002', 'The Art of Learning',
     'Master the mindset and methods for effective lifelong learning.', 'PAPERBACK', 259, 'English', DATE '2023-08-01', 210, 4.3, 2),
    ('b0000000-0000-0000-0000-000000000003', 'The Path to Success',
     'A practical guide to achieving goals with clarity and confidence.', 'PAPERBACK', 359, 'English', DATE '2022-11-20', 280, 4.1, 3),
    ('b0000000-0000-0000-0000-000000000004', 'The Midnight Hour',
     'Haunting tale of a man''s journey & the shadows of a forgotten past.', 'PAPERBACK', 299, 'English', DATE '2024-06-05', 320, 4.7, 4),
    ('b0000000-0000-0000-0000-000000000005', 'Beneath the Stars',
     'A heartwarming tale, where two souls discover who you need.', 'HARDCOVER', 499, 'English', DATE '2024-02-14', 360, 4.6, 5),
    ('b0000000-0000-0000-0000-000000000006', 'The Final Frontier',
     'A mission to space secrets to change humanity forever.', 'PAPERBACK', 359, 'English', DATE '2024-01-09', 400, 4.4, 6),
    ('b0000000-0000-0000-0000-000000000007', 'Joy of Minimalism',
     'Declutter your life to uncover peace, clarity, and joy.', 'PAPERBACK', 149, 'English', DATE '2025-07-01', 180, 4.0, 7),
    ('b0000000-0000-0000-0000-000000000008', 'The Vanishing House',
     'A chilling mystery unfolds within a house that disappears.', 'EBOOK', 99, 'English', DATE '2025-07-10', 260, 4.2, 8),
    ('b0000000-0000-0000-0000-000000000009', 'The Lost Kitten',
     'A heartwarming tale of courage, friendship, and feline adventure.', 'HARDCOVER', 339, 'English', DATE '2025-07-15', 120, 4.8, 9);

UPDATE books SET description = short_description;

INSERT INTO book_authors (book_id, author_id, sort_order) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 0),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', 0),
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', 0),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004', 0),
    ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000005', 0),
    ('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000006', 0),
    ('b0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000007', 0),
    ('b0000000-0000-0000-0000-000000000008', 'a0000000-0000-0000-0000-000000000008', 0),
    ('b0000000-0000-0000-0000-000000000009', 'a0000000-0000-0000-0000-000000000009', 0);

-- Categories: Self-help = 07, Mystery = 02, Romance = 01, Drama = 15, Science Fiction = 03, Children's = 11
INSERT INTO book_categories (book_id, category_id) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000007'),
    ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000007'),
    ('b0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000007'),
    ('b0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002'),
    ('b0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000001'),
    ('b0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000015'),
    ('b0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003'),
    ('b0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000007'),
    ('b0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000002'),
    ('b0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000011');

INSERT INTO book_genres (book_id, genre, sort_order) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'Non-fiction', 0),
    ('b0000000-0000-0000-0000-000000000001', 'Self Help', 1),
    ('b0000000-0000-0000-0000-000000000002', 'Non-fiction', 0),
    ('b0000000-0000-0000-0000-000000000002', 'Self Help', 1),
    ('b0000000-0000-0000-0000-000000000003', 'Non-fiction', 0),
    ('b0000000-0000-0000-0000-000000000003', 'Self Help', 1),
    ('b0000000-0000-0000-0000-000000000004', 'Fiction', 0),
    ('b0000000-0000-0000-0000-000000000004', 'Thriller', 1),
    ('b0000000-0000-0000-0000-000000000004', 'Horror', 2),
    ('b0000000-0000-0000-0000-000000000005', 'Fiction', 0),
    ('b0000000-0000-0000-0000-000000000005', 'Love', 1),
    ('b0000000-0000-0000-0000-000000000005', 'Drama', 2),
    ('b0000000-0000-0000-0000-000000000006', 'Fiction', 0),
    ('b0000000-0000-0000-0000-000000000006', 'Thriller', 1),
    ('b0000000-0000-0000-0000-000000000007', 'Non-fiction', 0),
    ('b0000000-0000-0000-0000-000000000007', 'Self Help', 1),
    ('b0000000-0000-0000-0000-000000000008', 'Fiction', 0),
    ('b0000000-0000-0000-0000-000000000008', 'Horror', 1),
    ('b0000000-0000-0000-0000-000000000009', 'Fiction', 0),
    ('b0000000-0000-0000-0000-000000000009', 'Children', 1);

INSERT INTO home_section_books (section, book_id, sort_order) VALUES
    ('RECOMMENDED',  'b0000000-0000-0000-0000-000000000001', 1),
    ('RECOMMENDED',  'b0000000-0000-0000-0000-000000000002', 2),
    ('RECOMMENDED',  'b0000000-0000-0000-0000-000000000003', 3),
    ('BESTSELLERS',  'b0000000-0000-0000-0000-000000000004', 1),
    ('BESTSELLERS',  'b0000000-0000-0000-0000-000000000005', 2),
    ('BESTSELLERS',  'b0000000-0000-0000-0000-000000000006', 3),
    ('NEW_LAUNCHES', 'b0000000-0000-0000-0000-000000000007', 1),
    ('NEW_LAUNCHES', 'b0000000-0000-0000-0000-000000000008', 2),
    ('NEW_LAUNCHES', 'b0000000-0000-0000-0000-000000000009', 3);
