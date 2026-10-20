# Java OOP and DBMS concepts used

## OOP
| Concept | Where |
|---------|-------|
| Encapsulation | `Database` hides the connection and reconnects; `AppConfig` hides how settings are read. Fields are private/final. |
| Abstraction / interfaces | `DeviceRepository`, `BrandRepository`, `RowMapper<T>`, `AiProvider`, `RequirementParser`, `AiRecommendationService`: callers depend on the interface, not the class. |
| Polymorphism | `AiProvider` has `OpenAiCompatibleProvider` and `AnthropicProvider`; `RequirementParser` has a rule-based and an LLM version; chosen at run time by `AiProviderFactory`. |
| Inheritance / exceptions | `InvalidRequestException`, `NotFoundException`, `DataAccessException` extend `RuntimeException`; the console catches them by type. |
| Composition and dependency injection | `AppContext` creates every object once and passes dependencies through constructors (no framework). |
| Immutability and records | `DeviceSummary`, `PageResponse`, `ChatRequest`... are records; `ChatRequest` validates in its compact constructor. |
| Pure, testable logic | `SimilarityScorer`, `ComparisonVerdict`, `Leaderboard`, `CsvExporter` are small classes without database or console code, so they are unit-tested directly. |
| File I/O | `ShortlistStore` (java.nio.file, atomic replace), `CsvExporter`, `.env` loading in `AppConfig`. |
| Default interface methods | `DeviceRepository.findRegionalPrices` / `insights` have defaults, so older implementations keep working. |
| Layered design | ui -> service -> repository -> JDBC; SQL exists only in `repository/` and `filter/DeviceQueryBuilder`. |
| Generics, enums, collections | `RowMapper<T>`, `PageResponse<T>`, `SortOption`, `UseCase`, `Dimension`; `List`, `Map`, streams. |

## DBMS
| Concept | Where |
|---------|-------|
| Relational schema, keys | `database/schema.sql`; `brand`, `device`, `device_specification`, `device_core_v4`, `device_extras_v1` linked by ids. |
| Joins | device core JOIN extras JOIN brand in `JdbcDeviceRepository`. |
| Normalisation vs derived tables | raw specification rows are normalised text; `device_core_v4` / `device_extras_v1` are derived, query-friendly tables built by scripts. |
| JDBC | `DriverManager.getConnection`, `PreparedStatement`, `ResultSet`, try-with-resources (`Jdbc`, `Database`). |
| SQL injection safety | `NamedSql` turns `:name` into `?`; every user value is bound, never concatenated. Tested in `NamedSqlTest`. |
| GROUP BY / HAVING | `JdbcDeviceRepository.insights()`: per-brand counts and averages, phones per year, price bands with CASE. |
| Aggregation | facets use `COUNT` and `SUM`; the query builder uses `COALESCE` and a `CASE` expression for ranking. |
| Indexing and paging | `LIMIT/OFFSET` paging; indexes are defined in `database/schema.sql`. |
| Metadata | `SchemaVerifier` reads `information_schema`. |
| Data cleaning (ETL) | `import_flipkart.py`, `import_mobiles2025.py`: dedupe, merge variants, match against existing rows, never invent values. |
| Composite primary key | `device_price_v1 (device_id, country, variant)`. |
