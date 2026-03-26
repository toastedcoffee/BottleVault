-- BottleVault initial schema

CREATE TABLE users (
    id              UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    default_currency VARCHAR(3) DEFAULT 'USD',
    measurement_unit VARCHAR(5) DEFAULT 'ml',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE brands (
    id              UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,
    country         VARCHAR(100),
    website         VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE products (
    id              UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    brand_id        UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    barcode         VARCHAR(50) UNIQUE,
    type            VARCHAR(30) NOT NULL,
    subtype         VARCHAR(100),
    size            VARCHAR(50),
    abv             DECIMAL(5,2),
    description     TEXT,
    image_url       VARCHAR(500),
    is_user_created BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE bottles (
    id                  UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL DEFAULT 'UNOPENED',
    percentage_left     INTEGER DEFAULT 100 CHECK (percentage_left >= 0 AND percentage_left <= 100),
    purchase_date       DATE,
    purchase_location   VARCHAR(255),
    purchase_cost       DECIMAL(10,2),
    notes               TEXT,
    rating              INTEGER CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    storage_location    VARCHAR(255),
    image_path          VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_bottles_product_id ON bottles(product_id);
CREATE INDEX idx_bottles_user_id ON bottles(user_id);
CREATE INDEX idx_bottles_status ON bottles(status);
CREATE INDEX idx_bottles_user_status ON bottles(user_id, status);
CREATE INDEX idx_bottles_updated_at ON bottles(updated_at);
CREATE INDEX idx_products_brand_id ON products(brand_id);
CREATE INDEX idx_products_type ON products(type);
CREATE INDEX idx_products_name ON products(name);
