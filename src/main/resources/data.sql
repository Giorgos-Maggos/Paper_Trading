-- Seed default user trader1 only if not already present
INSERT INTO users (id, username, email, created_at)
SELECT 1, 'trader1', 'trader1@example.com', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 1);

-- Seed AI bot user
INSERT INTO users (id, username, email, created_at)
SELECT 2, 'ai_quant_bot', 'quant-bot@tradesim.internal', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2);

-- Seed default portfolio 1 only if not already present
INSERT INTO portfolio (id, user_id, name, cash_balance, created_at)
SELECT 1, 1, 'Core Growth Portfolio', 100000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM portfolio WHERE id = 1);

-- Seed AI Quant Bot portfolio
INSERT INTO portfolio (id, user_id, name, cash_balance, created_at)
SELECT 2, 2, '🤖 AI Quant Strategy Fund', 100000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM portfolio WHERE id = 2);

-- Advance auto-increment sequences past pre-seeded IDs
ALTER TABLE users ALTER COLUMN id RESTART WITH 10;
ALTER TABLE portfolio ALTER COLUMN id RESTART WITH 10;
