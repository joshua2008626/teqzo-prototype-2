"""Imports the GSMArena SQLite database into MySQL (original tables only).

Settings come from environment variables (see .env.example): DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD,
DB_NAME and optionally SQLITE_FILE. No credentials are stored in this file.
"""
import os
import sqlite3
import sys

import mysql.connector

SQLITE_FILE = os.environ.get("SQLITE_FILE", "gsmarena.db")

MYSQL_HOST = os.environ.get("DB_HOST", "localhost")
MYSQL_PORT = int(os.environ.get("DB_PORT", "3306"))
MYSQL_USER = os.environ.get("DB_USERNAME", "root")
MYSQL_PASSWORD = os.environ.get("DB_PASSWORD")
MYSQL_DATABASE = os.environ.get("DB_NAME", "tech_finder")

if not MYSQL_PASSWORD:
    sys.exit("DB_PASSWORD is not set. Export the variables from .env.example first.")

BATCH_SIZE = 2000

def main():
    print("Starting SQLite -> MySQL migration...")

    sqlite_conn = sqlite3.connect(SQLITE_FILE)

    mysql_server = mysql.connector.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD
    )

    cur = mysql_server.cursor()
    cur.execute(
        f"CREATE DATABASE IF NOT EXISTS `{MYSQL_DATABASE}` "
        "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    cur.close()
    mysql_server.close()

    mysql_conn = mysql.connector.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE
    )

    cur = mysql_conn.cursor()

    cur.execute("""
        CREATE TABLE IF NOT EXISTS brand (
            id VARCHAR(255) NOT NULL,
            name VARCHAR(255),
            url TEXT,
            num_devices INT,
            PRIMARY KEY (id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS device (
            id VARCHAR(255) NOT NULL,
            brand_id VARCHAR(255),
            name VARCHAR(500),
            url TEXT,
            thumbnail TEXT,
            summary TEXT,
            PRIMARY KEY (id),
            INDEX idx_device_brand (brand_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS device_specification (
            device_id VARCHAR(255),
            spec_id BIGINT NOT NULL,
            spec_category VARCHAR(255),
            specification VARCHAR(500),
            spec_value TEXT,
            PRIMARY KEY (spec_id),
            INDEX idx_spec_device (device_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS old_device_specification (
            device_id VARCHAR(255),
            spec_id BIGINT NOT NULL,
            spec_category VARCHAR(255),
            specification VARCHAR(500),
            spec_value TEXT,
            PRIMARY KEY (spec_id),
            INDEX idx_old_spec_device (device_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)

    mysql_conn.commit()
    cur.close()

    tables = {
        "brand": ["id", "name", "url", "num_devices"],
        "device": ["id", "brand_id", "name", "url", "thumbnail", "summary"],
        "device_specification": [
            "device_id", "spec_id", "spec_category",
            "specification", "spec_value"
        ],
        "old_device_specification": [
            "device_id", "spec_id", "spec_category",
            "specification", "spec_value"
        ]
    }

    for table, columns in tables.items():
        print(f"\nCopying {table}...")

        mcur = mysql_conn.cursor()
        mcur.execute(f"DELETE FROM `{table}`")

        scolumns = ", ".join(f'"{c}"' for c in columns)
        mcur_columns = ", ".join(f"`{c}`" for c in columns)
        placeholders = ", ".join(["%s"] * len(columns))

        scur = sqlite_conn.cursor()
        scur.execute(f"SELECT {scolumns} FROM `{table}`")

        insert_sql = (
            f"INSERT INTO `{table}` ({mcur_columns}) "
            f"VALUES ({placeholders})"
        )

        total = 0

        while True:
            rows = scur.fetchmany(BATCH_SIZE)
            if not rows:
                break

            mcur.executemany(insert_sql, rows)
            mysql_conn.commit()
            total += len(rows)
            print(f"  {total:,} rows copied")

        scur.close()
        mcur.close()

    print("\nAdding relationships...")

    cur = mysql_conn.cursor()

    try:
        cur.execute("""
            ALTER TABLE device
            ADD CONSTRAINT fk_device_brand
            FOREIGN KEY (brand_id) REFERENCES brand(id)
            ON UPDATE CASCADE ON DELETE SET NULL
        """)
    except mysql.connector.Error as e:
        if "Duplicate foreign key" not in str(e):
            print("Brand relationship note:", e)

    try:
        cur.execute("""
            ALTER TABLE device_specification
            ADD CONSTRAINT fk_spec_device
            FOREIGN KEY (device_id) REFERENCES device(id)
            ON UPDATE CASCADE ON DELETE CASCADE
        """)
    except mysql.connector.Error as e:
        if "Duplicate foreign key" not in str(e):
            print("Specification relationship note:", e)

    try:
        cur.execute("""
            ALTER TABLE old_device_specification
            ADD CONSTRAINT fk_old_spec_device
            FOREIGN KEY (device_id) REFERENCES device(id)
            ON UPDATE CASCADE ON DELETE CASCADE
        """)
    except mysql.connector.Error as e:
        if "Duplicate foreign key" not in str(e):
            print("Old specification relationship note:", e)

    mysql_conn.commit()

    print("\nVerifying MySQL data...")
    for table in tables:
        cur.execute(f"SELECT COUNT(*) FROM `{table}`")
        count = cur.fetchone()[0]
        print(f"  {table}: {count:,} rows")

    cur.close()
    mysql_conn.close()
    sqlite_conn.close()

    print("\nMigration completed successfully!")


if __name__ == "__main__":
    main()
