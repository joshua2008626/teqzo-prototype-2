"""Curated big-brand layer: values researched from public pages (each row carries its source_url).

Rules ("never invent specs", same as the importers):
  * A curated value only fills a cell that is empty. It never overwrites a value another dataset states.
  * A phone that is not in the data yet is added (data source code 3).
  * Values outside a plausible range are dropped (and reported) instead of being stored.
The values were read from web pages through an automated extraction, so a few may still be wrong; every filled
phone is marked in the website and shows its source link.
"""
import csv
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CSV = os.path.join(HERE, "data", "curated_phones.csv")
CURATED_SOURCE = 3

# csv column -> (export field, plausible low, plausible high)
NUMERIC = {
    "year": ("y", 2010, 2027), "ram_gb": ("ram", 0.5, 32), "storage_gb": ("sto", 8, 2048),
    "battery_mah": ("bat", 1000, 12000), "wired_w": ("wc", 5, 240), "display_in": ("ds", 3, 9.5),
    "refresh_hz": ("rr", 30, 165), "main_mp": ("mc", 5, 250), "selfie_mp": ("sc", 1, 100),
    "weight_g": ("w", 80, 500), "price_inr": ("pi", 1000, 1000000), "price_usd": ("pu", 50, 5000),
}
TEXT = {"os": "os", "chipset": "cpu"}
FLAGS = {"has_5g": "g5", "nfc": "nfc", "wireless": "wl"}
LABELS = {"y": "Announced", "ram": "RAM (GB)", "sto": "Storage (GB)", "bat": "Battery (mAh)", "wc": "Wired charging (W)",
          "ds": "Screen (in)", "rr": "Refresh rate (Hz)", "mc": "Main camera (MP)", "sc": "Selfie camera (MP)",
          "w": "Weight (g)", "pi": "Launch price (INR)", "pu": "Launch price (USD)", "os": "Operating system",
          "cpu": "Chipset", "g5": "5G", "nfc": "NFC", "wl": "Wireless charging"}


def compact(text):
    t = str(text or "").lower().replace("+", " plus ")
    return re.sub(r"[^a-z0-9]+", "", t)


def match_key(brand, name):
    """Brand + name with the brand written once and the '5G' marker ignored."""
    b, n = compact(brand), compact(name)
    full = n if n.startswith(b) else b + n
    return full.replace("5g", "")


def alt_keys(brand, name):
    keys = {match_key(brand, name)}
    if compact(brand) in ("redmi", "poco"):
        keys.add(match_key("xiaomi", compact(brand) + compact(name)))
    return keys


def slug(brand, name):
    return "curated-" + re.sub(r"[^a-z0-9]+", "-", f"{brand} {name}".lower().replace("+", " plus ")).strip("-")


def number(text):
    try:
        v = float(str(text).replace(",", "").strip())
    except ValueError:
        return None
    return int(v) if v == int(v) else round(v, 2)


def load(path=DEFAULT_CSV):
    """-> (records, dropped). A record: {brand, name, url, values: {export field: value}}."""
    records, dropped = [], []
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            brand, name, url = (row.get("brand") or "").strip(), (row.get("name") or "").strip(), (row.get("source_url") or "").strip()
            if not (brand and name and url.startswith("http")):
                continue
            values = {}
            for col, (field, lo, hi) in NUMERIC.items():
                raw = (row.get(col) or "").strip()
                if not raw:
                    continue
                v = number(raw)
                if v is None or not lo <= v <= hi:
                    dropped.append((brand, name, col, raw))
                    continue
                values[field] = v
            for col, field in TEXT.items():
                raw = (row.get(col) or "").strip()
                if raw:
                    values[field] = raw[:120]
            for col, field in FLAGS.items():
                raw = (row.get(col) or "").strip().lower()
                if raw in ("yes", "no"):
                    values[field] = 1 if raw == "yes" else 0
            records.append({"brand": brand, "name": name, "url": url, "values": values})
    return records, dropped


def apply(rows, fields, records):
    """rows: lists in `fields` order, extended in place; returns (new rows appended, info) where
    info[id] = {"url": ..., "filled": [field, ...], "added": bool}."""
    idx = {f: i for i, f in enumerate(fields)}
    by_key = {}
    for r in rows:
        by_key.setdefault(match_key(r[idx["b"]], r[idx["n"]]), []).append(r)
    info, new_rows = {}, []
    for rec in records:
        targets = []
        for k in alt_keys(rec["brand"], rec["name"]):
            targets += by_key.get(k, [])
        if targets:
            for r in targets:
                filled = []
                for field, v in rec["values"].items():
                    i = idx[field]
                    if r[i] is None and not (field == "g5" and r[idx["s"]] == 0 and r[i] == 0):
                        r[i] = v
                        filled.append(field)
                if filled:
                    r[idx["cu"]] = 1
                    info[r[0]] = {"url": rec["url"], "filled": filled, "added": False}
        else:
            r = [None] * len(fields)
            r[idx["id"]], r[idx["b"]], r[idx["n"]], r[idx["s"]], r[idx["cu"]] = slug(rec["brand"], rec["name"]), rec["brand"], rec["name"], CURATED_SOURCE, 1
            for field, v in rec["values"].items():
                r[idx[field]] = v
            new_rows.append(r)
            by_key.setdefault(match_key(rec["brand"], rec["name"]), []).append(r)
            info[r[0]] = {"url": rec["url"], "filled": sorted(rec["values"]), "added": True}
    return new_rows, info


def spec_lines(rec_info, row, fields):
    """Rows for device_specification-style output: a group listing what the curated layer supplied."""
    idx = {f: i for i, f in enumerate(fields)}
    out = []
    for field in rec_info["filled"]:
        v = row[idx[field]]
        if field in FLAGS.values():
            v = "Yes" if v == 1 else "No"
        out.append(["Added from public pages", LABELS.get(field, field), str(v)])
    out.append(["Added from public pages", "Source", rec_info["url"]])
    return out
