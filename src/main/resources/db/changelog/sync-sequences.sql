-- data.sql inserts explicit ids for customer/order, bypassing their identity sequences.
-- Advance the sequences to the current max id so the app's next generated id doesn't collide.
SELECT setval(pg_get_serial_sequence('customer', 'id'), (SELECT MAX(id) FROM customer));
SELECT setval(pg_get_serial_sequence('"order"', 'id'), (SELECT MAX(id) FROM "order"));
