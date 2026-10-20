#!/usr/bin/env python3
"""Add the "Mobiles Dataset 2025" CSV (launch prices in five countries, 2014-2025 models) to the TEQZO data.

What the dataset contains: company, model (storage size is usually part of the name), weight, RAM, front/back
camera, processor, battery, screen size, launch prices in Pakistan / India / China / USA / Dubai and the launch year.

How it is added (same method as database/import_flipkart.py)
-------------------------------------------------------------
1. The CSV is cleaned: exact duplicate rows removed, tablets/laptops dropped (screen outside the phone range),
   storage variants ("iPhone 16 128GB", "... 256GB") merged into ONE device per model.
2. Every model is compared with what the database already holds (the GSMArena rows AND the Flipkart rows):
      * already there    -> NOT added again (the existing entry is kept untouched); only its launch prices are
                            attached in the new table ``device_price_v1`` so the price comparison still works.
      * not there        -> added as ordinary rows of brand / device / device_specification (ids start with
                            ``m25-``), in GSMArena's "category / specification / value" text format. The two
                            existing build scripts then pick the new devices up without special cases.
3. ``device_price_v1`` (device_id, country, currency, variant, amount) holds every launch price of every variant.

Rules (nothing is invented)
---------------------------
* Only values present in the CSV are stored; anything else stays "Not listed". 5G is only set when the name says so.
* Prices are exactly as listed and never converted between currencies.
* Operating system is filled in only for Apple (iOS) and brands that sell only Android phones, and labelled
  "inferred from brand". Huawei, Sony, Nokia and Lenovo are left blank on purpose.
* Re-running first removes the ``m25-`` rows and the price table content, so it is safe to repeat.
  Original and Flipkart rows are never modified.
* The dataset is third-party and a few values look wrong (for example an iPhone 16 with an A17 chip). Where the same
  phone exists in the GSMArena or Flipkart data, THAT entry wins and this one is skipped.

Usage
-----
    export DB_HOST=... DB_PORT=... DB_NAME=... DB_USERNAME=... DB_PASSWORD=...
    python database/import_mobiles2025.py path/to/Mobiles_Dataset_2025.csv

    --dry-run        clean the CSV and print the report without touching the database
    --no-rebuild     only write the tables; run the two build scripts yourself
    --sqlite FILE    use a SQLite copy instead of MySQL (verification only; implies --no-rebuild)
"""
import argparse
import collections
import csv
import os
import re
import sqlite3
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import import_flipkart as fk  # noqa: E402  (shared naming, brand and matching rules)
from extras_parsing import compact_search_text  # noqa: E402

DEVICE_PREFIX = "m25-"
BRAND_PREFIX = "m25-brand-"
SOURCE_LABEL = "Mobiles Dataset 2025"
PRICE_TABLE = "device_price_v1"

COUNTRIES = [  # (country, currency, csv column)
    ("Pakistan", "PKR", "Launched Price (Pakistan)"),
    ("India", "INR", "Launched Price (India)"),
    ("China", "CNY", "Launched Price (China)"),
    ("USA", "USD", "Launched Price (USA)"),
    ("Dubai", "AED", "Launched Price (Dubai)"),
]
STORAGE_SUFFIX = re.compile(r"\s*\b(\d+(?:\.\d+)?)\s*(GB|TB)\s*$", re.I)
MP_RE = re.compile(r"(\d+(?:\.\d+)?)\s*MP\s*(\([^)]*\))?", re.I)
MIN_PHONE_INCHES, MAX_PHONE_INCHES = 4.0, 8.5
NOT_A_PHONE = re.compile(r"\b(?:iPad|Tab|Pad|MatePad|Watch)\b", re.I)  # small tablets (7.9 in) pass the size test
# Brands whose phones are not (all) Android, or that are not clearly one OS, are not given an inferred OS.
NO_OS_GUESS = {"huawei", "sony", "nokia", "lenovo"}

DDL = f"""CREATE TABLE IF NOT EXISTS {PRICE_TABLE} (
    device_id VARCHAR(255) NOT NULL,
    country VARCHAR(40) NOT NULL,
    currency CHAR(3) NOT NULL,
    variant VARCHAR(40) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (device_id, country, variant)
)"""


# --------------------------------------------------------------------------------------------- reading
def read_rows(path):
    with open(path, "rb") as f:
        raw = f.read()
    for enc in ("utf-8-sig", "cp1252"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    import io
    return list(csv.DictReader(io.StringIO(text, newline="")))


def money(text):
    """'PKR 224,999' / '¥13,999' / 'CNY 17,999\\xa0' -> 224999.0; 'Not available' -> None."""
    m = re.search(r"(\d[\d,]*(?:\.\d+)?)", str(text or ""))
    if not m:
        return None
    value = float(m.group(1).replace(",", ""))
    return value if value > 0 else None


def first_number(text):
    m = re.search(r"(\d+(?:\.\d+)?)", str(text or ""))
    return float(m.group(1)) if m else None


def ram_options(text):
    """'8GB' -> [8.0]; '8GB / 12GB' -> [8.0, 12.0]; implausible values dropped."""
    out = []
    for m in re.finditer(r"(\d+(?:\.\d+)?)\s*GB", str(text or ""), re.I):
        value = float(m.group(1))
        if 1 <= value <= fk.MAX_PLAUSIBLE_RAM_GB and value not in out:
            out.append(value)
    return out


def camera_list(text):
    """'50MP (Main) + 12MP (Ultra-wide)' -> ['50 MP (Main)', '12 MP (Ultra-wide)']; zero sensors dropped."""
    out = []
    for m in MP_RE.finditer(str(text or "")):
        if float(m.group(1)) > 0:
            label = f" {m.group(2)}" if m.group(2) else ""
            out.append(f"{m.group(1)} MP{label}")
    return out


def split_model(model):
    """'iPhone 16 Plus 128GB' -> ('iPhone 16 Plus', 128.0).  No storage in the name -> (name, None)."""
    model = re.sub(r"\s+", " ", (model or "").strip())
    m = STORAGE_SUFFIX.search(model)
    if not m:
        return model, None
    storage = float(m.group(1)) * (1024.0 if m.group(2).upper() == "TB" else 1.0)
    return model[: m.start()].strip(), storage


# --------------------------------------------------------------------------------------------- cleaning
def clean(rows):
    """Return (devices, report). A device is one phone model with all its storage variants merged."""
    report = collections.OrderedDict()
    report["csv rows"] = len(rows)

    seen, unique = set(), []
    for r in rows:
        key = tuple((r.get(k) or "").strip() for k in sorted(r))
        if key not in seen:
            seen.add(key)
            unique.append(r)
    report["after removing exact duplicate rows"] = len(unique)

    dropped = collections.Counter()
    groups = collections.OrderedDict()
    for r in unique:
        brand = (r.get("Company Name") or "").strip()
        base, storage = split_model(r.get("Model Name"))
        inches = first_number(r.get("Screen Size"))
        if not brand or not base:
            dropped["no company or model name"] += 1
        elif NOT_A_PHONE.search(base):
            dropped["tablets and watches (by name)"] += 1
        elif inches is None or not (MIN_PHONE_INCHES <= inches <= MAX_PHONE_INCHES):
            dropped["screen outside the smartphone range (tablets, laptops, missing)"] += 1
        else:
            groups.setdefault(fk.group_key(brand, base), []).append((r, brand, base, storage))
    for reason, n in dropped.items():
        report["dropped: " + reason] = n

    devices = [_merge(items) for items in groups.values()]
    report["phone models after merging storage variants"] = len(devices)
    return devices, report


def _mode(values):
    values = [v for v in values if v not in (None, "", ())]
    return collections.Counter(values).most_common(1)[0][0] if values else None


def _merge(items):
    first_row, brand, base, _ = items[0]
    variants = {}  # (ram or None, storage or None) -> {country: (currency, amount)}  (lowest amount per country)
    for r, _, _, storage in items:
        for ram in ram_options(r.get("RAM")) or [None]:
            prices = variants.setdefault((ram, storage), {})
            for country, currency, column in COUNTRIES:
                amount = money(r.get(column))
                if amount is not None and (country not in prices or amount < prices[country][1]):
                    prices[country] = (currency, amount)

    battery = [first_number(r.get("Battery Capacity", "").replace(",", "")) for r, *_ in items]
    weight = [first_number(r.get("Mobile Weight")) for r, *_ in items]
    years = [int(y) for y in (first_number(r.get("Launched Year")) for r, *_ in items) if y and 2000 <= y <= 2026]
    screens = [re.sub(r"\s+", " ", (r.get("Screen Size") or "").strip()) for r, *_ in items]
    return {
        "brand": brand,
        "base": base,
        "variants": variants,
        "year": min(years) if years else None,
        "weight_g": _mode([w for w in weight if w and 60 <= w <= 1000]),
        "battery_mah": _mode([b for b in battery if b and 500 <= b <= 12000]),
        "screen": _mode(screens),
        "rear": _mode([tuple(camera_list(r.get("Back Camera"))) for r, *_ in items]) or (),
        "front": _mode([tuple(camera_list(r.get("Front Camera"))) for r, *_ in items]) or (),
        "processor": fk.clean_processor(_mode([(r.get("Processor") or "").strip() for r, *_ in items])),
        "five_g": bool(re.search(r"\b5G\b", base, re.I)),
    }


# --------------------------------------------------------------------------------------------- rows
def infer_os(brand_name):
    low = brand_name.lower()
    if low in NO_OS_GUESS:
        return None
    return fk.infer_os(brand_name)


def variant_label(ram, storage):
    parts = []
    if ram:
        parts.append(f"{ram:g} GB RAM")
    if storage:
        parts.append(f"{storage / 1024:g} TB" if storage >= 1024 else f"{storage:g} GB")
    return " / ".join(parts)


def lowest(device, country):
    """(currency, lowest amount over all variants) for one country, or None."""
    found = [v[country] for v in device["variants"].values() if country in v]
    return min(found, key=lambda x: x[1]) if found else None


def fmt(amount):
    return "{:,.0f}".format(amount)


def spec_rows_for(d, brand_name):
    year = d["year"]
    rows = []
    if year:
        rows += [("Launch", "Announced", str(year)),
                 ("Launch", "Status", f"Launched {year}. Current availability is not listed in the dataset.")]
    os_name = infer_os(brand_name)
    if os_name:
        rows.append(("Platform", "OS", f"{os_name} (inferred from brand)"))
    if d["processor"]:
        rows.append(("Platform", "Chipset", d["processor"]))

    configs = []
    for (ram, storage) in sorted(d["variants"], key=lambda k: (k[1] or 0, k[0] or 0)):
        if storage:
            s = f"{storage / 1024:g}TB" if storage >= 1024 else f"{storage:g}GB"
            configs.append(f"{s} {ram:g}GB RAM" if ram else s)
        elif ram:
            configs.append(f"{ram:g}GB RAM")
    if configs:
        rows.append(("Memory", "Internal", ", ".join(dict.fromkeys(configs))))

    if d["screen"]:
        rows.append(("Display", "Size", d["screen"]))
    if d["battery_mah"]:
        rows.append(("Battery", "Type", f"{d['battery_mah']:g} mAh"))
    if d["weight_g"]:
        rows.append(("Body", "Weight", f"{d['weight_g']:g} g"))

    labels = {1: "Single", 2: "Dual", 3: "Triple", 4: "Quad", 5: "Penta"}
    for category, lenses in (("Main Camera", d["rear"]), ("Selfie camera", d["front"])):
        if lenses:
            rows.append((category, labels.get(len(lenses), f"{len(lenses)} cameras"), lenses[0]))
            rows.extend((category, "", lens) for lens in lenses[1:])
    if d["five_g"]:
        rows.append(("Network", "5G bands", "5G (stated in the model name; bands not listed)"))

    inr, usd = lowest(d, "India"), lowest(d, "USA")
    price = " / ".join(p for p in ((f"₹ {fmt(inr[1])}" if inr else None), (f"$ {fmt(usd[1])}" if usd else None)) if p)
    if price:
        rows.append(("Misc", "Price", price))
    for country, currency, _ in COUNTRIES:
        low = lowest(d, country)
        if low:
            rows.append(("Misc", f"Launch price ({country})", f"{currency} {fmt(low[1])}"
                         + (" (lowest variant)" if len(d["variants"]) > 1 else "")))
    rows.append(("Misc", "Data source", SOURCE_LABEL + ". Only the fields in that dataset are listed; everything else "
                                                      "is shown as Not listed. Values are as listed there and were not checked."))
    return rows


def price_rows_for(device_id, d):
    """(device_id, country, currency, variant, amount) for every variant and country."""
    out = []
    for (ram, storage), prices in d["variants"].items():
        label = variant_label(ram, storage)
        for country, (currency, amount) in prices.items():
            out.append((device_id, country, currency, label, amount))
    return out


def exact_key(brand_name, name):
    """Like fk.name_key but keeps '5G': a 4G and a 5G model of the same name are different phones."""
    return compact_search_text(brand_name, name.replace("+", " plus "))


def plan(devices, existing_brands, by_key, by_exact):
    """Returns (new_brands, device_rows, spec_rows, price_rows, matched).

    ``by_key`` / ``by_exact`` map a model identity (ignoring / keeping "5G") to the id of a device that is already in
    the database. ``matched`` lists (name, existing_id) for models that were already there.

    Matching ignores "5G" (GSMArena writes "Galaxy S22 5G", this dataset "Galaxy S22"). Only when the dataset itself
    lists BOTH "X" and "X 5G" are they treated as two phones and matched on the exact name.
    """
    brands = {low: (bid, bname, False) for low, (bid, bname) in existing_brands.items()}
    new_count = collections.Counter()
    device_rows, specs, prices, matched = [], [], [], []

    resolved = []
    for d in devices:
        bid, bname, aliased = fk.resolve_brand(d["brand"], existing_brands)
        if bid is None:
            hit = brands.get(bname.lower())
            bid = hit[0] if hit else BRAND_PREFIX + fk.slug(bname)
            brands[bname.lower()] = (bid, bname, hit is None or hit[2])
        name = fk.device_name(d["base"], d["brand"], bname, aliased)
        resolved.append((d, bid, bname, name, fk.name_key(bname, name)))
    crowd = collections.Counter(r[4] for r in resolved)  # keys listed more than once = 4G/5G pairs

    taken, taken_exact = dict(by_key), dict(by_exact)
    for d, bid, bname, name, key in resolved:
        ambiguous = crowd[key] > 1
        existing = taken_exact.get(exact_key(bname, name)) if ambiguous else taken.get(key)
        if existing:
            matched.append((f"{bname} {name}", existing))
            prices.extend(price_rows_for(existing, d))
            continue
        device_id = DEVICE_PREFIX + fk.slug(f"{bname} {name}")
        taken_exact[exact_key(bname, name)] = device_id
        if not ambiguous:
            taken[key] = device_id
        if brands[bname.lower()][2]:
            new_count[bname.lower()] += 1
        device_rows.append({"id": device_id, "brand_id": bid, "name": name, "url": None, "thumbnail": None,
                            "summary": "Imported from the " + SOURCE_LABEL + "."})
        specs.extend((device_id, c, sp, v) for c, sp, v in spec_rows_for(d, bname))
        prices.extend(price_rows_for(device_id, d))

    new_brands = [{"id": b[0], "name": b[1], "num_devices": new_count[low]} for low, b in brands.items() if b[2]]
    return new_brands, device_rows, specs, prices, matched


# ------------------------------------------------------------------------------------------ database
def write_all(conn, ph, devices):
    """Everything happens in one transaction. Returns a result dict for the report."""
    cur = conn.cursor()
    cur.execute(DDL)
    cur.execute(f"DELETE FROM {PRICE_TABLE}")
    cur.execute(f"DELETE FROM device_specification WHERE device_id LIKE {ph}", (DEVICE_PREFIX + "%",))
    cur.execute(f"DELETE FROM device WHERE id LIKE {ph}", (DEVICE_PREFIX + "%",))
    cur.execute(f"DELETE FROM brand WHERE id LIKE {ph}", (BRAND_PREFIX + "%",))

    cur.execute("SELECT id, name FROM brand")
    existing_brands = {(n or "").strip().lower(): (i, n) for i, n in cur.fetchall()}
    cur.execute("SELECT d.id, b.name, d.name FROM device d LEFT JOIN brand b ON b.id = d.brand_id ORDER BY d.id")
    by_key, by_exact = {}, {}
    for device_id, brand, name in cur.fetchall():
        by_key.setdefault(fk.name_key(brand, name or ""), device_id)
        by_exact.setdefault(exact_key(brand, name or ""), device_id)

    new_brands, device_rows, specs, prices, matched = plan(devices, existing_brands, by_key, by_exact)

    cur.execute("SELECT COALESCE(MAX(spec_id), 0) FROM device_specification")
    next_id = int(cur.fetchone()[0]) + 1
    cur.executemany(f"INSERT INTO brand (id, name, url, num_devices) VALUES ({ph}, {ph}, {ph}, {ph})",
                    [(b["id"], b["name"], None, b["num_devices"]) for b in new_brands])
    cur.executemany(f"INSERT INTO device (id, brand_id, name, url, thumbnail, summary) VALUES ({', '.join([ph] * 6)})",
                    [(r["id"], r["brand_id"], r["name"], r["url"], r["thumbnail"], r["summary"]) for r in device_rows])
    cur.executemany(
        f"INSERT INTO device_specification (device_id, spec_id, spec_category, specification, spec_value) "
        f"VALUES ({', '.join([ph] * 5)})",
        [(dev, next_id + i, cat, spec, value) for i, (dev, cat, spec, value) in enumerate(specs)])

    unique_prices = {}
    for device_id, country, currency, variant, amount in prices:  # one row per primary key, keep the lowest
        key = (device_id, country, variant)
        if key not in unique_prices or amount < unique_prices[key][4]:
            unique_prices[key] = (device_id, country, currency, variant, amount)
    cur.executemany(f"INSERT INTO {PRICE_TABLE} (device_id, country, currency, variant, amount) "
                    f"VALUES ({', '.join([ph] * 5)})", list(unique_prices.values()))
    conn.commit()
    cur.close()
    return {"new_brands": new_brands, "devices": device_rows, "specs": len(specs), "matched": matched,
            "price_rows": len(unique_prices)}


def connect_mysql():
    return fk.connect_mysql()


def print_report(clean_report, result=None):
    print("Mobiles Dataset 2025 import")
    for k, v in clean_report.items():
        print(f"  {k}: {v:,}")
    if result:
        from_flipkart = sum(1 for _, i in result["matched"] if str(i).startswith("flipkart-"))
        print(f"  already in the data, not added again: {len(result['matched']):,} "
              f"({from_flipkart:,} Flipkart, {len(result['matched']) - from_flipkart:,} GSMArena)")
        print(f"  devices added: {len(result['devices']):,}   specification rows: {result['specs']:,}"
              f"   new brands: {len(result['new_brands'])}")
        print(f"  launch price rows stored (all countries, all variants): {result['price_rows']:,}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", help="path to Mobiles_Dataset_2025.csv")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--no-rebuild", action="store_true")
    ap.add_argument("--sqlite", metavar="FILE")
    args = ap.parse_args()

    devices, report = clean(read_rows(args.csv))
    if args.dry_run:
        print_report(report)
        return
    if args.sqlite:
        conn, ph = sqlite3.connect(args.sqlite), "?"
    else:
        conn, ph = connect_mysql(), "%s"
    result = write_all(conn, ph, devices)
    conn.close()
    print_report(report, result)

    if args.sqlite or args.no_rebuild:
        print("Next: run database/pipeline/build_device_core_v4.py and database/build_device_extras.py.")
        return
    for script in (os.path.join(HERE, "pipeline", "build_device_core_v4.py"), os.path.join(HERE, "build_device_extras.py")):
        print("Running", os.path.relpath(script, HERE))
        subprocess.run([sys.executable, script], check=True)


if __name__ == "__main__":
    main()
