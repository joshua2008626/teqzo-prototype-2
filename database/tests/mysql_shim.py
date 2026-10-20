"""Lets the real MySQL scripts (build_device_core_v4.py) run against SQLite for verification.

Install with ``install(sqlite_connection)`` BEFORE running the script: it registers a fake ``mysql.connector``
module whose ``connect()`` returns the given SQLite connection wrapped in a MySQL-looking cursor. MySQL-only DDL
(INDEX lines, foreign keys, ENGINE/CHARSET) is stripped; ``%(name)s`` parameters become ``:name``.
"""
import re
import sys
import types


class _Cursor:
    def __init__(self, conn, dictionary=False):
        self._cur = conn.cursor()
        self._dict = dictionary

    @staticmethod
    def _sql(sql):
        sql = re.sub(r"%\((\w+)\)s", r":\1", sql)
        if re.search(r"CREATE\s+TABLE", sql, re.I):
            lines = []
            for line in sql.splitlines():
                if re.match(r"\s*(INDEX|CONSTRAINT|FOREIGN KEY|REFERENCES)\b", line, re.I):
                    continue
                if re.match(r"\s*\)\s*ENGINE", line, re.I):
                    line = ")"
                lines.append(line)
            sql = "\n".join(lines)
            sql = re.sub(r",\s*\)", "\n)", sql)
        return sql

    def execute(self, sql, params=None):
        self._cur.execute(self._sql(sql), params or ())
        return self

    def executemany(self, sql, seq):
        self._cur.executemany(self._sql(sql), seq)

    def _row(self, row):
        if row is None or not self._dict:
            return row
        return {d[0]: v for d, v in zip(self._cur.description, row)}

    def fetchone(self):
        return self._row(self._cur.fetchone())

    def fetchall(self):
        return [self._row(r) for r in self._cur.fetchall()]

    def close(self):
        self._cur.close()


class _Conn:
    def __init__(self, conn):
        self._conn = conn

    def cursor(self, dictionary=False):
        return _Cursor(self._conn, dictionary)

    def commit(self):
        self._conn.commit()

    def close(self):
        pass  # the test keeps using the connection


def install(sqlite_conn):
    connector = types.ModuleType("mysql.connector")
    connector.connect = lambda **kwargs: _Conn(sqlite_conn)
    mysql = types.ModuleType("mysql")
    mysql.connector = connector
    sys.modules["mysql"] = mysql
    sys.modules["mysql.connector"] = connector
