# Import, export and backups

- **Import (UI):** Workflows → *Import JSON* → pick a file or paste JSON. Tick *Overwrite workflows with the same id* to replace.
  Without it, an import that contains an existing id fails as a whole and changes nothing.
- **Import (API):** `curl -X POST 'localhost:8080/api/workflows/import?overwrite=true' -H 'Content-Type: application/json' --data-binary @examples/realistic.json`
- **Export:** the *JSON* button per workflow, or *Export all*. Exports contain no secrets — credentials are referenced by name.
- **Moving to another server:** export workflows, recreate credentials (same names) and variables, import.
- **Backups:** back up the database (H2: the `data/` folder while the app is stopped; PostgreSQL: `pg_dump`) **and** the encryption key.
