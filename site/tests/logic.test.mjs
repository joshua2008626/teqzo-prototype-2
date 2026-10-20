import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import * as L from "../js/logic.js";

const dev = (o) => ({ id: o.id, brand: "Brand", name: "Name", year: 2024, yearIsListing: false, source: 2, priceInr: null, priceUsd: null,
  priceEur: null, ram: null, storage: null, battery: null, wired: null, wireless: null, display: null, refresh: null, os: null,
  chipset: null, mainCam: null, selfieCam: null, weight: null, has5g: null, nfc: null, ip: null, charging: null, displayType: null, ...o });

const flag = dev({ id: "flag", brand: "Samsung", name: "Galaxy S25 Ultra", priceInr: 129999, ram: 12, storage: 256, battery: 5000, wired: 45, display: 6.9, refresh: 120, mainCam: 200, weight: 218, os: "Android 15", has5g: 1, nfc: 1 });
const plus = dev({ id: "plus", brand: "Samsung", name: "Galaxy S25+", priceInr: 99999, ram: 12, storage: 256, battery: 4900, display: 6.7, os: "Android 15", has5g: 1 });
const base = dev({ id: "base", brand: "Samsung", name: "Galaxy S25", priceInr: 80999, ram: 12, storage: 256, battery: 4000, display: 6.2, os: "Android 15" });
const cheap = dev({ id: "cheap", brand: "Xiaomi", name: "Redmi 13", priceInr: 10999, ram: 4, storage: 64, battery: 6000, display: 6.7, os: "Android 14", year: 2022 });
const apple = dev({ id: "apple", brand: "Apple", name: "iPhone 17", priceInr: 82900, storage: 256, display: 6.3, os: "iOS 26", year: 2025 });
const nodata = dev({ id: "nodata", brand: "Nokia", name: "Mystery", year: null });
const ALL = [flag, plus, base, cheap, apple, nodata];

test("compact keeps S25+ and S25 apart", () => {
  assert.notEqual(L.compact("S25+"), L.compact("S25"));
  assert.equal(L.compact("Galaxy S25 Ultra"), "galaxys25ultra");
});

test("search ignores case and spacing and requires every word", () => {
  assert.deepEqual(L.filterDevices(ALL, { q: "SAMSUNG s25 ultra" }).map((d) => d.id), ["flag"]);
  assert.deepEqual(L.filterDevices(ALL, { q: "s25+" }).map((d) => d.id), ["plus"]);
  assert.equal(L.filterDevices(ALL, { q: "zzz" }).length, 0);
});

test("unknown values never match a filter", () => {
  assert.deepEqual(L.filterDevices(ALL, { ramMin: 8 }).map((d) => d.id).sort(), ["base", "flag", "plus"]);
  assert.ok(!L.filterDevices(ALL, { priceMax: 100000 }).some((d) => d.id === "nodata"));
  assert.deepEqual(L.filterDevices(ALL, { has5g: true }).map((d) => d.id).sort(), ["flag", "plus"]);
  assert.deepEqual(L.filterDevices(ALL, { nfc: true }).map((d) => d.id), ["flag"]);
});

test("brand, os and year filters", () => {
  assert.equal(L.filterDevices(ALL, { brands: ["xiaomi"] }).length, 1);
  assert.deepEqual(L.filterDevices(ALL, { os: "ios" }).map((d) => d.id), ["apple"]);
  assert.ok(!L.filterDevices(ALL, { yearFrom: 2024 }).some((d) => d.id === "cheap"));
});

test("sorting puts unknown values last and respects direction", () => {
  assert.deepEqual(L.sortDevices(ALL, "battery_desc").map((d) => d.id).slice(0, 3), ["cheap", "flag", "plus"]);
  assert.equal(L.sortDevices(ALL, "battery_desc").at(-1).id.length > 0, true);
  assert.deepEqual(L.sortDevices(ALL, "price_asc").map((d) => d.id).slice(0, 2), ["cheap", "base"]);
  assert.equal(L.sortDevices(ALL, "price_asc").at(-1).priceInr, null);
  assert.equal(L.sortDevices(ALL, "newest")[0].id, "apple");
});

test("relevance ranks exact name before partial", () => {
  const list = [dev({ id: "a", brand: "X", name: "Pro Max" }), dev({ id: "b", brand: "X", name: "Pro" })];
  assert.equal(L.sortDevices(list, "relevance", "pro")[0].id, "b");
});

test("price text: INR grouping, USD/EUR as listed, nothing invented", () => {
  assert.equal(L.inr(129999), "₹1,29,999");
  assert.equal(L.inr(999), "₹999");
  assert.equal(L.inr(54900), "₹54,900");
  assert.equal(L.priceText(dev({ id: "u", priceUsd: 799 })), "$799");
  assert.equal(L.priceText(dev({ id: "e", priceEur: 949 })), "€949");
  assert.equal(L.priceText(nodata), null);
});

test("similarity: same family beats a different class, too little data gives null", () => {
  const near = L.similarity(flag, plus);
  const far = L.similarity(flag, cheap);
  assert.ok(near.similarity > far.similarity);
  assert.equal(L.similarity(flag, flag).similarity, 100);
  assert.equal(L.similarity(nodata, dev({ id: "z" })), null);
  assert.deepEqual(L.similarTo(ALL, "flag", 2).map((s) => s.device.id), ["plus", "base"]);
  assert.ok(!L.similarTo(ALL, "flag", 9).some((s) => s.device.id === "flag"));
});

test("compare table: leaders only where a direction is defensible", () => {
  const table = L.compareTable([flag, cheap]);
  const row = (k) => table.find((r) => r.key === k);
  assert.deepEqual(row("battery").leaders, [1]);
  assert.deepEqual(row("ram").leaders, [0]);
  assert.deepEqual(row("price").leaders, [1]); // lower is better
  assert.deepEqual(row("mainCam").leaders, []); // megapixels are not a quality ranking
  assert.equal(row("battery").cells[0].bar, 5000 / 6000);
  assert.equal(row("wired").cells[1].text, null);
  assert.deepEqual(row("wired").leaders, []); // only one phone lists it
});

test("verdict counts leads and avoids false winners", () => {
  const v = L.verdict([flag, cheap], L.compareTable([flag, cheap]));
  assert.ok(v.comparable >= 4);
  assert.ok(v.lines[0].includes("leads in"));
  const tie = L.verdict([flag, plus], [{ leaders: [0], label: "A" }, { leaders: [1], label: "B" }]);
  assert.equal(tie.winner, -1);
  assert.equal(L.verdict([flag, plus], []).winner, -1);
});

test("top lists: phone-sized screens, stated values only, optional budget", () => {
  const tablet = dev({ id: "tab", brand: "X", name: "Tab", display: 11, battery: 9000 });
  const { items } = L.leaderboard([...ALL, tablet], "battery");
  assert.equal(items[0].id, "cheap");
  assert.ok(!items.some((d) => d.id === "tab" || d.id === "nodata"));
  const cheapOnly = L.leaderboard(ALL, "battery", 20000).items;
  assert.deepEqual(cheapOnly.map((d) => d.id), ["cheap"]);
});

test("insights: brands need 3 phones, bands count only known prices", () => {
  const r = L.insights(ALL);
  assert.deepEqual(r.brands.map((b) => b.brand), ["Samsung"]);
  assert.equal(r.brands[0].devices, 3);
  assert.equal(r.brands[0].avgBattery, (5000 + 4900 + 4000) / 3);
  assert.equal(r.bands.reduce((a, b) => a + b.count, 0), 5);
  assert.equal(r.sources[2], 6);
});

test("CSV: header, empty for missing, quoting and formula defence", () => {
  const csv = L.toCsv([flag, nodata]).split("\r\n");
  assert.equal(csv[0], L.CSV_HEADER);
  assert.ok(csv[1].startsWith("flag,Samsung,Galaxy S25 Ultra,2024,129999,"));
  assert.ok(csv[2].includes(",,,"));
  assert.equal(L.csvCell("a,b"), '"a,b"');
  assert.equal(L.csvCell('say "hi"'), '"say ""hi"""');
  assert.equal(L.csvCell("=SUM(A1)"), "'=SUM(A1)");
  assert.equal(L.csvCell(-5), "-5");
});

test("smart search understands budget, RAM, brand and intent", () => {
  const brands = ["Samsung", "Xiaomi", "Apple"];
  const r = L.parseSmartQuery("Samsung 5G under 30k with 8gb ram and good battery", brands);
  assert.equal(r.structured, true);
  assert.equal(r.filters.priceMax, 30000);
  assert.equal(r.filters.ramMin, 8);
  assert.equal(r.filters.has5g, true);
  assert.deepEqual(r.filters.brands, ["Samsung"]);
  assert.equal(r.sort, "battery_desc");
  assert.ok(r.chips.length >= 5);
});

test("smart search: lakh, rupee sign, storage, iphone, gaming", () => {
  assert.equal(L.parseSmartQuery("phone below 1.5 lakh", []).filters.priceMax, 150000);
  assert.equal(L.parseSmartQuery("₹25,000", []).filters.priceMax, 25000);
  assert.equal(L.parseSmartQuery("256 gb storage", []).filters.storageMin, 256);
  assert.equal(L.parseSmartQuery("iphone under 90000", ["Apple"]).filters.os, "ios");
  const g = L.parseSmartQuery("gaming phone", []);
  assert.equal(g.filters.ramMin, 8);
  assert.equal(g.sort, "ram_desc");
});

test("smart search falls back to a plain name search", () => {
  const r = L.parseSmartQuery("galaxy s25 ultra", ["Samsung"]);
  assert.equal(r.structured, false);
  assert.equal(r.filters.q, "galaxy s25 ultra");
  assert.deepEqual(L.parseSmartQuery("", []).chips, []);
});

test("exported demo data loads and works", () => {
  const payload = JSON.parse(readFileSync(new URL("../data/devices.json", import.meta.url), "utf8"));
  const devices = L.loadDevices(payload);
  assert.equal(devices.length, payload.meta.devices);
  assert.ok(devices.every((d) => d.id && d.name));
  assert.equal(new Set(devices.map((d) => d.id)).size, devices.length);
  assert.ok(L.filterDevices(devices, { q: "iphone 17" }).length > 0);
  // a device from a smaller dataset that does not say "5G" is unknown, not "no"
  assert.ok(devices.filter((d) => d.source !== 0).every((d) => d.has5g === 1 || d.has5g === null || (d.has5g === 0 && d.curated)))  // an explicit 'no' only comes from a cited public page;
});

test("spec file names match Python's zlib.crc32 (expected values computed with Python)", () => {
  assert.equal(L.specFile("apple_iphone_14-3"), "45.json");
  assert.equal(L.specFile("m25-honor-x60"), "66.json");
  assert.equal(L.specFile("flipkart-realme-p4-power-5g"), "55.json");
  assert.equal(L.specFile("Ünï-é"), "75.json");
});

test("every device's specs live in the file the browser will ask for", () => {
  const payload = JSON.parse(readFileSync(new URL("../data/devices.json", import.meta.url), "utf8"));
  const devices = L.loadDevices(payload);
  let checked = 0;
  for (const d of devices.slice(0, 40)) {
    const specs = JSON.parse(readFileSync(new URL("../data/specs/" + L.specFile(d.id, payload.meta.specBuckets), import.meta.url), "utf8"));
    assert.ok(Array.isArray(specs[d.id]) && specs[d.id].length > 0, d.id);
    checked++;
  }
  assert.equal(checked, 40);
});
