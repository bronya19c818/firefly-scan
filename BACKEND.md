# JuZiScan Backend

The FastAPI backend is located directly in the `backend/` directory of this repository.

## Client Base URL Configuration

Configure `JUZISCAN_API_BASE_URL` in your local `gradle.properties` or environment when testing payment and membership flows.

Recommended values:

- Local Android emulator: `JUZISCAN_API_BASE_URL=http://10.0.2.2:18001`
- Production server: `JUZISCAN_API_BASE_URL=https://api.example.com`

> **Note**: `JUZISCAN_API_BASE_URL` must be host root only (do not include `/api/v1` path), because the client app appends `/api/v1/...` internally.

For detailed backend setup instructions, database options (SQLite / PostgreSQL), and API endpoints, refer to [backend/README.md](backend/README.md).
