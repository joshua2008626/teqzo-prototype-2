# Original data pipeline

These are the scripts that produced the dataset the application reads. They are kept so the database can be
rebuilt on another machine. Run them in this order (all from the repository root, with the variables from
`.env.example` exported):

1. `gsmarena_to_mysql.py` – copies the GSMArena SQLite file (`SQLITE_FILE`, default `gsmarena.db`) into MySQL.
   Creates the original tables `brand`, `device`, `device_specification`, ... in database `DB_NAME`
   (utf8mb4 / utf8mb4_unicode_ci). The SQLite file itself is **not** included in this repository.
2. `build_device_core_v4.py` – builds the normalised table `device_core_v4` (interactive prompts for host,
   port, user and password; nothing is stored).
3. `../build_device_extras.py` – builds `device_extras_v1` (see `docs/DATABASE.md`). Required by the API.

`build_device_core_v4.py` is unchanged from the original project. Known limitation, also documented in
`docs/DATABASE.md`: it never extracts a wireless-charging wattage (`18W wireless` has the number *before* the
word), which is why `device_extras_v1` re-derives it from the untouched source rows.
