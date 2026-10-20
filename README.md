# TEQZO

A phone/gadget finder written in **core Java** (no Spring, no Maven, no web frontend) on top of **MySQL** using
**JDBC**. It is a console application: search, filter by specification, browse by brand, view full specifications,
compare up to four devices side by side (with a plain-words verdict), see launch prices in five countries, find
similar phones, keep a saved shortlist, export results to CSV, browse top lists and market insights, and ask an
assistant for recommendations.
Data preparation is done by **Python scripts** (`database/`).

```
Console menu (ui/)  ->  services (service/, recommendation/, ai/)  ->  repositories (repository/)  ->  JDBC  ->  MySQL
                                       ^ plain Java classes, interfaces and records; AppContext wires them by hand
```

## Read this first: data limitations
* The GSMArena part of the data ends in **2022**. Newer phones come from two smaller datasets:
  the **2026 Flipkart dataset** (about 190 models, `database/import_flipkart.py`) and the **Mobiles Dataset 2025**
  (about 590 models from 2014-2025 with weight and launch prices in Pakistan, India, China, USA and Dubai,
  `database/import_mobiles2025.py`). They have far fewer fields than GSMArena, so those phones show **Not listed**
  for charging speed, NFC, refresh rate... Nothing is invented. A phone that is already in the database is never
  added twice (its entry is kept; only the extra launch prices are attached).
* The Mobiles Dataset 2025 is third-party data that was not checked; a few values look wrong (for example an
  iPhone 16 with an A17 chip). If another source has the same phone, that source wins.
* Prices are sparse; budget filters use INR prices (EUR can be converted with `PRICE_EUR_TO_INR`, labelled approximate).
* No benchmark data: "performance" is estimated from RAM, CPU and core count; "camera" from megapixels only.
* "Not listed" means the dataset does not say; it does not mean the feature is absent.

## Requirements
* JDK 17 or newer (developed on 21), MySQL 8, Python 3.9+ (data scripts only)
* **MySQL Connector/J** jar: download "Platform Independent" from https://dev.mysql.com/downloads/connector/j/ ,
  unzip, and copy `mysql-connector-j-*.jar` into the `lib/` folder.

## Quick start
1. **Database** (see [docs/DATABASE.md](docs/DATABASE.md)):
   ```bash
   pip install mysql-connector-python
   export DB_HOST=localhost DB_PORT=3306 DB_NAME=tech_finder DB_USERNAME=root DB_PASSWORD='your-password'
   python database/build_device_extras.py
   python database/import_all.py --flipkart path/to/flipkart_mobiles_dataset_2026.csv \
                                 --mobiles2025 path/to/Mobiles_Dataset_2025.csv   # adds both datasets, rebuilds once
   ```
   (Windows: use `set DB_HOST=...` instead of `export`, or put the values in `.env`.)
2. **Settings**: copy `.env.example` to `.env` and fill in the password.
3. **Run**: `./run.sh` (Linux/macOS) or `run.bat` (Windows). The first run compiles into `out/`.
   `./run.sh --check` only tests the database connection.
4. **Tests** (no database needed): `./test.sh` or `test.bat` (168 Java tests); Python: `python -m unittest discover -s database/tests` (71 tests).

Manual commands: `javac -d out $(find src -name '*.java')` then `java -cp "out:lib/*" com.teqzo.Main`
(on Windows the classpath separator is `;`).

## Website (GitHub Pages)

The Java program is a console application, so it cannot run on GitHub Pages (Pages only serves files). The `site/` folder is a separate **static website** with the same data and rules, which runs entirely in the visitor's browser: search (including plain-English requests such as "samsung 5g under 30k"), filters, phone details with launch prices by country, similar phones, compare with bars, shortlist, top lists and insights. The site also has a light/dark switch, generated phone illustrations, optional Wikipedia photos on the phone page (needs internet; only shown when Wikipedia has a page for that phone) and **Ask TEQZO**, a rule-based helper (not an AI model) that answers from the site's data. The Java application is unchanged and remains the exam project.

1. Export your database to JSON (this overwrites `site/data/`, which currently holds demo data only):
   `python database/export_site_data.py` (uses the same `DB_*` variables as the importers; `--sqlite FILE` also works).
2. Try it locally: `cd site && python -m http.server 8000`, then open http://localhost:8000 (opening index.html by double-click will not work, browsers block loading the data files).
3. Publish: push to GitHub, then Settings > Pages > Source: **GitHub Actions**. `.github/workflows/pages.yml` publishes `site/` on every push to `main`.
4. Tests: `cd site && node --test tests/logic.test.mjs tests/assistant.test.mjs`.

**Big-brand layer.** `database/data/curated_phones.csv` holds 487 phones from Apple, Samsung, Google, OnePlus, Xiaomi, Nothing, Motorola, Vivo, Oppo, Realme, iQOO, Honor, Sony, Huawei, Infinix, Lenovo, Nokia, Motorola and others, each row with the source page it was read from. `export_site_data.py` merges it: it only fills empty cells (it never overwrites a value another dataset states), adds phones that are missing, and marks them in the site. The values were read from web pages with an automated extraction and range-checked, so a few may still be wrong; fix them in the CSV and re-export. Other empty cells stay "Not listed" rather than being guessed.

**Before you publish, check the data licence.** GitHub Pages makes every file in `site/data/` public, which redistributes the dataset. GSMArena's terms do not allow copying its data, and the Kaggle datasets have their own licences. For a private class demo, keep the repository and site private or share only a sample.

## Using it
See [docs/CONSOLE.md](docs/CONSOLE.md). Main menu: search, filter, brands, details, compare, assistant, top lists, my shortlist, market insights, about.

## AI assistant (optional)
Works without a key using built-in rules. For a language model set `AI_PROVIDER`, `AI_API_KEY` (and `AI_MODEL`)
in `.env`. The model only turns your text into structured requirements and wording; the database decides which
devices qualify. Details: [docs/RECOMMENDATION_ENGINE.md](docs/RECOMMENDATION_ENGINE.md).

## Java OOP and DBMS concepts in this code
See [docs/OOP_AND_DBMS.md](docs/OOP_AND_DBMS.md) (useful for viva/exam).

## Project layout
```
src/com/teqzo/Main.java        entry point
  ui/            Console, ConsoleApp (menus), Views (text formatting)
  config/        AppConfig (.env + environment), AppContext (object wiring), SchemaVerifier
  repository/    Database (connection), Jdbc, NamedSql, DeviceRepository/BrandRepository (interfaces) + Jdbc* classes
  service/       device, brand, comparison, facets services
  filter/        filter parsing and the SQL builder (all values bound as parameters)
  recommendation/ requirement checking and scoring
  ai/            assistant, rule-based and LLM requirement parsers, provider classes (HTTP via java.net.http)
  domain/, dto/  rows, records and exceptions
test/            168 tests with a tiny built-in test runner (no JUnit needed)
database/        Python scripts: importers (Flipkart, Mobiles 2025, import_all), extras builder, pipeline, tests
```

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| "No suitable driver" / cannot connect | Connector/J jar missing from `lib/`, MySQL not running, or wrong `DB_*` values. |
| WARNING: device_extras_v1 has no column ... | Re-run `python database/build_device_extras.py`. |
| Symbols show as `?` | Set `TEQZO_ASCII=1`; on Windows use `run.bat` (switches to UTF-8). |
| "iPhone 15 not found" / no launch prices | Run `import_all.py` (see Quick start). |
| Assistant says "not configured" | No `AI_API_KEY`; rule-based mode still works. |

## Credits and licence notes
Specifications, prices and image links originate from GSMArena and the Flipkart listing dataset and belong to their
owners. Check the sources' terms before public or commercial use.
