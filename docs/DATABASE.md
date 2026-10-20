# Database

Database name: `tech_finder` (MySQL 8, utf8mb4).

## Tables

| Table | Origin | Used for |
|-------|--------|----------|
| `brand` | original import | brand list (`id` like `samsung-phones-9.php`) |
| `device` | original import | name, source URL, thumbnail URL |
| `device_specification` | original import | every raw specification row (detail page, benchmarks) |
| `device_core_v4` | your processing script | normalised numeric specs used for filtering and ranking |
| `device_extras_v1` | `database/build_device_extras.py` | corrected / added values, see below |
| `device_price_v1` | `database/import_mobiles2025.py` | launch prices per country and variant (optional) |

The application **only reads** the first four. `build_device_extras.py` drops and recreates only
`device_extras_v1`, so it can be re-run at any time. No existing table is altered.

## Why a new table instead of changing `device_core_v4`

Checks against the real data found things the application should not guess around:

1. **Wireless charging wattage was never extracted.** The v4 regex only reads watts *after* the word "wireless",
   but the source writes `18W wireless` / `15W magnetic wireless`. Result: `wireless_charging_w` is NULL for all
   11,543 devices. `device_extras_v1.wireless_charging_w` re-parses the untouched `Battery > Charging` text
   (8 devices state a wattage; 108 state unconditional wireless support). Qualified statements
   (`(optional)`, `(market dependent)`) are stored as `CONDITIONAL` and are never counted as "supported".
2. **`device_core_v4.nfc` is text** (`Yes`, `No`, `Yes (market/region dependent)`); the extras table adds a
   clean tri-state `nfc_supported` (1 / 0 / NULL).
3. **Prices live in free text** (`€ 1,099.00 / $ 899.99 / ₹ 89,900`, `About 330 EUR`). They are parsed per
   currency exactly as listed: 7,049 EUR, 428 USD, 361 INR. Nothing is converted.
4. Release year, status, CPU core count/clock, resolution, Wi-Fi generation, Bluetooth version, USB-C and a
   compact lower-case search string (`appleiphone15pro`) are added so search and ranking stay in SQL.

Other facts about the data worth knowing: 680 entries have no specification rows at all
(`has_spec_data = 0`); `has_5g = 0` means "no 5G bands listed"; RAM/storage in `device_core_v4` describe the
highest-RAM configuration of a device that is sold in several; refresh rate is known for only ~740 devices.

## Flipkart 2026 dataset (newer phones)

`database/import_flipkart.py` adds the phones of `flipkart_mobiles_dataset_2026.csv` as ordinary rows of `brand`,
`device` and `device_specification`, written in the same category/specification/value text format GSMArena uses.
Their ids start with `flipkart-` (new brands: `flipkart-brand-`). The existing build scripts then parse them like any
other device, so re-running those scripts never loses them. Original rows are never modified; the importer deletes and
re-creates only its own `flipkart-` rows, so it can be run again at any time.

What the importer does to the CSV (33,600 rows → about 190 phone models):

1. Removes exact duplicate rows (the CSV repeats the same listing thousands of times).
2. Keeps only smartphones: the title must contain `(colour, storage)`; feature phones, accessories, rows without a
   price and screens outside 4–8.5 inches are dropped.
3. Cleans titles ("Charger in the Box", "Locked with Airtel Prepaid", glued colour/storage text, "Samsung M36" vs
   "Galaxy M36", "Note-14" vs "Note 14") and merges colour/RAM/storage variants into one device per model.
4. Price = lowest listed INR price over the variants (shown per variant on the detail page). Rating (weighted) and
   colours are kept as text.
5. Zero values (RAM 0, battery 0, 0MP camera, "NA Processor") become *not listed*, never 0.
6. REDMI and POCO phones go under Xiaomi, CMF under Nothing, MOTOROLA under Motorola, and so on; brands that do not
   exist yet are created. A model that already exists in the GSMArena data (same brand and name, ignoring "5G") is
   skipped and counted in the report.
7. The listing does not state the operating system. It is filled in only for brands that sell Android phones only
   (Samsung, Xiaomi, realme, vivo, OPPO, OnePlus, Motorola, Google, ...) and for Apple (iOS), and the text says
   "(inferred from brand)". Other brands stay not listed.
8. "5G" is set only when the listing title says 5G; for the others the app shows 5G as *not listed* (not "no 5G").

Columns added to `device_extras_v1`: `listed_year` (2026 for these phones: the year the listing was seen, not a
launch year) and `data_source` (`FLIPKART_2026`, NULL for GSMArena). "Newest first" ordering uses
`COALESCE(announced_year, listed_year)`; the "Released from" filter in Browse stays strict (announced year only), while
the assistant also accepts phones listed in the requested year and marks their launch date as unverified.

One behaviour change in `build_device_core_v4.py`: when a memory line gives storage but no RAM, the storage is now kept
(RAM stays NULL) instead of dropping both. Lines with RAM and storage behave exactly as before.

`build_device_core_v4.py` now reads `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME` from the environment
when they are set (it still asks for anything missing), so it can run non-interactively.

## Setting up on a new machine

1. Create the database and original tables: `database/pipeline/gsmarena_to_mysql.py` (needs the GSMArena SQLite
   file, which is not distributed with this project).
2. Build `device_core_v4`: `database/pipeline/build_device_core_v4.py`.
3. Build the extras: `python database/build_device_extras.py`.
4. Optional, newer phones: `python database/import_flipkart.py path/to/flipkart_mobiles_dataset_2026.csv`
   (it re-runs steps 2 and 3 for you).

All scripts read `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME` from the environment
(`build_device_core_v4.py` asks interactively). No password is stored in the repository.

If you already have a MySQL dump of the finished database, import it and run only step 3.

## Indexes

Search and filters scan at most ~11.5k narrow rows, so heavy indexing is not needed. `device_extras_v1` has
indexes on year, status, INR/EUR price and wireless status. Queries use the primary keys for detail lookups and
batch `IN (...)` lookups for comparison (no N+1 queries).


## Mobiles Dataset 2025 and the import order

`python database/import_all.py --flipkart <csv> --mobiles2025 <csv>` runs, in this order: the Flipkart import, the
Mobiles 2025 import, then `build_device_core_v4.py` and `build_device_extras.py` once.

* The Flipkart import removes the `m25-` rows first (they were matched against the data as it was), so the Mobiles
  2025 import must run after it. `import_all.py` does that.
* Mobiles 2025 models are matched with every device already in the database, GSMArena and Flipkart. Matching ignores
  "5G" (GSMArena writes "Galaxy S22 5G"); only when the dataset itself lists both "X" and "X 5G" are they kept as two
  phones. A matched model is not added again; its launch prices are stored in `device_price_v1` under the existing id.
* New devices get ids `m25-<brand>-<name>`, brands `m25-brand-...`, `device_extras_v1.data_source = 'MOBILES_2025'`.
* Tablets and watches (by name or by screen size outside 4-8.5 inches) are dropped; exact duplicate rows are dropped;
  storage variants are merged into one device with all variant prices kept.
* `build_device_core_v4.py` `parse_memory` now treats "8GB RAM" without a storage figure as RAM only (before, the
  number was also read as storage). This is needed for models that list RAM but no storage size.
