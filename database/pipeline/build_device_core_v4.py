import os
import re
import mysql.connector
from getpass import getpass

DB_NAME = os.environ.get("DB_NAME", "tech_finder")

def prompt(label, default=None, secret=False):
    suffix = f" [{default}]" if default else ""
    if secret:
        return getpass(f"{label}{suffix}: ")
    value = input(f"{label}{suffix}: ").strip()
    return value or default

def first_number(text, pattern):
    if not text:
        return None
    m = re.search(pattern, text, re.I)
    if not m:
        return None
    try:
        return float(m.group(1))
    except ValueError:
        return None

def extract_mp(text):
    if not text:
        return None
    # Prefer explicit megapixel notation.
    m = re.search(r"(\d+(?:\.\d+)?)\s*MP\b", text, re.I)
    if m:
        return float(m.group(1))
    return None

def extract_inches(text):
    if not text:
        return None
    m = re.search(r"(\d+(?:\.\d+)?)\s*inches?\b", text, re.I)
    if m:
        return float(m.group(1))
    return None

def extract_grams(text):
    if not text:
        return None
    m = re.search(r"(\d+(?:\.\d+)?)\s*g\b", text, re.I)
    if m:
        return float(m.group(1))
    return None

def extract_refresh_rate(text):
    if not text:
        return None
    rates = re.findall(r"(\d+(?:\.\d+)?)\s*Hz\b", text, re.I)
    if not rates:
        return None
    return max(float(x) for x in rates)

def parse_memory(value):
    if not value:
        return None, None

    best_ram = None
    best_storage = None
    storage_only = None
    ram_only = None

    # Examples:
    # 32GB 4GB RAM
    # 128GB 8GB RAM, 256GB 12GB RAM
    # 1TB 12GB RAM
    for part in value.split(","):
        ram = re.search(r"(\d+(?:\.\d+)?)\s*(GB|MB)\s*RAM\b", part, re.I)
        # A number followed by "RAM" is memory, never storage ("8GB RAM" alone has no storage figure).
        storage = re.search(r"(\d+(?:\.\d+)?)\s*(TB|GB|MB)\b(?!\s*RAM\b)", part, re.I)

        ram_gb = None
        storage_gb = None

        if ram:
            ram_gb = float(ram.group(1))
            if ram.group(2).upper() == "MB":
                ram_gb /= 1024.0

        if storage:
            storage_gb = float(storage.group(1))
            unit = storage.group(2).upper()
            if unit == "TB":
                storage_gb *= 1024.0
            elif unit == "MB":
                storage_gb /= 1024.0

        # Avoid treating the RAM number as storage when it is the only number.
        if storage and ram:
            if best_ram is None or (ram_gb, storage_gb or 0) > (best_ram, best_storage or 0):
                best_ram = ram_gb
                best_storage = storage_gb
        elif storage and not ram:
            if storage_only is None or storage_gb > storage_only:
                storage_only = storage_gb
        elif ram and not storage:
            if ram_only is None or ram_gb > ram_only:
                ram_only = ram_gb

    # Only when no configuration states both RAM and storage: keep the largest storage-only value (RAM stays NULL).
    # (Needed for entries that list storage but no RAM, e.g. the Flipkart import.)
    if best_ram is None and best_storage is None and storage_only is not None:
        return None, storage_only
    if best_ram is None and best_storage is None and ram_only is not None:
        return ram_only, None  # RAM known, storage not stated (Mobiles 2025 models without a storage size)

    return best_ram, best_storage

def parse_charging(value):
    if not value:
        return None, None, None, False, False

    text = value.strip()
    low = text.lower()

    wired = None
    wireless = None
    reverse = None

    # Observed forms include:
    # "15W wired"
    # "Wireless"
    # "Wireless (Qi)"
    # "Wireless (Qi/PMA)"
    # "Reverse wired"
    # "5W reverse wired"
    # "6W reverse wired"
    wireless_supported = "wireless" in low
    reverse_supported = "reverse wired" in low or "reverse charging" in low

    # Explicit reverse wattage: observed format is "5W reverse wired".
    reverse_matches = re.findall(
        r"(\d+(?:\.\d+)?)\s*W\s+reverse(?:\s+\w+){0,3}",
        text,
        re.I
    )
    if reverse_matches:
        reverse = max(float(x) for x in reverse_matches)

    # Wireless wattage, only when explicitly stated.
    wireless_matches = re.findall(
        r"wireless(?:\s+charging)?(?:\s*[:\-]?\s*(\d+(?:\.\d+)?)\s*W)?",
        text,
        re.I
    )
    for x in wireless_matches:
        if x:
            wireless = max(wireless or 0, float(x))

    # Also support "charging 15W wirelessly".
    wireless_after_matches = re.findall(
        r"charging\s+(\d+(?:\.\d+)?)\s*W\s+wirelessly",
        text,
        re.I
    )
    for x in wireless_after_matches:
        wireless = max(wireless or 0, float(x))

    # Explicit wired charging wattage.
    wired_matches = re.findall(
        r"(\d+(?:\.\d+)?)\s*W\s*(?:wired|fast charging|charging)",
        text,
        re.I
    )
    if wired_matches:
        wired = max(float(x) for x in wired_matches)

    return wired, wireless, reverse, wireless_supported, reverse_supported


def parse_ip(text):
    if not text:
        return None
    matches = re.findall(r"\bIP\d{2,3}\b", text, re.I)
    return matches[0].upper() if matches else None

def normalize_text(value):
    return value.strip() if value else None

# Values from the environment (see .env.example) are used when present; anything missing is asked for.
host = os.environ.get("DB_HOST") or prompt("MySQL host", "localhost")
port = int(os.environ.get("DB_PORT") or prompt("MySQL port", "3306"))
username = os.environ.get("DB_USERNAME") or prompt("MySQL username", "root")
password = os.environ.get("DB_PASSWORD") or prompt("MySQL password", secret=True)

conn = mysql.connector.connect(
    host=host,
    port=port,
    user=username,
    password=password,
    database=DB_NAME
)
cur = conn.cursor(dictionary=True)

print(f"Connected to {DB_NAME}.")

cur.execute("""
DROP TABLE IF EXISTS device_core_v4
""")

cur.execute("""
CREATE TABLE device_core_v4 (
    id VARCHAR(255) PRIMARY KEY,
    brand_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    url TEXT,
    thumbnail TEXT,

    ram_gb DECIMAL(10,2),
    storage_gb DECIMAL(10,2),

    battery_mah DECIMAL(10,2),
    wired_charging_w DECIMAL(10,2),
    wireless_charging_w DECIMAL(10,2),
    reverse_charging_w DECIMAL(10,2),
    wireless_charging_supported BOOLEAN NOT NULL DEFAULT FALSE,
    reverse_charging_supported BOOLEAN NOT NULL DEFAULT FALSE,

    display_size_inches DECIMAL(6,2),
    display_type VARCHAR(500),
    display_resolution VARCHAR(500),
    refresh_rate_hz DECIMAL(7,2),

    os VARCHAR(1000),
    chipset VARCHAR(1000),
    cpu VARCHAR(1000),
    gpu VARCHAR(1000),

    main_camera_mp DECIMAL(10,2),
    selfie_camera_mp DECIMAL(10,2),

    weight_g DECIMAL(8,2),
    ip_rating VARCHAR(20),

    has_5g BOOLEAN NOT NULL DEFAULT FALSE,

    wifi TEXT,
    bluetooth TEXT,
    nfc TEXT,
    usb TEXT,

    INDEX idx_brand (brand_id),
    INDEX idx_ram (ram_gb),
    INDEX idx_storage (storage_gb),
    INDEX idx_battery (battery_mah),
    INDEX idx_wired_charge (wired_charging_w),
    INDEX idx_wireless_charge (wireless_charging_w),
    INDEX idx_refresh_rate (refresh_rate_hz),
    INDEX idx_chipset (chipset(100)),
    INDEX idx_5g (has_5g),

    CONSTRAINT fk_device_core_v4_brand
        FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
""")

# Load devices first.
cur.execute("""
SELECT id, brand_id, name, url, thumbnail
FROM device
ORDER BY id
""")
devices = cur.fetchall()

# Load all specifications once. This avoids ~11k repeated queries.
cur.execute("""
SELECT device_id, spec_category, specification, spec_value
FROM device_specification
ORDER BY device_id, spec_id
""")
spec_rows = cur.fetchall()

specs = {}
for row in spec_rows:
    specs.setdefault(row["device_id"], {}).setdefault(row["spec_category"], []).append(
        (
            (row["specification"] or "").strip(),
            row["spec_value"] or ""
        )
    )

insert_sql = """
INSERT INTO device_core_v4 (
    id, brand_id, name, url, thumbnail,
    ram_gb, storage_gb,
    battery_mah, wired_charging_w, wireless_charging_w, reverse_charging_w,
    wireless_charging_supported, reverse_charging_supported,
    display_size_inches, display_type, display_resolution, refresh_rate_hz,
    os, chipset, cpu, gpu,
    main_camera_mp, selfie_camera_mp,
    weight_g, ip_rating, has_5g,
    wifi, bluetooth, nfc, usb
) VALUES (
    %(id)s, %(brand_id)s, %(name)s, %(url)s, %(thumbnail)s,
    %(ram_gb)s, %(storage_gb)s,
    %(battery_mah)s, %(wired_charging_w)s, %(wireless_charging_w)s, %(reverse_charging_w)s,
    %(wireless_charging_supported)s, %(reverse_charging_supported)s,
    %(display_size_inches)s, %(display_type)s, %(display_resolution)s, %(refresh_rate_hz)s,
    %(os)s, %(chipset)s, %(cpu)s, %(gpu)s,
    %(main_camera_mp)s, %(selfie_camera_mp)s,
    %(weight_g)s, %(ip_rating)s, %(has_5g)s,
    %(wifi)s, %(bluetooth)s, %(nfc)s, %(usb)s
)
"""

batch = []

for i, d in enumerate(devices, start=1):
    cats = specs.get(d["id"], {})

    # Memory
    ram = storage = None
    for spec_name, value in cats.get("Memory", []):
        if spec_name.lower() == "internal":
            ram, storage = parse_memory(value)
            break

    # Display
    display_type = None
    display_size = None
    display_resolution = None
    refresh_rate = None

    for spec_name, value in cats.get("Display", []):
        name_low = spec_name.lower()
        if name_low == "type":
            display_type = normalize_text(value)
        elif name_low == "size":
            display_size = extract_inches(value)
        elif name_low == "resolution":
            display_resolution = normalize_text(value)

        # Refresh rate can occur in any Display value even though
        # the database has no separate "Refresh rate" specification.
        rr = extract_refresh_rate(value)
        if rr is not None:
            refresh_rate = max(refresh_rate or 0, rr)

    # Platform
    os_value = chipset = cpu = gpu = None
    for spec_name, value in cats.get("Platform", []):
        n = spec_name.lower()
        if n == "os":
            os_value = normalize_text(value)
        elif n == "chipset":
            chipset = normalize_text(value)
        elif n == "cpu":
            cpu = normalize_text(value)
        elif n == "gpu":
            gpu = normalize_text(value)

    # Battery
    battery_mah = None
    wired = wireless = reverse = None
    wireless_supported = False
    reverse_supported = False
    for spec_name, value in cats.get("Battery", []):
        n = spec_name.lower()
        if n == "type":
            m = re.search(r"(\d+(?:\.\d+)?)\s*mAh\b", value, re.I)
            if m:
                battery_mah = float(m.group(1))
        elif n == "charging":
            w, wi, r, ws, rs = parse_charging(value)
            wired = max(wired or 0, w or 0) if w is not None else wired
            wireless = max(wireless or 0, wi or 0) if wi is not None else wireless
            reverse = max(reverse or 0, r or 0) if r is not None else reverse
            wireless_supported = wireless_supported or ws
            reverse_supported = reverse_supported or rs

    # Cameras
    main_camera = None
    for spec_name, value in cats.get("Main Camera", []):
        mp = extract_mp(value)
        if mp is not None:
            main_camera = max(main_camera or 0, mp)

    selfie_camera = None
    for spec_name, value in cats.get("Selfie camera", []):
        mp = extract_mp(value)
        if mp is not None:
            selfie_camera = max(selfie_camera or 0, mp)

    # Body
    weight = None
    ip_rating = None
    for spec_name, value in cats.get("Body", []):
        n = spec_name.lower()
        if n == "weight":
            weight = extract_grams(value)
        ip = parse_ip(value)
        if ip and ip_rating is None:
            ip_rating = ip

    # Features can also contain IP rating.
    if ip_rating is None:
        for spec_name, value in cats.get("Features", []):
            ip = parse_ip(value)
            if ip:
                ip_rating = ip
                break

    # Network: presence of a 5G bands row is the reliable 5G indicator.
    has_5g = any(
        spec_name.lower() == "5g bands" and bool(value.strip())
        for spec_name, value in cats.get("Network", [])
    )

    # Connectivity
    wifi = bluetooth = nfc = usb = None
    for spec_name, value in cats.get("Comms", []):
        n = spec_name.lower()
        if n in ("wlan", "wifi", "wi-fi"):
            wifi = normalize_text(value)
        elif n == "bluetooth":
            bluetooth = normalize_text(value)
        elif n == "nfc":
            nfc = normalize_text(value)
        elif n == "usb":
            usb = normalize_text(value)

    batch.append({
        "id": d["id"],
        "brand_id": d["brand_id"],
        "name": d["name"],
        "url": d["url"],
        "thumbnail": d["thumbnail"],
        "ram_gb": ram,
        "storage_gb": storage,
        "battery_mah": battery_mah,
        "wired_charging_w": wired,
        "wireless_charging_w": wireless,
        "reverse_charging_w": reverse,
        "wireless_charging_supported": wireless_supported,
        "reverse_charging_supported": reverse_supported,
        "display_size_inches": display_size,
        "display_type": display_type,
        "display_resolution": display_resolution,
        "refresh_rate_hz": refresh_rate,
        "os": os_value,
        "chipset": chipset,
        "cpu": cpu,
        "gpu": gpu,
        "main_camera_mp": main_camera,
        "selfie_camera_mp": selfie_camera,
        "weight_g": weight,
        "ip_rating": ip_rating,
        "has_5g": has_5g,
        "wifi": wifi,
        "bluetooth": bluetooth,
        "nfc": nfc,
        "usb": usb,
    })

    if len(batch) >= 500:
        cur.executemany(insert_sql, batch)
        conn.commit()
        batch.clear()
        print(f"Processed {i:,}/{len(devices):,} devices...")

if batch:
    cur.executemany(insert_sql, batch)
    conn.commit()

print("\nVerification:")
checks = [
    ("device_core_v4", "SELECT COUNT(*) AS c FROM device_core_v4"),
    ("RAM", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE ram_gb IS NOT NULL"),
    ("Storage", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE storage_gb IS NOT NULL"),
    ("Battery", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE battery_mah IS NOT NULL"),
    ("Wired charging", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE wired_charging_w IS NOT NULL"),
    ("Wireless charging", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE wireless_charging_w IS NOT NULL"),
    ("Reverse charging", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE reverse_charging_w IS NOT NULL"),
    ("Wireless supported", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE wireless_charging_supported = TRUE"),
    ("Reverse supported", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE reverse_charging_supported = TRUE"),
    ("Display", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE display_size_inches IS NOT NULL"),
    ("Refresh rate", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE refresh_rate_hz IS NOT NULL"),
    ("Chipset", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE chipset IS NOT NULL"),
    ("Main camera", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE main_camera_mp IS NOT NULL"),
    ("Selfie camera", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE selfie_camera_mp IS NOT NULL"),
    ("5G", "SELECT COUNT(*) AS c FROM device_core_v4 WHERE has_5g = TRUE"),
]
for label, sql in checks:
    cur.execute(sql)
    print(f"{label}: {cur.fetchone()['c']:,}")

cur.close()
conn.close()

print("\nDone. device_core_v3, device_core_v2 and all original GSMArena tables were not changed.")
print("New table: device_core_v4")
