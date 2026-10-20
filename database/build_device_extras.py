#!/usr/bin/env python3
"""Build ``device_extras_v1``: parsed, query-friendly values derived from the ORIGINAL
GSMArena tables (brand, device, device_specification).

* Original tables and ``device_core_v4`` are only read, never modified.
* Only ``device_extras_v1`` is dropped and recreated, so the script can be re-run.
* Every value is read from the source text. Nothing is estimated or converted.

Usage (MySQL, settings from environment variables, see .env.example):
    python build_device_extras.py

Dry run against a SQLite copy of the data (no MySQL needed):
    python build_device_extras.py --sqlite path/to/gsmarena.db
"""
import argparse
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extras_parsing import (compact_search_text, parse_bluetooth_version, parse_charging, parse_cpu,
                            parse_nfc, parse_price, parse_resolution, parse_status_code,
                            parse_usb_type_c, parse_wifi_generation, parse_year)

TABLE = "device_extras_v1"
MOBILES_SOURCE = "MOBILES_2025"  # data_source of devices added by import_mobiles2025.py (ids start "m25-")
FLIPKART_SOURCE = "FLIPKART_2026"  # data_source of devices added by import_flipkart.py; NULL = GSMArena
BATCH = 1000

COLUMNS = [
    "device_id", "brand_name", "search_compact", "has_spec_data",
    "announced_year", "listed_year", "data_source", "status_code", "status_raw",
    "charging_raw", "wireless_charging_w", "wireless_charging_status",
    "reverse_charging_status",
    "price_raw", "price_inr", "price_eur", "price_usd",
    "cpu_cores", "cpu_max_ghz", "resolution_short_px", "resolution_long_px",
    "nfc_supported", "wifi_generation", "bluetooth_version", "usb_type_c",
]

DDL_MYSQL = f"""
CREATE TABLE {TABLE} (
    device_id VARCHAR(255) {{collation}} NOT NULL PRIMARY KEY,
    brand_name VARCHAR(255),
    search_compact VARCHAR(700) NOT NULL,
    has_spec_data TINYINT(1) NOT NULL,
    announced_year SMALLINT,
    listed_year SMALLINT,
    data_source VARCHAR(20),
    status_code VARCHAR(20),
    status_raw VARCHAR(500),
    charging_raw TEXT,
    wireless_charging_w DECIMAL(10,2),
    wireless_charging_status VARCHAR(12),
    reverse_charging_status VARCHAR(12),
    price_raw TEXT,
    price_inr DECIMAL(14,2),
    price_eur DECIMAL(14,2),
    price_usd DECIMAL(14,2),
    cpu_cores SMALLINT,
    cpu_max_ghz DECIMAL(5,2),
    resolution_short_px INT,
    resolution_long_px INT,
    nfc_supported TINYINT(1),
    wifi_generation TINYINT,
    bluetooth_version DECIMAL(3,1),
    usb_type_c TINYINT(1),
    INDEX idx_extras_year (announced_year),
    INDEX idx_extras_status (status_code),
    INDEX idx_extras_price_inr (price_inr),
    INDEX idx_extras_price_eur (price_eur),
    INDEX idx_extras_wireless (wireless_charging_status)
) ENGINE=InnoDB
"""


def mysql_ddl(cur):
    """Give device_id the same character set / collation as device.id, otherwise MySQL 8 refuses to join the
    two tables ("Illegal mix of collations"). No explicit collation is added if it cannot be read."""
    cur.execute("SELECT character_set_name, collation_name FROM information_schema.columns "
                "WHERE table_schema = DATABASE() AND table_name = 'device' AND column_name = 'id'")
    row = cur.fetchone()
    collation = f"CHARACTER SET {row[0]} COLLATE {row[1]}" if row and row[0] and row[1] else ""
    return DDL_MYSQL.replace("{collation}", collation)

DDL_SQLITE = f"""
CREATE TABLE {TABLE} (
    device_id TEXT PRIMARY KEY, brand_name TEXT, search_compact TEXT NOT NULL, has_spec_data INTEGER NOT NULL,
    announced_year INTEGER, listed_year INTEGER, data_source TEXT, status_code TEXT, status_raw TEXT, charging_raw TEXT,
    wireless_charging_w REAL, wireless_charging_status TEXT, reverse_charging_status TEXT,
    price_raw TEXT, price_inr REAL, price_eur REAL, price_usd REAL,
    cpu_cores INTEGER, cpu_max_ghz REAL, resolution_short_px INTEGER, resolution_long_px INTEGER,
    nfc_supported INTEGER, wifi_generation INTEGER, bluetooth_version REAL, usb_type_c INTEGER
)
"""


def first_value(rows, *names):
    wanted = {n.lower() for n in names}
    for name, value in rows:
        if (name or "").strip().lower() in wanted and value not in (None, ""):
            return value.strip()
    return None


def source_for(device_id):
    """NULL = GSMArena; otherwise the smaller dataset the device was added from (decided by the id prefix)."""
    device_id = str(device_id)
    if device_id.startswith("flipkart-"):
        return FLIPKART_SOURCE
    if device_id.startswith("m25-"):
        return MOBILES_SOURCE
    return None


def build_row(device, specs):
    """``specs`` maps category -> list of (specification, value)."""
    launch = specs.get("Launch", [])
    announced = first_value(launch, "Announced")
    status = first_value(launch, "Status")

    charging = first_value(specs.get("Battery", []), "Charging")
    ch = parse_charging(charging)

    price_raw = first_value(specs.get("Misc", []), "Price")
    price = parse_price(price_raw)

    cores, ghz = parse_cpu(first_value(specs.get("Platform", []), "CPU"))
    res_short, res_long = parse_resolution(first_value(specs.get("Display", []), "Resolution"))

    comms = specs.get("Comms", [])
    return {
        "device_id": device["id"],
        "brand_name": device["brand_name"],
        "search_compact": compact_search_text(device["brand_name"], device["name"]),
        "has_spec_data": 1 if specs else 0,
        "announced_year": parse_year(announced),
        "listed_year": parse_year(first_value(launch, "Listed")),
        "data_source": source_for(device["id"]),
        "status_code": parse_status_code(status),
        "status_raw": status,
        "charging_raw": charging,
        "wireless_charging_w": ch["wireless_w"],
        "wireless_charging_status": ch["wireless_status"],
        "reverse_charging_status": ch["reverse_status"],
        "price_raw": price_raw,
        "price_inr": price["inr"],
        "price_eur": price["eur"],
        "price_usd": price["usd"],
        "cpu_cores": cores,
        "cpu_max_ghz": ghz,
        "resolution_short_px": res_short,
        "resolution_long_px": res_long,
        "nfc_supported": parse_nfc(first_value(comms, "NFC")),
        "wifi_generation": parse_wifi_generation(first_value(comms, "WLAN", "Wi-Fi", "WiFi")),
        "bluetooth_version": parse_bluetooth_version(first_value(comms, "Bluetooth")),
        "usb_type_c": parse_usb_type_c(first_value(comms, "USB")),
    }


def load_source(cur):
    cur.execute("SELECT d.id, d.name, b.name FROM device d LEFT JOIN brand b ON b.id = d.brand_id ORDER BY d.id")
    devices = [{"id": r[0], "name": r[1], "brand_name": r[2]} for r in cur.fetchall()]
    cur.execute("SELECT device_id, spec_category, specification, spec_value FROM device_specification "
                "WHERE spec_category IN ('Launch','Battery','Misc','Platform','Display','Comms') "
                "ORDER BY device_id, spec_id")
    specs = {}
    for device_id, category, name, value in cur.fetchall():
        specs.setdefault(device_id, {}).setdefault(category, []).append((name or "", value or ""))
    cur.execute("SELECT DISTINCT device_id FROM device_specification")
    with_any_spec = {r[0] for r in cur.fetchall()}
    return devices, specs, with_any_spec


def run(conn, placeholder, ddl):
    cur = conn.cursor()
    if callable(ddl):
        ddl = ddl(cur)
    devices, specs, with_any_spec = load_source(cur)
    cur.execute(f"DROP TABLE IF EXISTS {TABLE}")
    cur.execute(ddl)

    insert = (f"INSERT INTO {TABLE} ({', '.join(COLUMNS)}) VALUES ({', '.join([placeholder] * len(COLUMNS))})")
    batch = []
    for device in devices:
        row = build_row(device, specs.get(device["id"], {}))
        row["has_spec_data"] = 1 if device["id"] in with_any_spec else 0
        batch.append(tuple(row[c] for c in COLUMNS))
        if len(batch) >= BATCH:
            cur.executemany(insert, batch)
            batch.clear()
    if batch:
        cur.executemany(insert, batch)
    conn.commit()
    report(cur)
    cur.close()


def report(cur):
    cur.execute(f"SELECT COUNT(*) FROM {TABLE}")
    print(f"{TABLE}: {cur.fetchone()[0]:,} rows")
    for label, where in [
        ("devices with specification data", "has_spec_data = 1"),
        ("release year", "announced_year IS NOT NULL"),
        ("added from the Flipkart 2026 or Mobiles 2025 datasets", "data_source IS NOT NULL"),
        ("wireless charging stated (YES)", "wireless_charging_status = 'YES'"),
        ("wireless charging wattage stated", "wireless_charging_w IS NOT NULL"),
        ("price listed in INR", "price_inr IS NOT NULL"),
        ("price listed in EUR", "price_eur IS NOT NULL"),
        ("price listed in USD", "price_usd IS NOT NULL"),
        ("CPU cores/clock", "cpu_cores IS NOT NULL"),
        ("NFC stated Yes/No", "nfc_supported IS NOT NULL"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {TABLE} WHERE {where}")
        print(f"  {label}: {cur.fetchone()[0]:,}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sqlite", metavar="FILE", help="run against a SQLite copy instead of MySQL (verification)")
    args = parser.parse_args()

    if args.sqlite:
        conn = sqlite3.connect(args.sqlite)
        run(conn, "?", DDL_SQLITE)
        conn.close()
        return

    try:
        import mysql.connector
    except ImportError:
        sys.exit("mysql-connector-python is required: pip install mysql-connector-python")

    password = os.environ.get("DB_PASSWORD")
    if not password:
        sys.exit("DB_PASSWORD is not set. Export the variables from .env.example first.")
    conn = mysql.connector.connect(
        host=os.environ.get("DB_HOST", "localhost"),
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USERNAME", "root"),
        password=password,
        database=os.environ.get("DB_NAME", "tech_finder"),
    )
    run(conn, "%s", mysql_ddl)
    conn.close()


if __name__ == "__main__":
    main()
