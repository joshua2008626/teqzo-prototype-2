#!/usr/bin/env python3
"""Add the 2026 Flipkart mobiles dataset to the existing GSMArena data.

How it works
------------
The Flipkart CSV is cleaned (exact duplicates removed, feature phones and broken rows dropped, colour/storage
variants merged into one device per model) and written as ordinary rows of the three ORIGINAL tables:

    brand, device, device_specification

in the same "category / specification / value" text format GSMArena uses. Because of that, the two existing
build scripts understand the new devices without any special cases:

    database/pipeline/build_device_core_v4.py   ->  device_core_v4
    database/build_device_extras.py             ->  device_extras_v1

and re-running those scripts later never loses the new devices.

Rules (nothing is invented)
---------------------------
* Only values present in the CSV are stored. Everything else stays missing and the app shows "Not listed".
* Price = the lowest price listed for any variant of the model (INR, exactly as listed, never converted).
* Models that already exist in the GSMArena data (same brand, same name apart from "5G") are skipped, so
  nothing appears twice and the GSMArena entry is kept untouched.
* Rows are only ever added under ids starting with ``flipkart-`` (brands: ``flipkart-brand-``). Re-running the
  script first removes those rows again, so it is safe to run repeatedly. Original rows are never modified.
* The Flipkart listing does not state the operating system. It is filled in ONLY for brands that sell nothing
  but Android phones (and Apple -> iOS) and is labelled "inferred from brand" in the specification text.

Usage
-----
    export DB_HOST=... DB_PORT=... DB_NAME=... DB_USERNAME=... DB_PASSWORD=...
    python database/import_flipkart.py path/to/flipkart_mobiles_dataset_2026.csv

    --dry-run        clean the CSV and print the report without touching the database
    --no-rebuild     only write brand/device/device_specification; run the two build scripts yourself
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
from extras_parsing import compact_search_text  # noqa: E402

DEVICE_PREFIX = "flipkart-"
BRAND_PREFIX = "flipkart-brand-"
SOURCE_LABEL = "Flipkart mobiles dataset, 2026 snapshot"
LISTED_YEAR = "2026"

# "Samsung Galaxy S26 5G (Sky Blue, 256 GB)" -> base, colour, storage
VARIANT_RE = re.compile(
    r"^(?P<base>.+?)\s*\((?P<colour>[^(),]+),\s*(?P<size>\d+(?:\.\d+)?)\s*(?P<unit>GB|TB)\)\s*$", re.I)

MIN_PHONE_INCHES, MAX_PHONE_INCHES = 4.0, 8.5
MAX_PLAUSIBLE_RAM_GB = 24

# CSV brand (lower-case) -> GSMArena brand name. REDMI and POCO phones are listed under Xiaomi there, CMF under Nothing.
BRAND_ALIASES = {"redmi": "Xiaomi", "poco": "Xiaomi", "cmf": "Nothing"}
# Display names for brands that have to be created (the CSV shouts or lower-cases some of them).
BRAND_DISPLAY = {"iqoo": "iQOO", "hmd": "HMD", "itel": "itel", "oneplus": "OnePlus", "oppo": "OPPO",
                 "lava": "Lava", "karbonn": "Karbonn", "cmf": "CMF", "dizo": "DIZO", "ai+": "Ai+"}
# Brands whose smartphones all run Android (used only to fill the OS, labelled as inferred).
ANDROID_ONLY_BRANDS = {"samsung", "xiaomi", "redmi", "poco", "realme", "vivo", "iqoo", "oppo", "oneplus", "motorola",
                       "google", "nothing", "cmf", "infinix", "tecno", "itel", "honor", "lava"}


# --------------------------------------------------------------------------------------------- cleaning
def num(text):
    """float from a CSV cell, None when empty or not numeric."""
    try:
        return float(str(text).strip())
    except (TypeError, ValueError):
        return None


def clean_processor(text):
    if not text:
        return None
    s = re.sub(r"\s+Processor\s*$", "", text.strip(), flags=re.I).strip()
    if not s or s.upper() in {"NA", "N/A", "NONE", "0"}:
        return None
    return s


def clean_camera_text(text):
    """'50MP + 2MP' -> ['50 MP', '2 MP'].  Zero or empty sensors are dropped."""
    if not text:
        return []
    out = []
    for part in text.split("+"):
        part = re.sub(r"(\d+(?:\.\d+)?)\s*MP", r"\1 MP", part.strip(), flags=re.I)
        m = re.match(r"(\d+(?:\.\d+)?)\s*MP", part, re.I)
        if part and m and float(m.group(1)) > 0:
            out.append(part)
    return out


def clean_front(text):
    """'5MP; f/2.2; FOV 77°' -> '5 MP; f/2.2; FOV 77°'.  None when no real sensor is listed."""
    if not text or not clean_camera_text(text.split(";")[0]):
        return None
    return re.sub(r"(\d+(?:\.\d+)?)\s*MP", r"\1 MP", text.strip(), flags=re.I)


MARKETING_CUTS = [
    re.compile(r"\s*[-–—]\s*Locked with .*$", re.I),                      # "- Locked with Airtel Prepaid"
    re.compile(r"\s+with\s+\d[\d,]*\s*mAh\b.*$", re.I),                   # "with 7000mAh and 80W ... Charger"
    re.compile(r"\s+\d[\d,]*\s*mAh\b.*$", re.I),                          # "6000mAh and 45W SUPERVOOC Charger"
    re.compile(r"\s+\d+\s*W\s+(?:SUPERVOOC\s+)?Charger\b.*$", re.I),       # "45W Charger In the Box & AI"
    re.compile(r"\s+Charger\s+in[\s-]+(?:the[\s-]+)?box\b.*$", re.I),       # "Charger in the Box"
]


def clean_title(base, colour, storage_gb, brand):
    """Remove the marketing text Flipkart appends to titles and unify a few spellings, so that one phone is one
    device: "Galaxy A17 5G Charger in the Box" -> "Galaxy A17 5G", "Redmi Note-14" -> "Redmi Note 14"."""
    title = re.sub(r"\s+", " ", base).strip()
    for cut in MARKETING_CUTS:
        title = cut.sub("", title).strip()
    tail = f"{colour} {int(storage_gb)}".lower()  # "... 5G+ Cyber Blue 128" (colour and storage glued to the name)
    if title.lower().endswith(" " + tail):
        title = title[: -len(tail)].strip()
    title = re.sub(r"(?<=[A-Za-z])-(?=\d)", " ", title)
    if brand.lower() == "samsung":  # "Samsung M36 5G" and "Galaxy M36 5G" are the same phone
        title = re.sub(r"^(?:Samsung\s+)?(?=[SAMF]\d{2}[A-Za-z+]*\b)", "Samsung Galaxy ", title)
    return title


def read_rows(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def clean(rows):
    """Return (devices, report). A device is one phone model with all its variants merged."""
    report = collections.OrderedDict()
    report["csv rows"] = len(rows)

    seen, unique = set(), []
    for r in rows:
        key = tuple((r.get(k) or "").strip() for k in sorted(r))
        if key not in seen:
            seen.add(key)
            unique.append(r)
    report["after removing exact duplicate rows"] = len(unique)

    kept, dropped = [], collections.Counter()
    for r in unique:
        name = (r.get("model") or "").strip()
        m = VARIANT_RE.match(name)
        price, size = num(r.get("price")), num(r.get("display_inch"))
        if not m:
            dropped["no '(colour, storage)' part in the name (feature phones and accessories)"] += 1
        elif price is None or price <= 0:
            dropped["no valid price"] += 1
        elif size is not None and size > 0 and not (MIN_PHONE_INCHES <= size <= MAX_PHONE_INCHES):
            dropped["screen size outside smartphone range"] += 1
        else:
            kept.append((r, m))
    for reason, n in dropped.items():
        report["dropped: " + reason] = n

    groups = collections.OrderedDict()
    for r, m in kept:
        brand = (r.get("brand") or "").strip()
        storage = float(m.group("size")) * (1024.0 if m.group("unit").upper() == "TB" else 1.0)
        base = clean_title(m.group("base"), m.group("colour").strip(), storage, brand)
        groups.setdefault(group_key(brand, base), []).append((r, m, brand, base))

    devices = []
    for (_, _), items in groups.items():
        devices.append(_merge(items))
    report["phone models after merging variants"] = len(devices)
    report["variants (RAM/storage/colour listings)"] = len(kept)
    return devices, report


def group_key(brand, base):
    """Same phone = same brand family and same name. "5G" stays part of the key (a 4G and a 5G model differ)."""
    family = BRAND_ALIASES.get(brand.lower(), brand).lower()
    words = {brand.lower(), family} | {a for a, target in BRAND_ALIASES.items() if target.lower() == family}
    rest = base
    while True:  # "Xiaomi REDMI Note 14" and "REDMI Note 14" are the same phone
        stripped = re.sub(rf"^(?:{'|'.join(re.escape(w) for w in words)})\s+", "", rest, flags=re.I)
        if stripped == rest:
            break
        rest = stripped
    return family, plus_safe_compact(rest)


def plus_safe_compact(text):
    """compact_search_text drops '+', which would merge "S26" with "S26+"."""
    return compact_search_text("", text.replace("+", " plus "))


def _mode(values):
    values = [v for v in values if v not in (None, "")]
    if not values:
        return None
    return collections.Counter(values).most_common(1)[0][0]


def _merge(items):
    first_row, _, brand, base = items[0]
    variants = {}  # (ram, storage_gb) -> lowest price
    colours = []
    ratings, rating_weight, rating_count = 0.0, 0.0, 0
    for r, m, _, _ in items:
        storage = float(m.group("size")) * (1024.0 if m.group("unit").upper() == "TB" else 1.0)
        ram = num(r.get("ram"))
        ram = ram if ram is not None and 1 <= ram <= MAX_PLAUSIBLE_RAM_GB else None
        price = num(r["price"])
        key = (ram, storage)
        variants[key] = min(price, variants.get(key, price))
        colour = m.group("colour").strip()
        if colour and colour not in colours:
            colours.append(colour)
        rating, count = num(r.get("rating")), num(r.get("ratings_count")) or 0.0
        if rating and rating > 0 and count > 0:
            ratings += rating * count
            rating_weight += count
            rating_count += int(count)

    sizes = [num(r.get("display_inch")) for r, *_ in items]
    batteries = [num(r.get("battery")) for r, *_ in items]
    return {
        "brand": brand,
        "base": base,
        "variants": variants,
        "colours": colours,
        "price_min": min(variants.values()),
        "display_inch": _mode([s for s in sizes if s and s > 0]),
        "battery_mah": _mode([b for b in batteries if b and b > 100]),
        "rear": _mode([tuple(clean_camera_text(r.get("rear_camera"))) or None for r, *_ in items]) or (),
        "front_raw": _mode([clean_front(r.get("front_camera")) for r, *_ in items]),
        "processor": _mode([clean_processor(r.get("processor")) for r, *_ in items]),
        "rating": round(ratings / rating_weight, 1) if rating_weight else None,
        "rating_count": rating_count,
        "five_g": bool(re.search(r"\b5G\b", base, re.I)),
    }


# --------------------------------------------------------------------------------- naming and brands
def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower().replace("+", " plus ")).strip("-")


def name_key(brand_name, name):
    """Identity used to find a model that is already in the data (ignores 5G, keeps '+')."""
    return compact_search_text(brand_name, without_5g(name).replace("+", " plus "))


def without_5g(name):
    return re.sub(r"\s+", " ", re.sub(r"\b5G\+?", "", name, flags=re.I)).strip()


def resolve_brand(csv_brand, existing):
    """existing: {lower-case name: (id, name)}. Returns (brand_id or None, brand_name, was_aliased)."""
    low = csv_brand.strip().lower()
    aliased = low in BRAND_ALIASES
    target = BRAND_ALIASES.get(low, csv_brand.strip())
    hit = existing.get(target.lower())
    if hit:
        return hit[0], hit[1], aliased
    display = BRAND_DISPLAY.get(target.lower(), target)
    return None, display, aliased


def device_name(base, csv_brand, brand_name, aliased):
    """GSMArena names omit the brand ("Galaxy S24"). Strip the brand prefix unless the brand was remapped
    (REDMI/POCO phones keep their own name because their brand is Xiaomi)."""
    if not aliased:
        for prefix in {csv_brand.strip(), brand_name}:
            if prefix and base.lower().startswith(prefix.lower() + " "):
                return base[len(prefix):].strip()
    return base


def infer_os(brand_name):
    low = brand_name.lower()
    if low == "apple":
        return "iOS"
    if low in ANDROID_ONLY_BRANDS:
        return "Android"
    return None


def plan(devices, existing_brands, existing_names):
    """Turn cleaned devices into rows. Returns (new_brands, device_rows, spec_rows, skipped)."""
    brands = {}  # name.lower() -> (id, name, is_new)
    for low, (bid, bname) in existing_brands.items():
        brands[low] = (bid, bname, False)
    new_brand_count = collections.Counter()
    device_rows, specs, skipped = [], [], []

    for d in devices:
        bid, bname, aliased = resolve_brand(d["brand"], existing_brands)
        if bid is None:
            hit = brands.get(bname.lower())
            bid = hit[0] if hit else BRAND_PREFIX + slug(bname)
            brands[bname.lower()] = (bid, bname, hit is None or hit[2])
        name = device_name(d["base"], d["brand"], bname, aliased)
        key = name_key(bname, name)
        if key in existing_names:
            skipped.append(f"{bname} {name}")
            continue
        existing_names.add(key)
        if brands[bname.lower()][2]:
            new_brand_count[bname.lower()] += 1
        device_id = DEVICE_PREFIX + slug(f"{bname} {name}")
        device_rows.append({
            "id": device_id, "brand_id": bid, "name": name, "url": None, "thumbnail": None,
            "summary": "Imported from the " + SOURCE_LABEL + ".",
        })
        specs.extend((device_id, cat, spec, value) for cat, spec, value in spec_rows_for(d, bname))

    new_brands = [{"id": b[0], "name": b[1], "num_devices": new_brand_count[low]}
                  for low, b in brands.items() if b[2]]
    return new_brands, device_rows, specs, skipped


def inr(amount):
    return "₹ {:,.0f}".format(amount)


def spec_rows_for(d, brand_name):
    """GSMArena-style (category, specification, value) rows for one cleaned device."""
    rows = [("Launch", "Status", f"Available. Listed on Flipkart ({LISTED_YEAR}); the launch date is not in the dataset."),
            ("Launch", "Listed", LISTED_YEAR)]

    os_name = infer_os(brand_name)
    if os_name:
        rows.append(("Platform", "OS", f"{os_name} (inferred from brand)"))
    if d["processor"]:
        rows.append(("Platform", "Chipset", d["processor"]))

    configs = []
    for (ram, storage), _price in sorted(d["variants"].items(), key=lambda kv: (kv[0][1], kv[0][0] or 0)):
        s = f"{storage / 1024:g}TB" if storage >= 1024 else f"{storage:g}GB"
        configs.append(f"{s} {ram:g}GB RAM" if ram else s)
    rows.append(("Memory", "Internal", ", ".join(configs)))

    if d["display_inch"]:
        rows.append(("Display", "Size", f"{d['display_inch']:g} inches"))
    if d["battery_mah"]:
        rows.append(("Battery", "Type", f"{d['battery_mah']:g} mAh"))

    labels = {1: "Single", 2: "Dual", 3: "Triple", 4: "Quad", 5: "Penta"}
    if d["rear"]:
        rows.append(("Main Camera", labels.get(len(d["rear"]), f"{len(d['rear'])} cameras"), d["rear"][0]))
        rows.extend(("Main Camera", "", lens) for lens in d["rear"][1:])
    if d["front_raw"]:
        rows.append(("Selfie camera", "Single", d["front_raw"]))
    if d["five_g"]:
        rows.append(("Network", "5G bands", "5G (stated in the listing name; bands not listed)"))

    rows.append(("Misc", "Price", inr(d["price_min"])))
    if len(d["variants"]) > 1:
        lines = []
        for (ram, storage), price in sorted(d["variants"].items(), key=lambda kv: (kv[0][1], kv[0][0] or 0)):
            label = (f"{ram:g} GB RAM / " if ram else "") + (f"{storage / 1024:g} TB" if storage >= 1024 else f"{storage:g} GB")
            lines.append(f"{label}: {inr(price)}")
        rows.append(("Misc", "Price by variant", lines[0]))
        rows.extend(("Misc", "", line) for line in lines[1:])
    if d["colours"]:
        rows.append(("Misc", "Colors", ", ".join(d["colours"])))
    if d["rating"]:
        rows.append(("Misc", "Flipkart rating", f"{d['rating']:g} / 5 from {d['rating_count']:,} ratings"))
    rows.append(("Misc", "Data source", SOURCE_LABEL + ". Only the fields in that dataset are listed; "
                                                   "everything else is shown as Not listed. Price is the lowest listed price."))
    return rows


# ------------------------------------------------------------------------------------------ database
def write_all(conn, ph, devices):
    """Returns the report lines. Everything happens in one transaction."""
    cur = conn.cursor()
    # Devices from import_mobiles2025.py are matched against THIS data, so they are removed here and must be
    # re-imported afterwards (database/import_all.py does both in the right order).
    cur.execute(f"DELETE FROM device_specification WHERE device_id LIKE {ph}", ("m25-%",))
    cur.execute(f"DELETE FROM device WHERE id LIKE {ph}", ("m25-%",))
    cur.execute(f"DELETE FROM brand WHERE id LIKE {ph}", ("m25-brand-%",))
    cur.execute(f"DELETE FROM device_specification WHERE device_id LIKE {ph}", (DEVICE_PREFIX + "%",))
    cur.execute(f"DELETE FROM device WHERE id LIKE {ph}", (DEVICE_PREFIX + "%",))
    cur.execute(f"DELETE FROM brand WHERE id LIKE {ph}", (BRAND_PREFIX + "%",))

    cur.execute("SELECT id, name FROM brand")
    existing_brands = {(n or "").strip().lower(): (i, n) for i, n in cur.fetchall()}
    cur.execute("SELECT b.name, d.name FROM device d LEFT JOIN brand b ON b.id = d.brand_id")
    existing_names = {name_key(b, n or "") for b, n in cur.fetchall()}

    new_brands, device_rows, specs, skipped = plan(devices, existing_brands, existing_names)

    cur.execute("SELECT COALESCE(MAX(spec_id), 0) FROM device_specification")
    next_id = int(cur.fetchone()[0]) + 1

    cur.executemany(f"INSERT INTO brand (id, name, url, num_devices) VALUES ({ph}, {ph}, {ph}, {ph})",
                    [(b["id"], b["name"], None, b["num_devices"]) for b in new_brands])
    cur.executemany(
        f"INSERT INTO device (id, brand_id, name, url, thumbnail, summary) VALUES ({', '.join([ph] * 6)})",
        [(r["id"], r["brand_id"], r["name"], r["url"], r["thumbnail"], r["summary"]) for r in device_rows])
    cur.executemany(
        f"INSERT INTO device_specification (device_id, spec_id, spec_category, specification, spec_value) "
        f"VALUES ({', '.join([ph] * 5)})",
        [(dev, next_id + i, cat, spec, value) for i, (dev, cat, spec, value) in enumerate(specs)])
    conn.commit()
    cur.close()
    return {"new_brands": new_brands, "devices": device_rows, "specs": len(specs), "skipped": skipped}


def connect_mysql():
    try:
        import mysql.connector
    except ImportError:
        sys.exit("mysql-connector-python is required: pip install mysql-connector-python")
    password = os.environ.get("DB_PASSWORD")
    if not password:
        sys.exit("DB_PASSWORD is not set. Export the variables from .env.example first.")
    return mysql.connector.connect(
        host=os.environ.get("DB_HOST", "localhost"), port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USERNAME", "root"), password=password,
        database=os.environ.get("DB_NAME", "tech_finder"))


def print_report(clean_report, result=None):
    print("Flipkart import")
    for k, v in clean_report.items():
        print(f"  {k}: {v:,}")
    if result:
        print(f"  skipped, already in the GSMArena data: {len(result['skipped']):,}")
        print(f"  devices added: {len(result['devices']):,}   specification rows added: {result['specs']:,}"
              f"   new brands: {len(result['new_brands'])}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", help="path to flipkart_mobiles_dataset_2026.csv")
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
