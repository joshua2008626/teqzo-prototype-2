// TEQZO website: the screens. All searching happens in the browser; logic.js holds the rules.
import {
  SOURCE_LABELS, loadDevices, fullName, fmtNum, priceText, inr, filterDevices, sortDevices, SORTS, LEADERBOARDS,
  leaderboard, similarTo, compareTable, verdict, insights, toCsv, parseSmartQuery, specFile, compact, matchesQuery,
} from "./logic.js";
import { phoneArt } from "./art.js";
import { answer, SUGGESTIONS } from "./assistant.js";

const PAGE = 40;
const MAX_COMPARE = 4;
const view = document.getElementById("view");
/** Replaces the page content; null/false entries are skipped (replaceChildren would print them as text). */
const show = (...nodes) => view.replaceChildren(...nodes.flat().filter((n) => n != null && n !== false));

let DATA = null;      // { meta, devices, byId, brands }
let CURATED = {};     // id -> [source url, [filled fields], added 0/1]
let PRICES = {};      // id -> [[country, currency, variant, amount]]
const specCache = new Map();

// ------------------------------------------------------------------------------------------ helpers

/** Builds DOM safely: text goes in as text, never as HTML. */
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "text") el.textContent = v;
    else if (v === true) el.setAttribute(k, "");
    else el.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const store = {
  get(key) {
    try { const v = JSON.parse(localStorage.getItem(key)); return Array.isArray(v) ? v : []; } catch { return []; }
  },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable: keep working */ } },
};
let compareIds = store.get("teqzo.compare");
let savedIds = store.get("teqzo.saved");

let toastTimer;
function toast(msg) {
  const t = document.getElementById("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), 2200);
}

function updateBadges() {
  for (const [id, list] of [["compare-count", compareIds], ["saved-count", savedIds]]) {
    const el = document.getElementById(id);
    el.textContent = String(list.length);
    el.hidden = list.length === 0;
  }
}

function toggleCompare(id) {
  if (compareIds.includes(id)) compareIds = compareIds.filter((x) => x !== id);
  else if (compareIds.length >= MAX_COMPARE) { toast(`You can compare up to ${MAX_COMPARE} phones. Remove one first.`); return false; }
  else compareIds = [...compareIds, id];
  store.set("teqzo.compare", compareIds);
  updateBadges();
  return true;
}
function toggleSaved(id) {
  savedIds = savedIds.includes(id) ? savedIds.filter((x) => x !== id) : [...savedIds, id];
  store.set("teqzo.saved", savedIds);
  updateBadges();
}

const devicesOf = (ids) => ids.map((id) => DATA.byId.get(id)).filter(Boolean);
const link = (d) => `#/phone/${encodeURIComponent(d.id)}`;

function download(filename, text) {
  const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
  const a = h("a", { href: url, download: filename });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function meter(ratio) {
  return h("div", { class: "meter", "aria-hidden": "true" }, h("i", { style: `width:${Math.round(Math.max(0, Math.min(1, ratio)) * 100)}%` }));
}

const TAG_TEXT = { 1: "Flipkart 2026", 2: "Mobiles 2025", 3: "Public pages" };
function sourceTag(d) {
  if (d.source === 0 && !d.curated) return null;
  const filled = d.source !== 3 && d.curated;
  return h("span", { class: "tag" + (d.curated ? " tag-pub" : ""), title: filled ? "Some empty values were filled from public pages" : SOURCE_LABELS[d.source] },
    filled ? "Gaps filled" : TAG_TEXT[d.source]);
}

function toggleButtons(d, onChange) {
  const cmp = h("button", { class: "icon-btn", type: "button", "aria-pressed": String(compareIds.includes(d.id)), title: "Compare", "aria-label": `Compare ${fullName(d)}`, text: "⇄" });
  cmp.addEventListener("click", () => {
    if (toggleCompare(d.id)) cmp.setAttribute("aria-pressed", String(compareIds.includes(d.id)));
    onChange && onChange();
  });
  const sav = h("button", { class: "icon-btn", type: "button", "aria-pressed": String(savedIds.includes(d.id)), title: "Save to shortlist", "aria-label": `Save ${fullName(d)}`, text: "♥" });
  sav.addEventListener("click", () => {
    toggleSaved(d.id);
    sav.setAttribute("aria-pressed", String(savedIds.includes(d.id)));
    toast(savedIds.includes(d.id) ? "Saved to shortlist" : "Removed from shortlist");
    onChange && onChange();
  });
  return h("div", { class: "actions" }, cmp, sav);
}

function metric(label, value, ratio, cls) {
  return h("div", { class: cls }, h("span", { class: "cell-label" }, label),
    value == null ? h("span", { class: "muted" }, "Not listed") : [value, ratio != null ? meter(ratio) : null]);
}

function phoneRow(d, onChange) {
  const sub = [d.year ? (d.yearIsListing ? `Listed ${d.year}` : String(d.year)) : null, d.chipset, d.display ? `${fmtNum(d.display)} in` : null].filter(Boolean).join("  ·  ");
  return h("li", { class: "row" },
    h("div", { class: "who" }, h("a", { class: "thumb", href: link(d), tabindex: "-1", "aria-hidden": "true" }, phoneArt(d, "back")),
      h("div", null, h("a", { class: "name", href: link(d) }, fullName(d), sourceTag(d)), h("span", { class: "sub" }, sub || "Few details listed"))),
    metric("Memory", d.ram != null || d.storage != null ? [d.ram != null ? `${fmtNum(d.ram)} GB` : "–", " / ", d.storage != null ? `${fmtNum(d.storage)} GB` : "–"].join("") : null, null, "m-mem"),
    metric("Battery", d.battery != null ? `${fmtNum(d.battery)} mAh` : null, d.battery != null ? d.battery / 7000 : null, "m-battery"),
    h("div", { class: "price" }, priceText(d) || h("span", { class: "muted", style: "font-weight:400" }, "Price not listed")),
    toggleButtons(d, onChange));
}

function setTitle(t) { document.title = t ? `${t} – TEQZO` : "TEQZO – find and compare phones"; }

// ------------------------------------------------------------------------------------------ browse

const browseState = { text: "", rail: { brands: [], sources: [] }, sort: "relevance", shown: PAGE };

function numberField(label, key, props) {
  const input = h("input", { class: "field", type: "number", min: "0", inputmode: "numeric", "aria-label": label, placeholder: label, ...props });
  input.value = browseState.rail[key] ?? "";
  input.addEventListener("input", () => {
    const v = input.value === "" ? undefined : Number(input.value);
    if (v === undefined) delete browseState.rail[key]; else browseState.rail[key] = v;
    browseState.shown = PAGE;
    browseState.refresh();
  });
  return input;
}

function checkRow(label, checked, onChange) {
  const box = h("input", { type: "checkbox" });
  box.checked = checked;
  box.addEventListener("change", () => onChange(box.checked));
  return h("label", null, box, label);
}

/** Recent phones from well-known brands, at most two per brand, for the hero and the shelf. */
function featured(n) {
  const big = new Set(["apple", "samsung", "google", "oneplus", "xiaomi", "nothing", "motorola", "vivo", "oppo", "sony", "realme", "honor"]);
  const pool = sortDevices(DATA.devices.filter((d) => big.has(d.brand.toLowerCase()) && d.year >= 2025 && d.display >= 5.5 && d.display <= 7.2 && d.ram != null && d.mainCam != null && (d.priceInr || d.priceUsd)), "newest")
    .sort((a, b) => (b.priceInr || b.priceUsd * 85) - (a.priceInr || a.priceUsd * 85));
  const per = new Map(), out = [];
  for (const d of pool) {
    const k = d.brand;
    if ((per.get(k) || 0) >= 1) continue;
    per.set(k, 1); out.push(d);
    if (out.length === n) break;
  }
  return out;
}

function renderBrowse() {
  setTitle("");
  const s = browseState;
  const results = h("ol", { class: "rows" });
  const count = h("div", { class: "result-count", "aria-live": "polite" });
  const chips = h("div", { class: "chips" });
  const more = h("div", { class: "more" });
  const sortSel = h("select", { class: "field", style: "width:auto", "aria-label": "Sort by" },
    Object.entries(SORTS).map(([k, v]) => h("option", { value: k, selected: k === s.sort }, v)));
  sortSel.addEventListener("change", () => { s.sort = sortSel.value; s.manualSort = true; s.shown = PAGE; refresh(); });

  const search = h("input", { class: "field search", type: "search", id: "q", autocomplete: "off", spellcheck: "false",
    placeholder: "Try: samsung 5g under 30k with 8gb ram, or just a phone name", "aria-label": "Search phones" });
  search.value = s.text;

  let current = [];
  function effective() {
    const smart = parseSmartQuery(s.text, DATA.brands);
    const f = { ...s.rail };
    const sf = { ...smart.filters };
    if (sf.brands && f.brands && f.brands.length) sf.brands = sf.brands.filter((b) => f.brands.includes(b));
    Object.assign(f, sf);
    if (!sf.brands && s.rail.brands.length) f.brands = s.rail.brands;
    return { f, smart };
  }
  function refresh() {
    const { f, smart } = effective();
    const sort = s.manualSort ? s.sort : (smart.sort || s.sort);
    if (!s.manualSort) sortSel.value = sort;
    current = sortDevices(filterDevices(DATA.devices, f), sort, f.q || "");
    count.textContent = `${current.length.toLocaleString("en-US")} ${current.length === 1 ? "phone" : "phones"}`;
    chips.replaceChildren(...(s.text.trim() ? smart.chips.map((c) => h("span", { class: "chip" }, c)) : []));
    const showing = current.slice(0, s.shown);
    results.replaceChildren(...showing.map((d) => phoneRow(d)));
    if (!current.length) results.replaceChildren(h("li", { class: "empty" }, "No phones match. Remove a filter or shorten the search. Phones that do not list a value never match a filter on that value."));
    more.replaceChildren(current.length > showing.length
      ? h("button", { class: "btn", type: "button", onclick: () => { s.shown += PAGE; refresh(); } }, `Show ${Math.min(PAGE, current.length - showing.length)} more`)
      : null);
  }
  s.refresh = refresh;
  search.addEventListener("input", () => { s.text = search.value; s.shown = PAGE; refresh(); });

  // filter rail
  const rail = h("aside", { class: "rail", id: "rail", "aria-label": "Filters" });
  const brandList = h("div", { class: "brand-list" });
  const brandFilter = h("input", { class: "field", type: "search", placeholder: "Find a brand", "aria-label": "Find a brand" });
  const drawBrands = () => {
    const q = compact(brandFilter.value);
    brandList.replaceChildren(...DATA.brands.filter((b) => !q || compact(b).includes(q)).map((b) =>
      checkRow(b, s.rail.brands.includes(b), (on) => {
        s.rail.brands = on ? [...s.rail.brands, b] : s.rail.brands.filter((x) => x !== b);
        s.shown = PAGE; refresh();
      })));
  };
  brandFilter.addEventListener("input", drawBrands);
  drawBrands();

  const flag = (label, key) => checkRow(label, !!s.rail[key], (on) => { if (on) s.rail[key] = true; else delete s.rail[key]; s.shown = PAGE; refresh(); });
  const group = (title, open, ...kids) => h("details", { open }, h("summary", null, title), h("div", { class: "stack" }, ...kids));
  const reset = h("button", { class: "btn quiet", type: "button", text: "Clear filters" });
  reset.addEventListener("click", () => { s.rail = { brands: [], sources: [] }; s.text = ""; s.manualSort = false; s.sort = "relevance"; s.shown = PAGE; renderBrowse(); });

  rail.append(
    h("div", { class: "rail-head" }, h("h2", { style: "margin:0;font-size:16px" }, "Filters"), reset),
    group("Price (₹, as listed)", true, numberField("Min price", "priceMin"), numberField("Max price", "priceMax")),
    group("Brand", true, brandFilter, brandList),
    group("Memory and battery", true, numberField("RAM at least (GB)", "ramMin"), numberField("Storage at least (GB)", "storageMin"), numberField("Battery at least (mAh)", "batteryMin"), numberField("Wired charging at least (W)", "wiredMin")),
    group("Screen and camera", false, numberField("Screen at least (in)", "displayMin", { step: "0.1" }), numberField("Screen at most (in)", "displayMax", { step: "0.1" }), numberField("Refresh at least (Hz)", "refreshMin"), numberField("Main camera at least (MP)", "mainCamMin"), numberField("Weight at most (g)", "weightMax"), numberField("Released in or after", "yearFrom", { min: "1990" })),
    group("Features", false, flag("5G", "has5g"), flag("NFC", "nfc"), flag("Wireless charging", "wireless")),
    group("Data source", false, ...[0, 1, 2].map((src) => checkRow(SOURCE_LABELS[src], s.rail.sources.includes(src), (on) => {
      s.rail.sources = on ? [...s.rail.sources, src] : s.rail.sources.filter((x) => x !== src);
      s.shown = PAGE; refresh();
    }))));

  const toggle = h("button", { class: "btn rail-toggle", type: "button", "aria-expanded": "false", "aria-controls": "rail", text: "Filters" });
  toggle.addEventListener("click", () => { const open = rail.classList.toggle("open"); toggle.setAttribute("aria-expanded", String(open)); });
  const exportBtn = h("button", { class: "btn", type: "button", text: "Download these as CSV" });
  exportBtn.addEventListener("click", () => { download("teqzo-phones.csv", toCsv(current)); toast(`Downloaded ${current.length} phones`); });

  const heroPhones = featured(3);
  const hero = h("section", { class: "hero" },
    h("div", { class: "hero-copy" },
      h("h1", null, "Every spec. Side by side."),
      h("p", { class: "lede" }, `${DATA.devices.length.toLocaleString("en-US")} phones from ${DATA.brands.length} brands. Search by name, or describe what you want and TEQZO turns it into filters.`),
      search, chips,
      h("div", { class: "try" }, ["samsung 5g under 30k", "iphone 8gb ram", "best battery under 20k", "lightest phone"].map((t) =>
        h("button", { class: "pill", type: "button", onclick: () => { search.value = t; s.text = t; s.shown = PAGE; refresh(); search.focus(); } }, t)))),
    h("div", { class: "hero-art", "aria-hidden": "true" }, heroPhones.map((d, i) => h("a", { class: `hp hp${i}`, href: link(d), tabindex: "-1" }, phoneArt(d, i === 1 ? "front" : "back")))));

  const spot = featured(8);
  const shelf = spot.length ? h("section", { class: "shelf", "aria-label": "Latest flagships" },
    h("h2", null, "Latest flagships"),
    h("div", { class: "cards" }, spot.map((d) => h("a", { class: "card", href: link(d) },
      h("div", { class: "card-art" }, phoneArt(d, "back")),
      h("b", null, fullName(d)),
      h("span", { class: "muted small" }, [d.year, d.chipset].filter(Boolean).join("  ·  ") || " "),
      h("span", { class: "card-price" }, priceText(d) || "Price not listed"))))) : null;

  show(hero, shelf,
    h("div", { class: "browse" }, rail, h("section", null,
      h("div", { class: "bar-head" }, count, h("div", { class: "actions" }, toggle, sortSel, exportBtn)),
      results, more)));
  refresh();
}

// ------------------------------------------------------------------------------------------ phone details

async function loadSpecs(id) {
  const file = specFile(id, DATA.meta.specBuckets);
  if (!specCache.has(file)) {
    specCache.set(file, fetch(`data/specs/${file}`).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }));
  }
  try { return (await specCache.get(file))[id] || []; } catch { specCache.delete(file); return null; }
}

function renderPhone(id) {
  const d = DATA.byId.get(id);
  if (!d) { show(h("p", { class: "empty" }, "That phone is not in the data."), h("a", { class: "btn", href: "#/" }, "Back to phones")); return; }
  setTitle(fullName(d));
  const fact = (label, value, unit) => h("div", { class: "fact" + (value == null ? " missing" : "") }, h("span", { class: "cell-label" }, label), h("b", null, value == null ? "Not listed" : value + (unit || "")));
  const price = priceText(d);
  const specsBox = h("div", { class: "specs" }, h("p", { class: "muted" }, "Loading full specifications…"));
  const pricesBox = h("div", null);
  const rows = PRICES[d.id];
  if (rows && rows.length) {
    const byCountry = new Map();
    for (const [country, cur, variant, amount] of rows) {
      if (!byCountry.has(country)) byCountry.set(country, []);
      byCountry.get(country).push({ cur, variant, amount });
    }
    const sym = { INR: "₹", USD: "$", EUR: "€", AED: "AED ", PKR: "PKR ", CNY: "CN¥" };
    pricesBox.append(h("h2", null, "Launch prices by country"),
      h("p", { class: "muted small" }, "Each price is shown in its own currency exactly as the dataset lists it. Nothing is converted."),
      h("table", null, h("tbody", null, [...byCountry.entries()].map(([country, list]) =>
        h("tr", null, h("th", null, country), h("td", null, list.map((p) => `${sym[p.cur] ?? p.cur + " "}${Math.round(p.amount).toLocaleString("en-US")}${p.variant ? ` (${p.variant})` : ""}`).join("\n")))))));
    pricesBox.querySelector("tbody").querySelectorAll("td").forEach((td) => { td.style.whiteSpace = "pre-line"; });
  }
  const stageBox = h("div", { class: "stage-art" }, phoneArt(d, "pair"));
  const photoNote = h("p", { class: "photo-note" }, "Illustration, not a photo of the real phone.");
  stageBox.append(photoNote);
  wikiPhoto(d).then((photo) => {
    if (!photo || !stageBox.isConnected) return;
    const img = h("img", { class: "photo", alt: `${fullName(d)} (photo from Wikipedia)`, loading: "lazy", referrerpolicy: "no-referrer", src: photo.src });
    img.addEventListener("load", () => {
      stageBox.replaceChildren(img, h("p", { class: "photo-note" }, "Photo: ", h("a", { href: photo.page, target: "_blank", rel: "noopener noreferrer" }, "Wikipedia")));
    });
  });
  const sim = similarTo(DATA.devices, d.id, 6);
  const similarBox = sim.length ? h("div", null, h("h2", null, "Similar phones"),
    h("ul", { class: "similar" }, sim.map((x) => h("li", null,
      h("div", null, h("a", { class: "name", href: link(x.device), style: "font-weight:600" }, fullName(x.device)), h("div", { class: "sub muted small" }, x.reasons.slice(0, 3).join(", "))),
      h("span", { class: "match" }, `${fmtNum(x.similarity)}%`),
      h("span", { class: "muted" }, priceText(x.device) || ""))))) : null;

  show(
    h("div", { class: "crumb" }, h("a", { href: "#/" }, "← All phones")),
    h("div", { class: "detail-head" },
      h("div", { class: "stage" }, stageBox),
      h("div", { class: "detail-title" }, h("h1", null, fullName(d)),
        h("p", { class: "lede" }, [d.year ? (d.yearIsListing ? `Listed ${d.year}` : `Announced ${d.year}`) : "Year not listed", d.chipset].filter(Boolean).join("  ·  ")),
        toggleButtons(d, updateBadges))),
    d.source === 3 || d.curated ? h("p", { class: "note" }, d.source === 3
      ? "This phone was added from public web pages, not from the original datasets. Values the page did not state are shown as Not listed."
      : "Some empty values for this phone were filled from public web pages. Values that other datasets already stated were kept as they were.",
      CURATED[d.id] ? [" ", h("a", { href: CURATED[d.id][0], target: "_blank", rel: "noopener noreferrer" }, "Source page")] : null) : null,
    d.source === 1 ? h("p", { class: "note" }, "From the Flipkart 2026 listings, which carry fewer fields than GSMArena. Values a listing does not state are shown as Not listed.")
      : d.source === 2 ? h("p", { class: "note" }, "From the Mobiles Dataset 2025. It lists launch prices in several countries; other fields may be missing.") : null,
    h("div", { class: "facts" },
      fact("Price", price), fact("RAM", d.ram != null ? fmtNum(d.ram) : null, " GB"), fact("Storage", d.storage != null ? fmtNum(d.storage) : null, " GB"),
      fact("Battery", d.battery != null ? fmtNum(d.battery) : null, " mAh"), fact("Wired charging", d.wired != null ? fmtNum(d.wired) : null, " W"),
      fact("Screen", d.display != null ? fmtNum(d.display) : null, " in"), fact("Refresh rate", d.refresh != null ? fmtNum(d.refresh) : null, " Hz"),
      fact("Main camera", d.mainCam != null ? fmtNum(d.mainCam) : null, " MP"), fact("Selfie camera", d.selfieCam != null ? fmtNum(d.selfieCam) : null, " MP"),
      fact("Weight", d.weight != null ? fmtNum(d.weight) : null, " g"), fact("Operating system", d.os), fact("5G", d.has5g === 1 ? "Yes" : d.has5g === 0 ? "No 5G listed" : null),
      fact("NFC", d.nfc === 1 ? "Yes" : d.nfc === 0 ? "No" : null)),
    pricesBox, similarBox,
    h("h2", null, "Full specifications"), specsBox);

  loadSpecs(d.id).then((specs) => {
    if (location.hash !== `#/phone/${encodeURIComponent(id)}`) return;
    if (specs === null) { specsBox.replaceChildren(h("p", { class: "note" }, "The full specification file could not be loaded. Check your connection and reload.")); return; }
    if (!specs.length) { specsBox.replaceChildren(h("p", { class: "muted" }, "No further specifications are listed for this phone.")); return; }
    const groups = new Map();
    for (const [cat, name, value] of specs) {
      if (!groups.has(cat)) groups.set(cat, []);
      groups.get(cat).push([name, value]);
    }
    specsBox.replaceChildren(...[...groups.entries()].map(([cat, list], i) =>
      h("details", { open: i < 2 }, h("summary", null, cat || "Other"),
        h("table", null, h("tbody", null, list.map(([n, v]) => h("tr", null, h("th", null, n || ""), h("td", null, v))))))));
  });
}

// ------------------------------------------------------------------------------------------ compare / saved

function renderCompare() {
  setTitle("Compare");
  const list = devicesOf(compareIds);
  const box = h("div", { class: "adder" });
  const input = h("input", { class: "field", type: "search", placeholder: "Add a phone by name", "aria-label": "Add a phone to compare", autocomplete: "off" });
  const sug = h("ul", { class: "suggest", hidden: true });
  input.addEventListener("input", () => {
    const q = input.value.trim();
    const hits = q ? sortDevices(DATA.devices.filter((d) => matchesQuery(d, q) && !compareIds.includes(d.id)), "relevance", q).slice(0, 6) : [];
    sug.hidden = !hits.length;
    sug.replaceChildren(...hits.map((d) => h("li", null, h("button", { type: "button", onclick: () => { if (toggleCompare(d.id)) renderCompare(); } }, fullName(d)))));
  });
  box.append(input, sug);

  const out = [h("h1", null, "Compare phones"), h("p", { class: "lede" }, `Pick 2 to ${MAX_COMPARE}. The bar is each phone's value relative to the largest in that row. Blue marks the leader where a higher (or lower, for price) value is clearly better.`), box];
  if (list.length < 2) {
    out.push(h("p", { class: "empty" }, list.length ? "Add one more phone to see the comparison." : "Nothing to compare yet. Use the ⇄ button on any phone, or add one above."));
    if (list.length) out.push(compareHead(list));
  } else {
    const table = compareTable(list);
    const head = h("tr", null, h("th", { scope: "col" }, ""), list.map((d, i) => h("th", { scope: "col" },
      h("a", { href: link(d), style: "text-decoration:none" }, fullName(d)), " ",
      h("button", { class: "btn quiet", type: "button", "aria-label": `Remove ${fullName(d)}`, text: "✕", onclick: () => { toggleCompare(d.id); renderCompare(); } }))));
    const body = table.map((row) => h("tr", null, h("th", { scope: "row" }, row.label),
      row.cells.map((c, i) => h("td", { class: row.leaders.includes(i) ? "lead" : c.text == null ? "none" : "" },
        h("span", { class: "val" }, c.text ?? "Not listed", row.leaders.includes(i) ? h("span", { class: "sr-only" }, " (leads)") : null),
        c.bar != null && row.key !== "year" ? meter(c.bar) : null))));
    out.push(h("div", { class: "cmp-wrap" }, h("table", { class: "cmp" }, h("thead", null, head), h("tbody", null, body))));
    const v = verdict(list, table);
    out.push(h("div", { class: "verdict" }, h("h3", null, "Summary"), v.lines.map((l) => h("p", null, l))));
  }
  out.push(h("p", null, h("button", { class: "btn quiet", type: "button", text: "Clear comparison", onclick: () => { compareIds = []; store.set("teqzo.compare", []); updateBadges(); renderCompare(); } })));
  show(...out);
}
function compareHead(list) { return h("ul", { class: "similar" }, list.map((d) => h("li", null, h("a", { href: link(d) }, fullName(d)), h("span"), h("span")))); }

function renderSaved() {
  setTitle("Shortlist");
  const list = devicesOf(savedIds);
  const out = [h("h1", null, "Your shortlist"), h("p", { class: "lede" }, "Saved on this device only, in your browser. Clearing site data removes it.")];
  if (!list.length) out.push(h("p", { class: "empty" }, "Nothing saved yet. Use the ♥ button on any phone."));
  else {
    out.push(h("div", { class: "actions", style: "margin-bottom:12px" },
      h("button", { class: "btn primary", type: "button", text: "Compare first 4", onclick: () => { compareIds = list.slice(0, MAX_COMPARE).map((d) => d.id); store.set("teqzo.compare", compareIds); updateBadges(); location.hash = "#/compare"; } }),
      h("button", { class: "btn", type: "button", text: "Download as CSV", onclick: () => download("teqzo-shortlist.csv", toCsv(list)) }),
      h("button", { class: "btn quiet", type: "button", text: "Clear shortlist", onclick: () => { savedIds = []; store.set("teqzo.saved", []); updateBadges(); renderSaved(); } })));
    out.push(h("ol", { class: "rows" }, list.map((d) => phoneRow(d, () => { if (!savedIds.includes(d.id)) renderSaved(); }))));
  }
  show(...out);
}

// ------------------------------------------------------------------------------------------ top lists, insights, about

const topState = { key: LEADERBOARDS[0].key, budget: "" };
function renderTop() {
  setTitle("Top lists");
  const budget = h("input", { class: "field", type: "number", min: "0", placeholder: "Budget in ₹ (optional)", "aria-label": "Maximum price in rupees", style: "max-width:240px" });
  budget.value = topState.budget;
  const listBox = h("div");
  const tabs = h("div", { class: "tabs", role: "group", "aria-label": "List" });
  const draw = () => {
    const b = topState.budget === "" ? null : Number(topState.budget);
    const { board, items } = leaderboard(DATA.devices, topState.key, b);
    tabs.replaceChildren(...LEADERBOARDS.map((x) => h("button", { class: "btn", type: "button", "aria-pressed": String(x.key === topState.key), onclick: () => { topState.key = x.key; draw(); } }, x.title)));
    const shown = items.slice(0, 15);
    const value = (d) => ({ battery: d.battery != null ? `${fmtNum(d.battery)} mAh` : "", ram: `${fmtNum(d.ram)} GB`, camera: `${fmtNum(d.mainCam)} MP`, weight: `${fmtNum(d.weight)} g`, charging: `${fmtNum(d.wired)} W`, price: priceText(d), year: String(d.year ?? "") })[board.show];
    listBox.replaceChildren(h("h2", null, board.title + (b != null ? ` under ${inr(b)}` : "")), board.note ? h("p", { class: "muted small" }, board.note) : null,
      shown.length ? h("ol", { class: "rows" }, shown.map((d, i) => h("li", { class: "row", style: "grid-template-columns:minmax(0,1fr) auto auto" },
        h("div", null, h("span", { class: "rank" }, String(i + 1)), h("a", { class: "name", href: link(d), style: "display:inline" }, fullName(d)), sourceTag(d)),
        h("b", null, value(d)), toggleButtons(d))))
      : h("p", { class: "empty" }, "No phones with a listed value match this budget."));
  };
  budget.addEventListener("input", () => { topState.budget = budget.value; draw(); });
  show(h("h1", null, "Top lists"), h("p", { class: "lede" }, "Phone-sized screens only (4 to 8.5 in). A phone must state the value to appear."), budget, tabs, listBox);
  draw();
}

function hbars(items, fmt) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return items.map((i) => h("div", { class: "hbar" }, h("span", null, i.label), meter(i.value / max), h("span", null, fmt ? fmt(i.value) : String(i.value))));
}

function renderInsights() {
  setTitle("Insights");
  const ins = insights(DATA.devices);
  show(h("h1", null, "Insights"), h("p", { class: "lede" }, "A summary of what the data contains. Counts are phones in this dataset, not market share."),
    h("div", { class: "cols" },
      h("div", null, h("h2", null, "Phones per brand"), ...hbars(ins.brands.map((b) => ({ label: b.brand, value: b.devices })))),
      h("div", null, h("h2", null, "Phones per year"), ...hbars(ins.years.map((y) => ({ label: String(y.year), value: y.count })))),
      h("div", null, h("h2", null, "Phones by listed price (₹)"), ...hbars(ins.bands.map((b) => ({ label: b.label, value: b.count })))),
      h("div", null, h("h2", null, "Average by brand"), h("table", null,
        h("thead", null, h("tr", null, ["Brand", "Avg price", "Avg battery", "Avg RAM"].map((t) => h("th", { scope: "col", style: "width:auto" }, t)))),
        h("tbody", null, ins.brands.map((b) => h("tr", null, h("td", null, b.brand), h("td", null, b.avgPrice != null ? inr(b.avgPrice) : "–"), h("td", null, b.avgBattery != null ? `${Math.round(b.avgBattery)} mAh` : "–"), h("td", null, b.avgRam != null ? `${fmtNum(Math.round(b.avgRam * 10) / 10)} GB` : "–"))))))));
}

function renderAbout() {
  setTitle("About");
  const by = DATA.meta.bySource;
  show(h("h1", null, "About TEQZO"),
    h("p", { class: "lede" }, "TEQZO searches and compares phone specifications. This page is the web edition: it runs in your browser and sends nothing anywhere. The full application is a core Java and MySQL console program."),
    h("h2", null, "Where the data comes from"),
    h("ul", null, Object.entries(SOURCE_LABELS).map(([k, label]) => h("li", null, `${label}: ${(by[k] || 0).toLocaleString("en-US")} phones`))),
    h("p", { class: "muted" }, `Data exported on ${DATA.meta.generated}.`),
    h("h2", null, "How to read it"),
    h("ul", null,
      h("li", null, "Not listed means the source does not state it. TEQZO never fills a gap with a guess, so a phone with a missing value never matches a filter on that value."),
      h("li", null, "Prices are in the currency the dataset lists. Rupee and dollar prices are never converted into each other."),
      h("li", null, "5G is only reported as Yes when a source states it. For the two smaller datasets, a phone that does not say 5G is shown as not listed."),
      h("li", null, "Smart search is rule based, not AI. It shows chips for exactly what it understood. Anything else is searched as part of the name."),
      h("li", null, "The comparison summary counts rows where one phone is clearly ahead. It cannot say which phone is best for you."),
      h("li", null, "Camera comparisons are megapixels only, which does not measure image quality.")),
    h("h2", null, "Your data"),
    h("p", null, "Your compare list and shortlist are kept in this browser's local storage and never leave your device."));
}

// ------------------------------------------------------------------------------------------ photos (Wikipedia)

const photoCache = new Map();
/** Looks the phone up on Wikipedia and returns its lead image only when the page is about a phone. Needs internet. */
function wikiPhoto(d) {
  if (photoCache.has(d.id)) return photoCache.get(d.id);
  const title = fullName(d).replace(/\s+/g, "_");
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 6000);
  const p = fetch(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}?redirect=true`, { signal: ctl.signal })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => {
      if (!j || j.type !== "standard" || !j.thumbnail || !/phone|handset|smartphone|mobile/i.test(`${j.description || ""} ${j.extract || ""}`.slice(0, 400))) return null;
      return { src: j.thumbnail.source, page: j.content_urls && j.content_urls.desktop ? j.content_urls.desktop.page : `https://en.wikipedia.org/wiki/${encodeURIComponent(title)}` };
    })
    .catch(() => null)
    .finally(() => clearTimeout(timer));
  photoCache.set(d.id, p);
  return p;
}

// ------------------------------------------------------------------------------------------ light / dark

const themeBtn = document.getElementById("theme");
function currentTheme() {
  try { const t = localStorage.getItem("teqzo.theme"); if (t === "light" || t === "dark") return t; } catch { /* ignore */ }
  return window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}
function applyTheme(t, save) {
  document.documentElement.dataset.theme = t;
  themeBtn.setAttribute("aria-pressed", String(t === "dark"));
  themeBtn.setAttribute("aria-label", t === "dark" ? "Switch to light theme" : "Switch to dark theme");
  themeBtn.querySelector("span").textContent = t === "dark" ? "Light" : "Dark";
  if (save) { try { localStorage.setItem("teqzo.theme", t); } catch { /* ignore */ } }
}
themeBtn.addEventListener("click", () => applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark", true));
applyTheme(currentTheme(), false);

// ------------------------------------------------------------------------------------------ Ask TEQZO (helper panel)

const helper = (() => {
  const log = h("div", { class: "chat-log", role: "log", "aria-live": "polite" });
  const input = h("input", { class: "field", type: "text", placeholder: "Ask about phones…", "aria-label": "Ask TEQZO", autocomplete: "off", maxlength: "200" });
  const form = h("form", { class: "chat-form" }, input, h("button", { class: "btn primary", type: "submit" }, "Ask"));
  const sugg = h("div", { class: "try" }, SUGGESTIONS.map((t) => h("button", { class: "pill", type: "button", onclick: () => ask(t) }, t)));
  const close = h("button", { class: "icon-btn", type: "button", "aria-label": "Close helper", text: "✕" });
  const panel = h("section", { class: "chat", id: "chat", role: "dialog", "aria-label": "Ask TEQZO", hidden: true },
    h("header", null, h("div", null, h("b", null, "Ask TEQZO"), h("span", { class: "muted small" }, "Rule-based helper. It answers from this site's data and is not an AI model.")), close),
    log, sugg, form);
  const open = h("button", { class: "fab", type: "button", "aria-expanded": "false", "aria-controls": "chat" }, h("span", { "aria-hidden": "true" }, "✦"), " Ask TEQZO");
  document.body.append(open, panel);

  function say(who, ...kids) { const m = h("div", { class: `msg ${who}` }, ...kids); log.append(m); log.scrollTop = log.scrollHeight; return m; }
  function ask(text) {
    const t = String(text || "").trim();
    if (!t || !DATA) return;
    say("me", t);
    input.value = "";
    const r = answer(t, DATA.devices, DATA.brands);
    const body = [h("p", null, r.text)];
    if (r.devices.length) body.push(h("ul", { class: "mini" }, r.devices.map((d) => h("li", null,
      h("span", { class: "mini-art" }, phoneArt(d, "back")), h("a", { href: link(d), onclick: () => hide() }, fullName(d)), h("span", { class: "muted" }, priceText(d) || "")))));
    if (r.action && r.action.type === "compare") body.push(h("button", { class: "btn", type: "button", onclick: () => { compareIds = r.action.ids.slice(0, MAX_COMPARE); store.set("teqzo.compare", compareIds); updateBadges(); hide(); location.hash = "#/compare"; } }, "Open comparison"));
    if (r.action && r.action.type === "browse") body.push(h("button", { class: "btn", type: "button", onclick: () => { browseState.text = r.action.text; browseState.shown = PAGE; hide(); if (location.hash && location.hash !== "#/") location.hash = "#/"; else renderBrowse(); } }, "See all results in Phones"));
    say("bot", ...body);
  }
  function show() {
    panel.hidden = false; open.setAttribute("aria-expanded", "true"); open.hidden = true;
    if (!log.children.length) say("bot", h("p", null, "Hi. Ask for a phone by budget, battery or brand, compare two phones, or find similar ones."));
    input.focus();
  }
  function hide() { panel.hidden = true; open.hidden = false; open.setAttribute("aria-expanded", "false"); open.focus({ preventScroll: true }); }
  open.addEventListener("click", show);
  close.addEventListener("click", hide);
  panel.addEventListener("keydown", (e) => { if (e.key === "Escape") hide(); });
  form.addEventListener("submit", (e) => { e.preventDefault(); ask(input.value); });
  return { show };
})();

// ------------------------------------------------------------------------------------------ router

const ROUTES = { "": ["browse", renderBrowse], top: ["top", renderTop], compare: ["compare", renderCompare], saved: ["saved", renderSaved], insights: ["insights", renderInsights], about: ["about", renderAbout] };

function route() {
  if (!DATA) return;
  const parts = location.hash.replace(/^#\/?/, "").split("/");
  let name = parts[0] || "";
  if (name === "phone" && parts[1]) {
    let id; try { id = decodeURIComponent(parts.slice(1).join("/")); } catch { id = ""; }
    markNav("browse");
    renderPhone(id);
  } else {
    const [nav, fn] = ROUTES[name] || ROUTES[""];
    markNav(nav);
    fn();
  }
  window.scrollTo(0, 0);
  view.focus({ preventScroll: true });
}

function markNav(active) {
  document.querySelectorAll("#nav a").forEach((a) => {
    if (a.dataset.route === active) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
  });
}

async function start() {
  try {
    const [dev, prices, curated] = await Promise.all([
      fetch("data/devices.json").then((r) => { if (!r.ok) throw new Error("devices " + r.status); return r.json(); }),
      fetch("data/prices.json").then((r) => (r.ok ? r.json() : {})).catch(() => ({})),
      fetch("data/curated.json").then((r) => (r.ok ? r.json() : {})).catch(() => ({})),
    ]);
    const devices = loadDevices(dev);
    DATA = { meta: dev.meta, devices, byId: new Map(devices.map((d) => [d.id, d])), brands: [...new Set(devices.map((d) => d.brand).filter(Boolean))].sort((a, b) => a.localeCompare(b)) };
    PRICES = prices;
    CURATED = curated;
    updateBadges();
    window.addEventListener("hashchange", route);
    route();
  } catch (e) {
    show(h("p", { class: "note" }, "The phone data could not be loaded. If you opened index.html directly from a folder, serve the site over http instead (for example: python -m http.server inside the site folder), or use the GitHub Pages address."));
  }
}
start();
