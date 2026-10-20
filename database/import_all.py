#!/usr/bin/env python3
"""Import the Flipkart 2026 and Mobiles 2025 datasets in the right order, then rebuild the two derived tables once.

    python database/import_all.py --flipkart flipkart_mobiles_dataset_2026.csv --mobiles2025 Mobiles_Dataset_2025.csv

Either option can be left out. Order matters: Flipkart first, then Mobiles 2025, because the Mobiles 2025 importer
compares its models with everything already in the database and never adds a phone twice.
Same settings as the single importers (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD); --sqlite FILE for tests.
"""
import argparse
import os
import sqlite3
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import import_flipkart as fk  # noqa: E402
import import_mobiles2025 as m25  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--flipkart", metavar="CSV")
    ap.add_argument("--mobiles2025", metavar="CSV")
    ap.add_argument("--sqlite", metavar="FILE", help="verification only; skips the rebuild")
    args = ap.parse_args()
    if not (args.flipkart or args.mobiles2025):
        ap.error("give at least one of --flipkart / --mobiles2025")

    conn, ph = (sqlite3.connect(args.sqlite), "?") if args.sqlite else (fk.connect_mysql(), "%s")
    if args.flipkart:
        devices, report = fk.clean(fk.read_rows(args.flipkart))
        fk.print_report(report, fk.write_all(conn, ph, devices))
    # The Flipkart import removes the Mobiles 2025 rows (see its docstring), so this always runs afterwards.
    if args.mobiles2025:
        devices, report = m25.clean(m25.read_rows(args.mobiles2025))
        m25.print_report(report, m25.write_all(conn, ph, devices))
    elif args.flipkart:
        print("Note: the Flipkart import removed the Mobiles 2025 rows; run with --mobiles2025 to add them again.")
    conn.close()
    if args.sqlite:
        return
    for script in (os.path.join(HERE, "pipeline", "build_device_core_v4.py"), os.path.join(HERE, "build_device_extras.py")):
        print("Running", os.path.relpath(script, HERE))
        subprocess.run([sys.executable, script], check=True)


if __name__ == "__main__":
    main()
