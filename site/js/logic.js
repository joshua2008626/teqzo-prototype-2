// TEQZO website logic: everything that is not drawing. No DOM, no network, so it can be tested with Node.
// It mirrors the rules of the Java console application: a missing value is null and is never treated as 0 or "no".

export const SOURCE_LABELS = {
  0: "GSMArena (full specifications, up to 2022)",
  1: "Flipkart 2026 listings (fewer fields)",
  2: "Mobiles Dataset 2025 (launch prices in 5 countries)",
  3: "Big-brand phones researched from public pages (each with a source link)",
};

/** Turns the compact export (fields + rows) into objects with readable names. */
export function loadDevices(payload) {
  const idx = Object.fromEntries(payload.fields.map((f, i) => [f, i]));
  return payload.rows.map((r) => {
    const g = (f) => r[idx[f]];
    const year = g("y") ?? g("ly") ?? null;
    return {
      id: g("id"), brand: g("b") || "", name: g("n") || "", year, yearIsListing: g("y") == null && g("ly") != null,
      source: g("s"), priceInr: g("pi"), priceUsd: g("pu"), priceEur: g("pe"), ram: g("ram"), storage: g("sto"),
      battery: g("bat"), wired: g("wc"), wireless: g("wl"), display: g("ds"), refresh: g("rr"), os: g("os"),
      chipset: g("cpu"), mainCam: g("mc"), selfieCam: g("sc"), weight: g("w"), has5g: g("g5"), nfc: g("nfc"),
      ip: g("ip"), charging: g("ch"), displayType: g("dt"), curated: g("cu") === 1,
    };
  });
}

// ------------------------------------------------------------------------------------------ text helpers

/** Lower-case letters and digits only; "+" becomes "plus" so "S26+" and "S26" stay different. */
export function compact(text) {
  return String(text ?? "").toLowerCase().replace(/\+/g, " plus ").replace(/[^a-z0-9]+/g, "");
}

export function fullName(d) {
  const name = d.name || "";
  return compact(name).startsWith(compact(d.brand)) ? name : `${d.brand} ${name}`.trim();
}

export function fmtNum(v) {
  if (v == null) return null;
  return Number.isInteger(v) ? String(v) : String(Math.round(v * 100) / 100);
}

/** Indian digit grouping: 129999 -> 1,29,999 */
export function inr(amount) {
  const s = String(Math.round(amount));
  if (s.length <= 3) return "₹" + s;
  const last3 = s.slice(-3);
  const rest = s.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
  return "₹" + rest + "," + last3;
}

/** The price to show: INR if listed, else the USD/EUR price exactly as listed (never converted). */
export function priceText(d) {
  if (d.priceInr != null) return inr(d.priceInr);
  if (d.priceUsd != null) return "$" + Math.round(d.priceUsd).toLocaleString("en-US");
  if (d.priceEur != null) return "€" + Math.round(d.priceEur).toLocaleString("en-US");
  return null;
}

// ------------------------------------------------------------------------------------------ search and filter

/** Matches when every word of the query is found in brand + name + chipset (spaces, case and "+" handled). */
export function matchesQuery(d, q) {
  const words = String(q || "").toLowerCase().replace(/\+/g, " plus ").split(/\s+/).map((w) => w.replace(/[^a-z0-9]/g, "")).filter(Boolean);
  if (!words.length) return true;
  const hay = compact(d.brand + " " + d.name + " " + (d.chipset || ""));
  return words.every((w) => hay.includes(w));
}

function relevance(d, q) {
  const cq = compact(q);
  const cn = compact(d.name);
  const cb = compact(d.brand + d.name);
  if (cn === cq || cb === cq) return 0;
  if (cn.startsWith(cq) || cb.startsWith(cq)) return 1;
  if (cb.includes(cq)) return 2;
  return 3;
}

const atLeast = (v, min) => min == null || (v != null && v >= min);
const atMost = (v, max) => max == null || (v != null && v <= max);

/** f: { q, brands[], priceMax, priceMin, ramMin, storageMin, batteryMin, wiredMin, displayMin, displayMax,
 *       refreshMin, mainCamMin, weightMax, yearFrom, has5g, nfc, wireless, os, chipset, sources[] }.
 *  A device whose value is unknown never matches a filter on that value. */
export function filterDevices(devices, f) {
  const brands = f.brands && f.brands.length ? new Set(f.brands.map((b) => b.toLowerCase())) : null;
  const os = f.os ? f.os.toLowerCase() : null;
  const chipset = f.chipset ? f.chipset.toLowerCase() : null;
  const sources = f.sources && f.sources.length ? new Set(f.sources) : null;
  return devices.filter((d) =>
    matchesQuery(d, f.q) &&
    (!brands || brands.has(d.brand.toLowerCase())) &&
    (!sources || sources.has(d.source)) &&
    atMost(d.priceInr, f.priceMax) && atLeast(d.priceInr, f.priceMin) &&
    atLeast(d.ram, f.ramMin) && atLeast(d.storage, f.storageMin) && atLeast(d.battery, f.batteryMin) &&
    atLeast(d.wired, f.wiredMin) && atLeast(d.display, f.displayMin) && atMost(d.display, f.displayMax) &&
    atLeast(d.refresh, f.refreshMin) && atLeast(d.mainCam, f.mainCamMin) && atMost(d.weight, f.weightMax) &&
    atLeast(d.year, f.yearFrom) &&
    (!f.has5g || d.has5g === 1) && (!f.nfc || d.nfc === 1) && (!f.wireless || d.wireless === 1) &&
    (!os || (d.os || "").toLowerCase().includes(os)) &&
    (!chipset || (d.chipset || "").toLowerCase().includes(chipset)));
}

export const SORTS = {
  relevance: "Best match",
  newest: "Newest",
  name: "Name",
  price_asc: "Price: low to high",
  price_desc: "Price: high to low",
  battery_desc: "Battery",
  ram_desc: "RAM",
  storage_desc: "Storage",
  wired_desc: "Charging speed",
  camera_desc: "Main camera (MP)",
  display_desc: "Screen size",
  refresh_desc: "Refresh rate",
  weight_asc: "Lightest",
};

const SORT_FIELD = {
  newest: ["year", -1], price_asc: ["priceInr", 1], price_desc: ["priceInr", -1], battery_desc: ["battery", -1],
  ram_desc: ["ram", -1], storage_desc: ["storage", -1], wired_desc: ["wired", -1], camera_desc: ["mainCam", -1],
  display_desc: ["display", -1], refresh_desc: ["refresh", -1], weight_asc: ["weight", 1],
};

/** Returns a new sorted array. Unknown values always go last. */
export function sortDevices(list, key, q) {
  const out = [...list];
  const byName = (a, b) => fullName(a).localeCompare(fullName(b));
  if (key === "name") return out.sort(byName);
  if (key === "relevance" || !SORT_FIELD[key]) {
    if (q && compact(q)) {
      return out.sort((a, b) => relevance(a, q) - relevance(b, q) || (b.year ?? 0) - (a.year ?? 0) || byName(a, b));
    }
    return out.sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || byName(a, b));
  }
  const [field, dir] = SORT_FIELD[key];
  return out.sort((a, b) => {
    const x = a[field], y = b[field];
    if (x == null && y == null) return byName(a, b);
    if (x == null) return 1;
    if (y == null) return -1;
    return (x - y) * dir || byName(a, b);
  });
}

// ------------------------------------------------------------------------------------------ top lists

export const LEADERBOARDS = [
  { key: "battery", title: "Biggest battery", sort: "battery_desc", need: (d) => d.battery != null, show: "battery" },
  { key: "ram", title: "Most RAM", sort: "ram_desc", need: (d) => d.ram != null, show: "ram" },
  { key: "camera", title: "Highest main camera resolution", note: "Megapixels only; not a measure of image quality.", sort: "camera_desc", need: (d) => d.mainCam != null, show: "camera" },
  { key: "light", title: "Lightest phones", sort: "weight_asc", need: (d) => d.weight != null, show: "weight" },
  { key: "charging", title: "Fastest wired charging", sort: "wired_desc", need: (d) => d.wired != null, show: "charging" },
  { key: "cheap", title: "Lowest listed price", sort: "price_asc", need: (d) => d.priceInr != null, show: "price" },
  { key: "new", title: "Newest", sort: "newest", need: () => true, show: "year" },
];

/** Phone-sized screens only (4.0-8.5 in): the data also contains tablets and watches. */
export function leaderboard(devices, key, budgetInr) {
  const board = LEADERBOARDS.find((b) => b.key === key) || LEADERBOARDS[0];
  const pool = devices.filter((d) => d.display != null && d.display >= 4 && d.display <= 8.5 && board.need(d) &&
    (budgetInr == null || (d.priceInr != null && d.priceInr <= budgetInr)));
  return { board, items: sortDevices(pool, board.sort) };
}

// ------------------------------------------------------------------------------------------ similarity

function closeness(diff, same, different) {
  if (diff <= same) return 1;
  if (diff >= different) return 0;
  return 1 - (diff - same) / (different - same);
}
const log2 = (v) => Math.log(Math.max(v, 0.1)) / Math.log(2);
const osFamily = (os) => {
  if (!os) return null;
  const low = os.toLowerCase();
  if (low.startsWith("android")) return "android";
  if (low.startsWith("ios") || low.startsWith("ipados")) return "ios";
  return low.split(/[ ,]/)[0] || null;
};

/** 0-100 from the values BOTH phones list, or null when they share too little. Port of the Java SimilarityScorer. */
export function similarity(a, b) {
  let total = 0, weighted = 0, used = 0;
  const reasons = [];
  const add = (weight, c, reason, threshold) => {
    total += weight; weighted += weight * c; used++;
    if (reason && c >= threshold) reasons.push(reason);
  };
  if (a.priceInr > 0 && b.priceInr > 0) add(3, closeness(Math.abs(Math.log(a.priceInr / b.priceInr)), Math.log(1.2), Math.log(2.5)), "similar price", 0.85);
  if (a.ram != null && b.ram != null) add(2, closeness(Math.abs(log2(a.ram) - log2(b.ram)), 0, 2), `same RAM (${fmtNum(a.ram)} GB)`, 0.99);
  if (a.storage != null && b.storage != null) add(1, closeness(Math.abs(log2(a.storage) - log2(b.storage)), 0, 3), "same storage", 0.99);
  if (a.battery != null && b.battery != null) add(2, closeness(Math.abs(a.battery - b.battery) / Math.max(a.battery, b.battery), 0.05, 0.5), "similar battery", 0.85);
  if (a.display != null && b.display != null) add(1.5, closeness(Math.abs(a.display - b.display), 0.1, 1.2), "similar screen size", 0.85);
  if (a.mainCam != null && b.mainCam != null) add(1, closeness(Math.abs(log2(a.mainCam) - log2(b.mainCam)), 0, 2), "same main camera resolution", 0.99);
  if (a.year != null && b.year != null) add(1, closeness(Math.abs(a.year - b.year), 0, 5), "same year", 0.99);
  const fa = osFamily(a.os), fb = osFamily(b.os);
  if (fa && fb) add(1.5, fa === fb ? 1 : 0);
  if (used < 3 || total < 4) return null;
  return { similarity: Math.round((1000 * weighted) / total) / 10, reasons };
}

export function similarTo(devices, id, limit = 6) {
  const base = devices.find((d) => d.id === id);
  if (!base) return [];
  const out = [];
  for (const d of devices) {
    if (d.id === id) continue;
    if (base.priceInr && d.priceInr && (d.priceInr < base.priceInr * 0.5 || d.priceInr > base.priceInr * 1.8)) continue;
    if (base.display != null && d.display != null && Math.abs(d.display - base.display) > 1.2) continue;
    const s = similarity(base, d);
    if (s) out.push({ device: d, ...s });
  }
  return out.sort((x, y) => y.similarity - x.similarity).slice(0, limit);
}

// ------------------------------------------------------------------------------------------ comparison

/** direction: 1 = higher is better, -1 = lower is better, 0 = no defensible "better" (no leader is marked). */
export const COMPARE_ROWS = [
  { key: "price", label: "Price", dir: -1, num: (d) => d.priceInr, text: (d) => (d.priceInr != null ? inr(d.priceInr) : priceText(d)) },
  { key: "year", label: "Year", dir: 0, num: (d) => d.year, text: (d) => (d.year == null ? null : String(d.year) + (d.yearIsListing ? " (listed)" : "")) },
  { key: "ram", label: "RAM", dir: 1, num: (d) => d.ram, text: (d) => (d.ram == null ? null : fmtNum(d.ram) + " GB") },
  { key: "storage", label: "Storage", dir: 1, num: (d) => d.storage, text: (d) => (d.storage == null ? null : fmtNum(d.storage) + " GB") },
  { key: "battery", label: "Battery", dir: 1, num: (d) => d.battery, text: (d) => (d.battery == null ? null : fmtNum(d.battery) + " mAh") },
  { key: "wired", label: "Wired charging", dir: 1, num: (d) => d.wired, text: (d) => (d.wired == null ? null : fmtNum(d.wired) + " W") },
  { key: "refresh", label: "Refresh rate", dir: 1, num: (d) => d.refresh, text: (d) => (d.refresh == null ? null : fmtNum(d.refresh) + " Hz") },
  { key: "display", label: "Screen", dir: 0, num: (d) => d.display, text: (d) => (d.display == null ? null : fmtNum(d.display) + " in") },
  { key: "mainCam", label: "Main camera", dir: 0, num: (d) => d.mainCam, text: (d) => (d.mainCam == null ? null : fmtNum(d.mainCam) + " MP") },
  { key: "selfieCam", label: "Selfie camera", dir: 0, num: (d) => d.selfieCam, text: (d) => (d.selfieCam == null ? null : fmtNum(d.selfieCam) + " MP") },
  { key: "weight", label: "Weight", dir: 0, num: (d) => d.weight, text: (d) => (d.weight == null ? null : fmtNum(d.weight) + " g") },
  { key: "os", label: "Operating system", dir: 0, num: () => null, text: (d) => d.os },
  { key: "chipset", label: "Chipset", dir: 0, num: () => null, text: (d) => d.chipset },
  { key: "g5", label: "5G", dir: 0, num: () => null, text: (d) => (d.has5g === 1 ? "Yes" : d.has5g === 0 ? "No 5G listed" : null) },
  { key: "nfc", label: "NFC", dir: 0, num: () => null, text: (d) => (d.nfc === 1 ? "Yes" : d.nfc === 0 ? "No" : null) },
];

/** Rows with value text per device, a 0-1 bar width (relative to the largest value shown) and the leaders. */
export function compareTable(devices) {
  return COMPARE_ROWS.map((row) => {
    const nums = devices.map((d) => row.num(d));
    const known = nums.filter((v) => v != null);
    const max = known.length ? Math.max(...known) : null;
    let leaders = [];
    if (row.dir !== 0 && known.length >= 2 && new Set(known).size > 1) {
      const best = row.dir > 0 ? Math.max(...known) : Math.min(...known);
      leaders = nums.map((v, i) => (v === best ? i : -1)).filter((i) => i >= 0);
    }
    return {
      key: row.key, label: row.label, dir: row.dir, leaders,
      cells: devices.map((d, i) => ({ text: row.text(d), value: nums[i], bar: nums[i] != null && max ? nums[i] / max : null })),
    };
  });
}

export function verdict(devices, table) {
  const leads = devices.map(() => []);
  let comparable = 0;
  for (const row of table) {
    if (!row.leaders.length) continue;
    comparable++;
    row.leaders.forEach((i) => leads[i].push(row.label));
  }
  const lines = [];
  if (!comparable) return { comparable, winner: -1, lines: ["Not enough listed values to say which phone is ahead."] };
  devices.forEach((d, i) => lines.push(`${fullName(d)} leads in ${leads[i].length} of ${comparable} comparable rows` + (leads[i].length ? `: ${leads[i].join(", ")}.` : ".")));
  const top = Math.max(...leads.map((l) => l.length));
  const winners = leads.map((l, i) => (l.length === top ? i : -1)).filter((i) => i >= 0);
  const winner = top > 0 && winners.length === 1 ? winners[0] : -1;
  lines.push(winner >= 0 ? `Most leads: ${fullName(devices[winner])}. This counts rows; it does not say which phone suits you.` : "No single phone is clearly ahead; it depends on what matters to you.");
  return { comparable, winner, lines };
}

// ------------------------------------------------------------------------------------------ insights

export const PRICE_BANDS = [
  ["Under ₹10,000", 0, 10000], ["₹10,000 – 19,999", 10000, 20000], ["₹20,000 – 29,999", 20000, 30000],
  ["₹30,000 – 49,999", 30000, 50000], ["₹50,000 – 79,999", 50000, 80000], ["₹80,000 and above", 80000, Infinity],
];

const mean = (xs) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : null);

export function insights(devices) {
  const byBrand = new Map();
  for (const d of devices) {
    if (!d.brand) continue;
    if (!byBrand.has(d.brand)) byBrand.set(d.brand, []);
    byBrand.get(d.brand).push(d);
  }
  const brands = [...byBrand.entries()].filter(([, list]) => list.length >= 3).map(([brand, list]) => ({
    brand, devices: list.length,
    avgPrice: mean(list.filter((d) => d.priceInr != null).map((d) => d.priceInr)),
    avgBattery: mean(list.filter((d) => d.battery != null).map((d) => d.battery)),
    avgRam: mean(list.filter((d) => d.ram != null).map((d) => d.ram)),
  })).sort((a, b) => b.devices - a.devices || a.brand.localeCompare(b.brand)).slice(0, 12);

  const yearCounts = new Map();
  for (const d of devices) if (d.year != null) yearCounts.set(d.year, (yearCounts.get(d.year) || 0) + 1);
  const years = [...yearCounts.entries()].map(([year, count]) => ({ year, count })).sort((a, b) => b.year - a.year).slice(0, 12);

  const bands = PRICE_BANDS.map(([label, lo, hi]) => ({ label, count: devices.filter((d) => d.priceInr != null && d.priceInr >= lo && d.priceInr < hi).length }));
  const sources = {};
  for (const d of devices) sources[d.source] = (sources[d.source] || 0) + 1;
  return { brands, years, bands, sources };
}

// ------------------------------------------------------------------------------------------ CSV export

export const CSV_HEADER = "id,brand,name,year,price_inr,price_usd,ram_gb,storage_gb,battery_mah,display_inches,chipset,os,main_camera_mp,selfie_camera_mp,weight_g,has_5g";

export function csvCell(v) {
  if (v == null) return "";
  let text = String(v);
  if (typeof v === "string" && /^[=+\-@\t\r]/.test(text)) text = "'" + text; // spreadsheet formula injection
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(devices) {
  const lines = [CSV_HEADER];
  for (const d of devices) {
    lines.push([d.id, d.brand, d.name, d.year, d.priceInr, d.priceUsd, d.ram, d.storage, d.battery, d.display, d.chipset, d.os,
      d.mainCam, d.selfieCam, d.weight, d.has5g == null ? null : d.has5g ? "yes" : "no"].map(csvCell).join(","));
  }
  return lines.join("\r\n") + "\r\n";
}

// ------------------------------------------------------------------------------------------ smart search

const money = (n, unit) => {
  const x = parseFloat(String(n).replace(/,/g, ""));
  if (Number.isNaN(x)) return null;
  if (!unit) return x;
  if (/^l/.test(unit)) return x * 100000;
  return x * 1000; // k, thousand
};

/**
 * Reads a plain-English request ("samsung 5g under 30k with 8gb ram and good battery") into filters.
 * Rule based, no AI: it only understands the patterns below and reports exactly what it understood (chips).
 * When nothing structured is found the text is used as a normal name search.
 */
export function parseSmartQuery(text, brandNames = []) {
  const original = String(text || "").trim();
  let t = " " + original.toLowerCase() + " ";
  const filters = {};
  const chips = [];
  let sort = null;
  const take = (re, fn) => {
    const m = re.exec(t);
    if (!m) return false;
    fn(m);
    t = t.replace(m[0], " ");
    return true;
  };

  take(/(?:under|below|less than|within|upto|up to|max(?:imum)?|budget(?: of)?|<=?)\s*(?:rs\.?|inr|₹)?\s*([\d][\d,.]*)\s*(k|thousand|lakhs?|l)?\b/, (m) => {
    const v = money(m[1], m[2]);
    if (v) { filters.priceMax = v; chips.push(`Up to ${inr(v)}`); }
  }) || take(/(?:rs\.?|inr|₹)\s*([\d][\d,.]*)\s*(k|thousand|lakhs?|l)?\b/, (m) => {
    const v = money(m[1], m[2]);
    if (v) { filters.priceMax = v; chips.push(`Up to ${inr(v)}`); }
  });
  take(/(?:above|over|more than|from|min(?:imum)?)\s*(?:rs\.?|inr|₹)\s*([\d][\d,.]*)\s*(k|thousand|lakhs?|l)?\b/, (m) => {
    const v = money(m[1], m[2]);
    if (v) { filters.priceMin = v; chips.push(`From ${inr(v)}`); }
  });
  take(/(\d+)\s*(gb|tb)\s*(?:of\s*)?(?:storage|rom|internal)/, (m) => {
    const v = parseInt(m[1], 10) * (m[2] === "tb" ? 1024 : 1);
    filters.storageMin = v; chips.push(`${v} GB+ storage`);
  });
  take(/(\d+)\s*gb\s*(?:of\s*)?ram|ram\s*(?:of\s*)?(\d+)\s*gb/, (m) => {
    const v = parseInt(m[1] || m[2], 10);
    filters.ramMin = v; chips.push(`${v} GB+ RAM`);
  });
  take(/(\d{4,5})\s*mah/, (m) => { filters.batteryMin = parseInt(m[1], 10); chips.push(`${m[1]} mAh+`); });
  take(/\b5g\b/, () => { filters.has5g = true; chips.push("5G"); });
  take(/\bnfc\b/, () => { filters.nfc = true; chips.push("NFC"); });
  take(/wireless charging/, () => { filters.wireless = true; chips.push("Wireless charging"); });
  take(/\biphones?\b|\bios\b/, () => { filters.os = "ios"; chips.push("iOS"); });
  take(/\bandroid\b/, () => { filters.os = "android"; chips.push("Android"); });

  const brands = [];
  for (const name of brandNames) {
    const re = new RegExp(`(^|[^a-z0-9])${name.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?![a-z0-9])`);
    if (re.test(t)) brands.push(name);
  }
  if (brands.length) { filters.brands = brands; chips.push(brands.join(" or ")); }

  const intents = [
    [/\b(?:gaming|games?)\b/, "ram_desc", "Gaming: most RAM first", () => { if (!filters.ramMin) filters.ramMin = 8; }],
    [/\b(?:camera|photos?|photography)\b/, "camera_desc", "Camera: highest megapixels first"],
    [/\b(?:battery|long[- ]lasting)\b/, "battery_desc", "Battery: biggest first"],
    [/\b(?:lightest|light(?:weight)?)\b/, "weight_asc", "Lightest first"],
    [/\b(?:cheapest|cheap|lowest price)\b/, "price_asc", "Cheapest first"],
    [/\b(?:latest|newest|new)\b/, "newest", "Newest first"],
  ];
  for (const [re, key, label, extra] of intents) {
    if (re.test(t) && !sort) {
      sort = key; chips.push(label);
      if (extra) extra();
    }
  }

  if (!chips.length) {
    filters.q = original;
    return { filters, sort: null, chips: original ? [`Name contains "${original}"`] : [], structured: false };
  }
  return { filters, sort, chips, structured: true };
}

// ------------------------------------------------------------------------------------------ spec files

let CRC_TABLE = null;

/** CRC-32 of the UTF-8 bytes, the same value as Python's zlib.crc32 (used to find the specs file of a device). */
export function crc32(text) {
  if (!CRC_TABLE) {
    CRC_TABLE = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      CRC_TABLE[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (const byte of new TextEncoder().encode(text)) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/** File name (without folder) of the full-specification file that holds this device. */
export function specFile(id, buckets = 128) {
  return (crc32(id) % buckets).toString(16).padStart(2, "0") + ".json";
}
