-- Migration: Add selling_price column to products table
-- This allows products to have a separate selling price from the base price

-- Add selling_price column to products table in public schema (template)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS selling_price DECIMAL(10, 2);

-- Add comment explaining the column
COMMENT ON COLUMN products.selling_price IS 'Optional selling price, may differ from base price for promotions or special pricing';
