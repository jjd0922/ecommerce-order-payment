INSERT INTO product (id, name, price, selling)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'keyboard', 1000.00, TRUE),
    ('00000000-0000-0000-0000-000000000102', 'mouse', 500.00, TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    selling = VALUES(selling);

INSERT INTO inventory (product_id, available_quantity, held_quantity)
VALUES
    ('00000000-0000-0000-0000-000000000101', 100, 0),
    ('00000000-0000-0000-0000-000000000102', 100, 0)
ON DUPLICATE KEY UPDATE
    available_quantity = VALUES(available_quantity),
    held_quantity = VALUES(held_quantity);
