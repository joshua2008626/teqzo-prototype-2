"""Pure parsing helpers used to build the ``device_extras_v1`` table.

Every function returns ``None`` when the source text does not state the value.
Nothing here guesses: a missing value stays missing.
"""
import re

THIN_SPACE = "\u2009"


# ---------------------------------------------------------------- search text
def compact_search_text(brand_name, device_name):
    """Lower-case alphanumeric-only text, so "iPhone 15", "iphone15" and
    "Samsung S24" can all be matched with plain LIKE tokens."""
    raw = f"{brand_name or ''} {device_name or ''}".lower()
    return re.sub(r"[^a-z0-9]+", "", raw)


# ------------------------------------------------------------------- charging
_WATTS = re.compile(r"(\d+(?:\.\d+)?)\s*W\b", re.I)
_QUALIFIERS = ("optional", "market dependent", "market/region dependent", "region dependent")


def parse_charging(text):
    """Split a GSMArena "Charging" string into wired / wireless / reverse parts.

    Segments are separated by ',' or ';'. A segment containing "reverse" is
    reverse charging, otherwise one containing "wireless" is wireless charging,
    otherwise a segment with "wired" is wired charging. Watts are read from the
    same segment, whichever side of the keyword they appear on
    ("18W wireless", "15W magnetic wireless").

    A qualifier such as "(optional)" or "(market dependent)" makes the status
    CONDITIONAL instead of YES. Because qualifiers sit in their own
    parenthesis, segments are split only on commas/semicolons outside brackets.

    Returns a dict with keys: wired_w, wireless_w, reverse_w,
    wireless_status ('YES' / 'CONDITIONAL' / None), reverse_status (same).
    """
    out = {"wired_w": None, "wireless_w": None, "reverse_w": None,
           "wireless_status": None, "reverse_status": None}
    if not text:
        return out
    for segment in _split_outside_brackets(text):
        low = segment.lower().strip()
        if not low:
            continue
        watts = [float(x) for x in _WATTS.findall(segment)]
        conditional = any(q in low for q in _QUALIFIERS)
        if "reverse" in low:
            out["reverse_status"] = _merge_status(out["reverse_status"], conditional)
            if watts:
                out["reverse_w"] = max([out["reverse_w"] or 0] + watts)
        elif "wireless" in low:
            out["wireless_status"] = _merge_status(out["wireless_status"], conditional)
            if watts:
                out["wireless_w"] = max([out["wireless_w"] or 0] + watts)
        elif "wired" in low and watts:
            out["wired_w"] = max([out["wired_w"] or 0] + watts)
    return out


def _split_outside_brackets(text):
    parts, depth, current = [], 0, []
    for ch in text:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth = max(0, depth - 1)
        if ch in ",;" and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    return parts


def _merge_status(current, conditional):
    new = "CONDITIONAL" if conditional else "YES"
    return "YES" if "YES" in (current, new) else new


# --------------------------------------------------------------------- prices
_PRICE_NUMBER = re.compile(r"(\d[\d,]*(?:\.\d+)?)")


def _amount(text):
    m = _PRICE_NUMBER.search(text.replace(THIN_SPACE, " "))
    if not m:
        return None
    try:
        return float(m.group(1).replace(",", ""))
    except ValueError:
        return None


def parse_price(raw):
    """Read the prices a device lists. Only amounts that are literally present are
    returned; nothing is converted between currencies.

    Handles "€ 1,099.00 / $ 899.99 / ₹ 89,900" and "About 330 EUR".
    """
    out = {"inr": None, "eur": None, "usd": None}
    if not raw:
        return out
    text = raw.replace(THIN_SPACE, " ").strip()
    about = re.match(r"about\s+([\d,\.]+)\s+([A-Za-z]{3})\b", text, re.I)
    if about:
        key = {"INR": "inr", "EUR": "eur", "USD": "usd"}.get(about.group(2).upper())
        amount = _amount(about.group(1))
        if key and amount is not None:
            out[key] = amount
        return out
    for part in text.split("/"):
        part = part.strip()
        if part.startswith("₹"):
            key = "inr"
        elif part.startswith("€"):
            key = "eur"
        elif part.startswith("$"):
            key = "usd"
        else:
            continue  # £, C$, A$, Rp ... are not used for filtering
        amount = _amount(part)
        if amount is not None:
            out[key] = amount
    return out


# --------------------------------------------------------------------- launch
def parse_year(text):
    if not text:
        return None
    m = re.search(r"\b(19[89]\d|20[0-4]\d)\b", text)
    return int(m.group(1)) if m else None


def parse_status_code(text):
    if not text:
        return None
    low = text.lower()
    if low.startswith("available"):
        return "AVAILABLE"
    if low.startswith("discontinued"):
        return "DISCONTINUED"
    if low.startswith("cancelled"):
        return "CANCELLED"
    if low.startswith("coming") or low.startswith("rumored"):
        return "UPCOMING"
    return "OTHER"


# ------------------------------------------------------------------ platform
_CORE_WORDS = {"single": 1, "dual": 2, "tri": 3, "quad": 4, "hexa": 6, "octa": 8, "deca": 10}


def parse_cpu(text):
    """Return (cores, max_ghz). Placeholders such as "X.X GHz" are ignored."""
    if not text:
        return None, None
    cores = None
    m = re.search(r"\b(single|dual|tri|quad|hexa|octa|deca)[- ]?core", text, re.I)
    if m:
        cores = _CORE_WORDS[m.group(1).lower()]
    else:
        m = re.search(r"\b(\d+)[- ]core", text, re.I)
        if m:
            cores = int(m.group(1))
    speeds = [float(x) for x in re.findall(r"(\d+(?:\.\d+)?)\s*GHz", text, re.I)]
    speeds += [float(x) / 1000.0 for x in re.findall(r"(\d{3,4})\s*MHz", text, re.I)]
    return cores, (max(speeds) if speeds else None)


def parse_resolution(text):
    """Return (short_side, long_side) in pixels, or (None, None)."""
    if not text:
        return None, None
    m = re.search(r"(\d{3,5})\s*x\s*(\d{3,5})", text, re.I)
    if not m:
        return None, None
    a, b = int(m.group(1)), int(m.group(2))
    return min(a, b), max(a, b)


# ------------------------------------------------------------- connectivity
def parse_nfc(text):
    """1 = stated unconditionally, 0 = "No", None = unknown / conditional."""
    if text is None:
        return None
    low = text.strip().lower()
    if low == "yes":
        return 1
    if low == "no":
        return 0
    return None


def parse_wifi_generation(text):
    if not text:
        return None
    low = text.lower()
    if re.search(r"wi-?fi\s*7|802\.11[a-z/ ]*be\b", low):
        return 7
    if re.search(r"wi-?fi\s*6|802\.11[a-z/ ]*(ax|/6e?)\b", low):
        return 6
    if re.search(r"802\.11[a-z/ ]*ac\b", low):
        return 5
    if re.search(r"802\.11[a-z/ ]*n\b", low):
        return 4
    return None


def parse_bluetooth_version(text):
    if not text:
        return None
    m = re.match(r"\s*(\d\.\d)", text)
    return float(m.group(1)) if m else None


def parse_usb_type_c(text):
    if not text:
        return None
    return 1 if re.search(r"type-?c", text, re.I) else 0
