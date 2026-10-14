INSERT INTO wallets (id, user_id, type, code, currency, balance_minor, status)
VALUES (gen_random_uuid(), NULL, 'SYSTEM', 'CASH_IN', 'INR', 0, 'ACTIVE'),
       (gen_random_uuid(), NULL, 'SYSTEM', 'MERCHANT', 'INR', 0, 'ACTIVE');
