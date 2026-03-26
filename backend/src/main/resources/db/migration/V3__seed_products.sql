-- Seed initial product catalog
-- References brands by name since UUIDs are auto-generated

INSERT INTO products (brand_id, name, barcode, type, subtype, size, abv, description) VALUES
    ((SELECT id FROM brands WHERE name = 'Jack Daniel''s'), 'Old No. 7 Tennessee Whiskey', '082184000427', 'WHISKEY', 'Tennessee Whiskey', '750ml', 40.0, 'Mellowed drop by drop through 10-feet of sugar maple charcoal, then matured in handcrafted barrels.'),
    ((SELECT id FROM brands WHERE name = 'Jack Daniel''s'), 'Single Barrel Select', '082184125427', 'WHISKEY', 'Tennessee Whiskey', '750ml', 45.0, 'Hand-selected from the upper reaches of the barrelhouse.'),
    ((SELECT id FROM brands WHERE name = 'Jameson'), 'Jameson Irish Whiskey', '080432400012', 'WHISKEY', 'Irish Whiskey', '750ml', 40.0, 'Triple distilled and aged in a combination of ex-bourbon and ex-sherry casks.'),
    ((SELECT id FROM brands WHERE name = 'Jameson'), 'Black Barrel', '080432400119', 'WHISKEY', 'Irish Whiskey', '750ml', 40.0, 'Aged in double charred bourbon barrels for extra richness.'),
    ((SELECT id FROM brands WHERE name = 'Johnnie Walker'), 'Black Label', '088004015756', 'SCOTCH', 'Blended Scotch', '750ml', 40.0, 'An uncompromising blend of grain and malt whiskies from across Scotland.'),
    ((SELECT id FROM brands WHERE name = 'Johnnie Walker'), 'Blue Label', '088004015863', 'SCOTCH', 'Blended Scotch', '750ml', 40.0, 'An unrivalled masterpiece, created from some of Scotland''s rarest whiskies.'),
    ((SELECT id FROM brands WHERE name = 'Glenfiddich'), '12 Year Old', '083664868254', 'SCOTCH', 'Single Malt Scotch', '750ml', 40.0, 'Fresh and fruity with a hint of pear. Beautifully balanced.'),
    ((SELECT id FROM brands WHERE name = 'Glenfiddich'), '18 Year Old', '083664868308', 'SCOTCH', 'Single Malt Scotch', '750ml', 40.0, 'Rich and complex with notes of baked apple and cinnamon.'),
    ((SELECT id FROM brands WHERE name = 'The Macallan'), '12 Year Old Sherry Oak', '087236001209', 'SCOTCH', 'Single Malt Scotch', '750ml', 40.0, 'Matured exclusively in hand-picked sherry seasoned oak casks.'),
    ((SELECT id FROM brands WHERE name = 'Crown Royal'), 'Crown Royal Canadian Whisky', '087000002685', 'WHISKEY', 'Canadian Whisky', '750ml', 40.0, 'Blended from 50 of the finest whiskies and aged in oak barrels.'),
    ((SELECT id FROM brands WHERE name = 'Grey Goose'), 'Grey Goose Vodka', '080480000059', 'VODKA', NULL, '750ml', 40.0, 'Made from French wheat and limestone-filtered spring water.'),
    ((SELECT id FROM brands WHERE name = 'Tito''s'), 'Handmade Vodka', '619947000002', 'VODKA', NULL, '750ml', 40.0, 'Crafted in small batches using old-fashioned pot stills.'),
    ((SELECT id FROM brands WHERE name = 'Belvedere'), 'Belvedere Vodka', '081256121008', 'VODKA', NULL, '750ml', 40.0, 'Made from 100% Polish Dankowskie Gold Rye.'),
    ((SELECT id FROM brands WHERE name = 'Tanqueray'), 'London Dry Gin', '088110002008', 'GIN', 'London Dry', '750ml', 47.3, 'A perfect balance of four classic gin botanicals.'),
    ((SELECT id FROM brands WHERE name = 'Bombay Sapphire'), 'Bombay Sapphire Gin', '080480300470', 'GIN', 'London Dry', '750ml', 47.0, 'Infused with 10 hand-selected botanicals from exotic locations.'),
    ((SELECT id FROM brands WHERE name = 'Bacardi'), 'Superior White Rum', '080480000516', 'RUM', 'White Rum', '750ml', 40.0, 'Smooth and light with subtle notes of almond and vanilla.'),
    ((SELECT id FROM brands WHERE name = 'Captain Morgan'), 'Original Spiced Rum', '087000002302', 'RUM', 'Spiced Rum', '750ml', 35.0, 'A blend of Caribbean rums with vanilla and natural flavors.'),
    ((SELECT id FROM brands WHERE name = 'Patrón'), 'Silver Tequila', '721733001019', 'TEQUILA', 'Blanco', '750ml', 40.0, 'Crystal clear, smooth and fresh with citrus and sweet agave.'),
    ((SELECT id FROM brands WHERE name = 'Don Julio'), 'Blanco Tequila', '087116002302', 'TEQUILA', 'Blanco', '750ml', 40.0, 'Double distilled and unaged, crisp agave flavor.'),
    ((SELECT id FROM brands WHERE name = 'Hennessy'), 'Very Special Cognac', '088320102715', 'COGNAC', 'VS', '750ml', 40.0, 'A blend of over 40 eaux-de-vie with notes of fruit and toasted oak.');
