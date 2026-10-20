-- Reference DDL for the one table this application ADDS to your database.
--
-- You normally do not run this file: `python database/build_device_extras.py` creates (and fills) the table
-- itself, copying the character set / collation of device.id so the tables can be joined on MySQL 8.
-- Nothing in the original tables or in device_core_v4 is modified.
--
-- Required existing tables: brand, device, device_specification, device_core_v4 (see docs/DATABASE.md).
-- Devices from the Flipkart 2026 dataset are ordinary rows of brand/device/device_specification with ids
-- starting "flipkart-" (see database/import_flipkart.py).

CREATE TABLE device_extras_v1 (
    device_id VARCHAR(255) NOT NULL PRIMARY KEY,     -- same value as device.id / device_core_v4.id
    brand_name VARCHAR(255),
    search_compact VARCHAR(700) NOT NULL,            -- lower-case letters+digits of "<brand> <name>"
    has_spec_data TINYINT(1) NOT NULL,               -- 0 for the entries that have no specification rows
    announced_year SMALLINT,
    listed_year SMALLINT,                            -- year a retail listing was seen (Flipkart import); NULL for GSMArena
    data_source VARCHAR(20),                         -- NULL = GSMArena, FLIPKART_2026 = added by import_flipkart.py
    status_code VARCHAR(20),                         -- AVAILABLE / DISCONTINUED / CANCELLED / UPCOMING / OTHER
    status_raw VARCHAR(500),
    charging_raw TEXT,                               -- untouched Battery > Charging text
    wireless_charging_w DECIMAL(10,2),               -- NULL = wattage not stated
    wireless_charging_status VARCHAR(12),            -- YES / CONDITIONAL (optional, market dependent) / NULL
    reverse_charging_status VARCHAR(12),
    price_raw TEXT,
    price_inr DECIMAL(14,2),                         -- only amounts literally listed; never converted
    price_eur DECIMAL(14,2),
    price_usd DECIMAL(14,2),
    cpu_cores SMALLINT,
    cpu_max_ghz DECIMAL(5,2),
    resolution_short_px INT,
    resolution_long_px INT,
    nfc_supported TINYINT(1),                        -- 1 = "Yes", 0 = "No", NULL = unknown / conditional
    wifi_generation TINYINT,
    bluetooth_version DECIMAL(3,1),
    usb_type_c TINYINT(1),
    INDEX idx_extras_year (announced_year),
    INDEX idx_extras_status (status_code),
    INDEX idx_extras_price_inr (price_inr),
    INDEX idx_extras_price_eur (price_eur),
    INDEX idx_extras_wireless (wireless_charging_status)
) ENGINE=InnoDB;


-- Launch prices per country, written by `python database/import_mobiles2025.py` (the application only reads it).
-- One row per device, country and RAM/storage variant, exactly as listed in the dataset (never converted).
-- Rows can belong to ANY device (GSMArena, Flipkart or Mobiles 2025). The table is optional: without it the
-- application simply shows no regional prices.
CREATE TABLE IF NOT EXISTS device_price_v1 (
    device_id VARCHAR(255) NOT NULL,     -- device.id
    country VARCHAR(40) NOT NULL,        -- Pakistan, India, China, USA, Dubai
    currency CHAR(3) NOT NULL,           -- PKR, INR, CNY, USD, AED
    variant VARCHAR(40) NOT NULL,        -- e.g. "8 GB RAM / 256 GB"; empty when the dataset names no variant
    amount DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (device_id, country, variant)
);
