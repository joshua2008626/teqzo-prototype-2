# What was verified, and how

The core-Java version was built in a sandbox with JDK 21 and Python, but **no MySQL server and no MySQL Connector/J
jar** (Maven Central is blocked there). Be clear about what that means:

| Area | How it was checked | Status |
|------|--------------------|--------|
| Data scripts (`database/`) | 60 Python tests (incl. 24 for the Mobiles 2025 import); real 930-row Mobiles 2025 CSV cleaned to 587 phone models; real 33,600-row Flipkart CSV cleaned to ~194 models; importer + the real `build_device_extras.py` + the real MySQL `build_device_core_v4.py` (through a MySQL-to-SQLite shim) | Verified |
| Java logic (search, filters, SQL builder, comparison, scoring, assistant, parsers, AI providers over local HTTP mock, similarity, shortlist, CSV, top lists, verdict) | 168 tests, `./test.sh`, all pass | Verified |
| JDBC code (`Database`, `Jdbc`, `NamedSql`, repositories) and the whole console | Ran the real `Main` and menus (search, filter, brand, details, compare, assistant, about, `--check`) against the merged data through a **throw-away JDBC bridge to SQLite** (not shipped) | Verified on SQLite |
| Real MySQL and real Connector/J | Not available here | **Not verified** |
| Real AI provider calls | Only against a local mock; no API key | **Not verified** |

Things most likely to need a small fix on first run with MySQL: the Connector/J URL options, and SQL that SQLite
accepts but MySQL treats differently. Start with `./run.sh --check`, then open each menu once.
Windows `.bat` scripts were written but not executed here.

## Mobiles Dataset 2025 and new features

* The two uploaded files were byte-identical, so they were imported once.
* The real GSMArena database was not available in this sandbox (only a 4-device stand-in), so how many Mobiles 2025
  models match your real GSMArena rows is **not known yet**; the import report prints the numbers when you run it.
  On the stand-in plus the Flipkart data: 587 models, 22 already present, 565 added, 4,074 launch-price rows.
  An audit of the merged data found no duplicate ids and no repeated phone identity except 4G/5G sibling pairs
  that the dataset itself lists separately.
* The new menus (top lists, shortlist, similar phones, insights, export, verdict, launch prices) were run through the
  console against the merged SQLite data via the throw-away JDBC bridge. The GROUP BY queries have **not** run on MySQL.
