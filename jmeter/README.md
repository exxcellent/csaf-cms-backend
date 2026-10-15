# JMeter and bulk advisory data

## Persistent Red Hat CSAF test data

`performance-test.jmx` creates and subsequently deletes test advisories. Do not
use it to seed a database. Use `import-redhat-advisories.js` instead; it imports
6 | real Red Hat CSAF documents through the backend API and never deletes them.

The script obtains the official daily archive from
`https://security.access.redhat.com/data/csaf/v2/advisories/`, verifies its
published SHA-256 checksum, and imports documents sequentially. It reuses a
previously downloaded archive for the requested date and downloads it only when
the local archive is absent. The catalog
currently contains roughly 30,000 documents, so use a disposable performance
database and begin with a small batch.

### Prerequisites

1. Start the local stack from `C:\Projects\exxcellent\bsi\forks\csaf-cms-backend\docker`:

   ```powershell
   docker compose up -d
   ```

2. Verify the backend responds:

   ```powershell
   Invoke-WebRequest http://localhost:8081/api/v1/about
   ```

3. Install Node.js 24 or later.

4. Install an extractor for the Red Hat `.tar.zst` archive:
   - `tar` supporting `--zstd`, or
   - a `zstd` executable on `PATH` alongside `tar`.

### Trial import

From `C:\Projects\exxcellent\bsi\forks\csaf-cms-backend`:

```powershell
node .\jmeter\import-redhat-advisories.js --limit 10
```

The default archive date is yesterday. If Red Hat has not yet published that
snapshot, provide the date shown in the provider directory listing:

```powershell
node .\jmeter\import-redhat-advisories.js --archive-date 2026-10-11 --limit 10
```

Results are appended to
`jmeter\redhat-advisories\import-results-<date>.csv`. Before every create
request, the importer persists a `Started` row; it then appends `Success` or
`Failed` after that request completes. On a later invocation, `--resume` skips
only documents whose latest state is `Success`:

```powershell
node .\jmeter\import-redhat-advisories.js --archive-date 2026-10-11 --resume
```

### Full import

After validating a trial batch and sizing the test database, run:

```powershell
node .\jmeter\import-redhat-advisories.js --archive-date 2026-10-11 --resume
```

The archive and extracted files are retained under
`jmeter\redhat-advisories`. To avoid another download and extraction, use that
directory on a resumed run:

```powershell
node .\jmeter\import-redhat-advisories.js --archive-date 2026-10-11 --skip-download --resume
```

Use `--api-base-url` and `--keycloak-base-url` if the backend or Keycloak is not on
local default ports. The script authenticates with a Keycloak bearer token; it
does not require an `XSRF-TOKEN` cookie. The script reports failures in its CSV
and returns exit code `1` if one or more documents fail; correct the issue and
use `--resume`.

For long-running imports, the importer renews the Keycloak access token before
its reported expiry. If an advisory API request still receives HTTP 401, it
obtains a fresh token and retries that exact request once. A second HTTP 401 is
recorded as `Failed`, avoiding an endless retry loop for invalid credentials or
missing permissions.

If a process stops after the API accepted a create request but before the local
`Success` row is written, `--resume` finds the retained `Started` row. It checks
the API for the document's CSAF tracking ID before issuing another create
request. A matching advisory is recorded as `Success`; otherwise the importer
records a fresh `Started` row and retries the create. This recovery requires the
same archive-derived state file (or the same explicit `--state-file`) and read
access to the advisory list API.

### API request format

The importer creates each advisory through `POST /api/v1/advisories`, not the
dedicated import endpoint. It sends the regular create-request body:

```json
{
  "summary": "Imported Red Hat advisory RHSA-2026:0001.",
  "csaf": { "...": "original Red Hat CSAF document" }
}
```

`summary` is required by the regular create flow and becomes the summary of the
initial local revision-history entry. Its advisory identifier is taken from
`document.tracking.id`; if that is absent, the extracted document path is used.
The original Red Hat CSAF document is sent as the `csaf` value. The backend then
applies its normal create processing, including generating its initial local
revision-history entry, so the stored advisory can differ from the source
document. Documents that do not pass validation for the normal create flow
still fail and are recorded in the CSV for a later `--resume` run.