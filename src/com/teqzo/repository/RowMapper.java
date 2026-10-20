package com.teqzo.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Turns the current row of a ResultSet into an object. */
@FunctionalInterface
public interface RowMapper<T> {
	T map(ResultSet rs) throws SQLException;
}
