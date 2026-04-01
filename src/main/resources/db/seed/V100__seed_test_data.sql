-- ============================================================================
-- TEST DATA SEEDER - Products for existing tenants
-- ============================================================================
-- This script seeds products for tenants that already exist.
-- Run this manually after creating tenants via the API.
-- ============================================================================

-- Function to seed products for a tenant
CREATE OR REPLACE FUNCTION seed_tenant_products(p_schema TEXT)
RETURNS VOID AS $$
DECLARE
    v_category_id TEXT;
    v_main_branch_id TEXT;
    v_product_data TEXT[][];
    i INT;
BEGIN
    -- Get main branch ID
    EXECUTE format('SELECT id FROM %I.branches WHERE is_main_branch = true LIMIT 1', p_schema) INTO v_main_branch_id;
    
    -- Get General category ID
    EXECUTE format('SELECT id FROM %I.categories WHERE name = ''General'' LIMIT 1', p_schema) INTO v_category_id;
    
    -- Products data based on tenant type
    IF p_schema = 'technova' THEN
        v_product_data := ARRAY[
            ARRAY['LAPTOP001', 'Laptop Dell Inspiron', '450.00', '599.99', '10', '15.6 inch laptop with Intel i5'],
            ARRAY['PHONE001', 'iPhone 14 Pro', '899.00', '1099.00', '15', 'Latest iPhone with 128GB'],
            ARRAY['TABLET001', 'iPad Air', '499.00', '649.00', '8', '10.9 inch tablet with M1 chip'],
            ARRAY['MOUSE001', 'Wireless Mouse Logitech', '15.00', '29.99', '50', 'Ergonomic wireless mouse'],
            ARRAY['KEYBOARD001', 'Mechanical Keyboard', '45.00', '79.99', '25', 'RGB mechanical keyboard'],
            ARRAY['MONITOR001', '27-inch Monitor 4K', '280.00', '399.99', '12', '4K UHD monitor with HDR'],
            ARRAY['HEADPHONE001', 'Sony WH-1000XM4', '250.00', '349.99', '20', 'Noise cancelling headphones'],
            ARRAY['CHARGER001', 'USB-C Charger 65W', '20.00', '34.99', '40', 'Fast charging USB-C adapter'],
            ARRAY['CABLE001', 'HDMI Cable 2m', '5.00', '12.99', '100', 'High-speed HDMI 2.1 cable'],
            ARRAY['WEBCAM001', 'Logitech Webcam HD', '35.00', '59.99', '30', '1080p HD webcam with mic'],
            ARRAY['SPEAKER001', 'Bluetooth Speaker JBL', '80.00', '129.99', '18', 'Portable waterproof speaker'],
            ARRAY['ROUTER001', 'WiFi 6 Router', '120.00', '179.99', '15', 'Dual-band gigabit router'],
            ARRAY['SSD001', 'Samsung SSD 1TB', '85.00', '129.99', '22', 'NVMe M.2 SSD 1TB'],
            ARRAY['RAM001', 'Corsair RAM 16GB', '45.00', '69.99', '35', 'DDR4 3200MHz RAM kit'],
            ARRAY['PRINTER001', 'HP Laser Printer', '150.00', '229.99', '8', 'Wireless monochrome printer'],
            ARRAY['INK001', 'Printer Ink Cartridge', '25.00', '39.99', '60', 'Black toner cartridge'],
            ARRAY['CASE001', 'Laptop Case 15.6"', '15.00', '29.99', '45', 'Padded laptop carrying case'],
            ARRAY['STAND001', 'Laptop Stand Aluminum', '20.00', '34.99', '28', 'Adjustable laptop stand'],
            ARRAY['HUB001', 'USB Hub 7-port', '18.00', '29.99', '33', 'Powered USB 3.0 hub'],
            ARRAY['BATTERY001', 'Power Bank 20000mAh', '25.00', '44.99', '40', 'Fast charging power bank']
        ];
    ELSIF p_schema = 'freshmart' THEN
        v_product_data := ARRAY[
            ARRAY['RICE001', 'Basmati Rice 5kg', '4.50', '7.99', '50', 'Premium long grain rice'],
            ARRAY['PASTA001', 'Spaghetti 500g', '0.80', '1.49', '100', 'Italian durum wheat pasta'],
            ARRAY['OIL001', 'Olive Oil 1L', '4.00', '6.99', '40', 'Extra virgin olive oil'],
            ARRAY['SUGAR001', 'White Sugar 1kg', '1.20', '1.99', '80', 'Refined white sugar'],
            ARRAY['FLOUR001', 'All Purpose Flour 2kg', '1.50', '2.49', '60', 'Wheat flour for baking'],
            ARRAY['MILK001', 'Whole Milk 1L', '0.90', '1.49', '100', 'Fresh whole milk'],
            ARRAY['EGGS001', 'Large Eggs 12-pack', '2.50', '3.99', '50', 'Free-range large eggs'],
            ARRAY['BREAD001', 'White Bread Sliced', '1.00', '1.79', '40', 'Fresh sliced white bread'],
            ARRAY['CHEESE001', 'Cheddar Cheese 400g', '3.00', '4.99', '35', 'Mature cheddar cheese'],
            ARRAY['YOGURT001', 'Greek Yogurt 500g', '1.80', '2.99', '45', 'Natural Greek yogurt'],
            ARRAY['CHICKEN001', 'Chicken Breast 1kg', '5.00', '8.99', '30', 'Fresh chicken breast fillets'],
            ARRAY['BEEF001', 'Ground Beef 500g', '4.00', '6.99', '25', 'Premium ground beef'],
            ARRAY['APPLE001', 'Red Apples 1kg', '1.50', '2.49', '60', 'Fresh red apples'],
            ARRAY['BANANA001', 'Bananas 1kg', '0.90', '1.49', '80', 'Fresh ripe bananas'],
            ARRAY['TOMATO001', 'Tomatoes 1kg', '1.20', '1.99', '50', 'Fresh vine tomatoes'],
            ARRAY['POTATO001', 'Potatoes 2kg', '1.00', '1.79', '70', 'White potatoes'],
            ARRAY['COFFEE001', 'Ground Coffee 250g', '3.50', '5.99', '40', 'Arabica ground coffee'],
            ARRAY['TEA001', 'Black Tea 100 bags', '2.00', '3.49', '55', 'Premium black tea bags'],
            ARRAY['JUICE001', 'Orange Juice 1L', '1.50', '2.49', '45', '100% pure orange juice'],
            ARRAY['WATER001', 'Mineral Water 6-pack', '1.80', '2.99', '100', 'Natural mineral water']
        ];
    ELSIF p_schema = 'stylehub' THEN
        v_product_data := ARRAY[
            ARRAY['TSHIRT001', 'Cotton T-Shirt White', '5.00', '9.99', '50', '100% cotton basic t-shirt'],
            ARRAY['JEANS001', 'Slim Fit Jeans Blue', '20.00', '34.99', '40', 'Classic slim fit denim jeans'],
            ARRAY['SHIRT001', 'Formal Shirt White', '15.00', '24.99', '35', 'Business formal cotton shirt'],
            ARRAY['DRESS001', 'Summer Floral Dress', '25.00', '42.99', '25', 'Light summer floral print dress'],
            ARRAY['JACKET001', 'Denim Jacket Blue', '30.00', '49.99', '20', 'Classic denim jacket'],
            ARRAY['SNEAKER001', 'White Sneakers', '25.00', '44.99', '30', 'Casual white sneakers'],
            ARRAY['BOOTS001', 'Leather Boots Brown', '45.00', '74.99', '20', 'Genuine leather boots'],
            ARRAY['SANDAL001', 'Summer Sandals', '12.00', '19.99', '40', 'Comfortable summer sandals'],
            ARRAY['BAG001', 'Leather Handbag', '35.00', '59.99', '25', 'Genuine leather handbag'],
            ARRAY['BELT001', 'Leather Belt Black', '10.00', '16.99', '45', 'Classic leather belt'],
            ARRAY['WALLET001', 'Leather Wallet', '12.00', '19.99', '35', 'Bifold genuine leather wallet'],
            ARRAY['SCARF001', 'Silk Scarf', '15.00', '24.99', '30', 'Elegant silk scarf'],
            ARRAY['HAT001', 'Baseball Cap', '8.00', '14.99', '50', 'Adjustable cotton cap'],
            ARRAY['SOCKS001', 'Crew Socks 3-pack', '5.00', '8.99', '80', 'Cotton crew socks pack'],
            ARRAY['UNDER001', 'Boxer Briefs 3-pack', '8.00', '14.99', '60', 'Cotton boxer briefs pack'],
            ARRAY['SHORTS001', 'Cargo Shorts Khaki', '15.00', '24.99', '35', 'Casual cargo shorts'],
            ARRAY['HOODIE001', 'Pullover Hoodie Gray', '20.00', '34.99', '30', 'Comfortable cotton hoodie'],
            ARRAY['COAT001', 'Winter Coat Black', '60.00', '99.99', '15', 'Warm winter padded coat'],
            ARRAY['SWEATER001', 'Wool Sweater Navy', '25.00', '42.99', '25', 'Soft wool knit sweater'],
            ARRAY['SUNG001', 'UV Sunglasses', '10.00', '19.99', '40', 'Polarized UV protection sunglasses']
        ];
    END IF;
    
    -- Insert products
    FOR i IN 1..array_length(v_product_data, 1) LOOP
        EXECUTE format('
            INSERT INTO %I.products (id, code, name, price, selling_price, stock, category_id, branch_id, unit_of_measure, allow_decimal, min_quantity, step_quantity, active, created_at, created_by)
            VALUES (gen_random_uuid(), %L, %L, %L, %L, %L, %L, %L, ''pcs'', false, 1, 1, true, NOW(), ''system'')
        ', p_schema, 
            v_product_data[i][1],
            v_product_data[i][2],
            v_product_data[i][3],
            v_product_data[i][4],
            v_product_data[i][5],
            v_category_id,
            v_main_branch_id
        );
    END LOOP;
    
    RAISE NOTICE 'Seeded % products for tenant %', array_length(v_product_data, 1), p_schema;
END;
$$ LANGUAGE plpgsql;

-- Run seeding for each tenant
-- Uncomment and run these after creating tenants:
-- SELECT seed_tenant_products('technova');
-- SELECT seed_tenant_products('freshmart');
-- SELECT seed_tenant_products('stylehub');

-- Clean up
DROP FUNCTION IF EXISTS seed_tenant_products(TEXT);
