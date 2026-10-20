#!/usr/bin/env python3
"""Export the TEQZO database to static JSON files for the website in ``site/`` (GitHub Pages).

GitHub Pages can only serve files, so the website does all searching and comparing in the browser. This script
writes what the browser needs:

    site/data/devices.json      one compact row per phone (key specifications, prices, year, data source)
    site/data/prices.json       launch prices per country, from table device_price_v1 (if it exists)
    site/data/specs/NN.json     the full specification rows, split into 128 files; the browser loads one file
                                only when a details page is opened

Same data as the Java console application: it reads device_core_v4 JOIN device_extras_v1 JOIN brand and
device_specification. Nothing is converted or invented; a missing value is exported as null.

Usage
-----
    export DB_HOST=... DB_PORT=... DB_NAME=... DB_USERNAME=... DB_PASSWORD=...
    python database/export_site_data.py                    # writes into site/data
    python database/export_site_data.py --sqlite my.db     # verification / demo data from a SQLite copy
    python database/export_site_data.py --out some/dir

Only phones with specification data are exported (the original GSMArena data has ~680 name-only entries).
"""
import argparse
import datetime
import json
import os
import sqlite3
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curated  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "site", "data")
SOURCES = {None: 0, "FLIPKART_2026": 1, "MOBILES_2025": 2}  # code 3 = curated big-brand layer (curated.py)
SPEC_BUCKETS = 128
MAX_SPEC_VALUE = 600

FIELDS = ["id", "b", "n", "y", "ly", "s", "pi", "pu", "pe", "ram", "sto", "bat", "wc", "wl", "ds", "rr", "os",
          "cpu", "mc", "sc", "w", "g5", "nfc", "ip", "ch", "dt", "cu"]

QUERY = """
SELECT c.id, COALESCE(x.brand_name, b.name), c.name, x.announced_year, x.listed_year, x.data_source,
       x.price_inr, x.price_usd, x.price_eur, c.ram_gb, c.storage_gb, c.battery_mah, c.wired_charging_w,
       x.wireless_charging_status, c.display_size_inches, c.refresh_rate_hz, c.os, c.chipset,
       c.main_camera_mp, c.selfie_camera_mp, c.weight_g, c.has_5g, x.nfc_supported, c.ip_rating, x.charging_raw,
       c.display_type
FROM device_core_v4 c
JOIN device_extras_v1 x ON x.device_id = c.id
LEFT JOIN brand b ON b.id = c.brand_id
WHERE x.has_spec_data = 1
ORDER BY c.id
"""


def num(v):
    """Plain JSON number (int when whole) or None."""
    if v is None:
        return None
    v = float(v)
    return int(v) if v == int(v) else round(v, 2)


def flag(v):
    return None if v is None else (1 if v else 0)


def row_for(r):
    (device_id, brand, name, year, listed, source, p_inr, p_usd, p_eur, ram, storage, battery, wired, wireless_status,
     display, refresh, os_name, chipset, main, selfie, weight, has5g, nfc, ip, charging, dtype) = r
    code = SOURCES.get(source, 9)
    # A device from a smaller dataset that does not say "5G" is UNKNOWN, not "no 5G" (same rule as the Java app).
    five_g = 1 if has5g else (None if code else 0)
    wl = {"YES": 1, "CONDITIONAL": 2}.get(wireless_status)
    return [device_id, brand, name, year, listed, code, num(p_inr), num(p_usd), num(p_eur), num(ram), num(storage),
            num(battery), num(wired), wl, num(display), num(refresh), os_name, chipset, num(main), num(selfie),
            num(weight), five_g, flag(nfc), ip, (charging or None), dtype, 0]


def connect(sqlite_file):
    if sqlite_file:
        return sqlite3.connect(sqlite_file), "?"
    try:
        import mysql.connector
    except ImportError:
        sys.exit("mysql-connector-python is required: pip install mysql-connector-python")
    password = os.environ.get("DB_PASSWORD")
    if not password:
        sys.exit("DB_PASSWORD is not set. Export the variables from .env.example first.")
    conn = mysql.connector.connect(
        host=os.environ.get("DB_HOST", "localhost"), port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USERNAME", "root"), password=password,
        database=os.environ.get("DB_NAME", "tech_finder"))
    return conn, "%s"


def table_exists(conn, name):
    cur = conn.cursor()
    try:
        cur.execute(f"SELECT 1 FROM {name} LIMIT 1")
        cur.fetchall()
        return True
    except Exception:  # noqa: BLE001 - any failure means "not there"
        return False
    finally:
        cur.close()


def bucket(device_id):
    return zlib.crc32(device_id.encode("utf-8")) % SPEC_BUCKETS


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def export(conn, out_dir, curated_csv=curated.DEFAULT_CSV):
    cur = conn.cursor()
    cur.execute(QUERY)
    raw = cur.fetchall()
    rows = [row_for(r) for r in raw]
    cur_info = {}
    if curated_csv and os.path.exists(curated_csv):
        records, _dropped = curated.load(curated_csv)
        added, cur_info = curated.apply(rows, FIELDS, records)
        rows += added
        rows.sort(key=lambda r: r[0])
    ids = {r[0] for r in rows}

    prices = {}
    if table_exists(conn, "device_price_v1"):
        cur.execute("SELECT device_id, country, currency, variant, amount FROM device_price_v1 "
                    "ORDER BY device_id, country, amount")
        for device_id, country, currency, variant, amount in cur.fetchall():
            if device_id in ids:
                prices.setdefault(device_id, []).append([country, currency, variant, num(amount)])

    specs = {}
    cur.execute("SELECT device_id, spec_category, specification, spec_value FROM device_specification ORDER BY spec_id")
    for device_id, category, spec, value in cur.fetchall():
        if device_id in ids:
            text = (value or "").strip()
            specs.setdefault(bucket(device_id), {}).setdefault(device_id, []).append(
                [category or "", spec or "", text[:MAX_SPEC_VALUE]])
    cur.close()
    by_id = {r[0]: r for r in rows}
    for device_id, i in cur_info.items():
        lines = curated.spec_lines(i, by_id[device_id], FIELDS)
        if i["added"]:
            lines.insert(0, ["Launch", "Announced", str(by_id[device_id][FIELDS.index("y")] or "Not listed")])
        specs.setdefault(bucket(device_id), {}).setdefault(device_id, []).extend(lines)

    counts = {}
    for r in rows:
        counts[r[5]] = counts.get(r[5], 0) + 1
    meta = {"generated": datetime.date.today().isoformat(), "devices": len(rows),
            "bySource": {str(k): v for k, v in sorted(counts.items())}, "specBuckets": SPEC_BUCKETS}
    write_json(os.path.join(out_dir, "devices.json"), {"meta": meta, "fields": FIELDS, "rows": rows})
    write_json(os.path.join(out_dir, "prices.json"), prices)
    write_json(os.path.join(out_dir, "curated.json"), {k: [v["url"], v["filled"], 1 if v["added"] else 0] for k, v in cur_info.items()})
    spec_dir = os.path.join(out_dir, "specs")
    if os.path.isdir(spec_dir):
        for name in os.listdir(spec_dir):
            if name.endswith(".json"):
                os.remove(os.path.join(spec_dir, name))
    for i in range(SPEC_BUCKETS):
        write_json(os.path.join(spec_dir, f"{i:02x}.json"), specs.get(i, {}))
    return meta, len(prices)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sqlite", metavar="FILE", help="read a SQLite copy instead of MySQL")
    ap.add_argument("--no-curated", action="store_true", help="skip database/data/curated_phones.csv")
    ap.add_argument("--out", default=DEFAULT_OUT, help="output folder (default: site/data)")
    args = ap.parse_args()
    conn, _ = connect(args.sqlite)
    meta, with_prices = export(conn, args.out, None if args.no_curated else curated.DEFAULT_CSV)
    conn.close()
    size = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(args.out) for f in fs)
    print(f"Exported {meta['devices']:,} devices ({with_prices:,} with launch prices) to {os.path.abspath(args.out)}")
    print(f"  by source: {meta['bySource']}   total size: {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
