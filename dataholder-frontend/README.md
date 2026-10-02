# Test Data Holder Frontend

A React-based administration UI for the RDAP Test Data Holder service.

## Features

- **Dashboard**: Overview of pending requests, recent activity, and system status
- **Pending Requests**: Review and approve/deny data access requests requiring manual verification
- **Agreements**: Configure access levels for different agreements
- **Access Policy**: Configure default access policies, manual verification settings
- **Audit Logs**: View all RDAP query activity with detailed logging
- **RDAP Query**: Test RDAP queries directly against the data holder

## Quick Start

### Development

```bash
# Install dependencies
npm install

# Start development server
npm start
```

The app will be available at `http://localhost:3000`

### Docker

```bash
# Build and run with Docker
docker build -t dataholder-frontend .
docker run -p 3000:3000 -e REACT_APP_API_URL=http://localhost:8082 dataholder-frontend
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REACT_APP_API_URL` | Backend API URL | `http://localhost:8082` |
| `REACT_APP_VERSION` | App version displayed in header | `1.0.0` |

## Integration with Docker Compose

Add this service to your main `docker-compose.yml`:

```yaml
  # Data Holder Frontend
  dataholder-frontend:
    build:
      context: ./dataholder-frontend
      dockerfile: Dockerfile
    container_name: dataholder-frontend
    ports:
      - "3001:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:8082
      - REACT_APP_VERSION=1.0.0
      - CHOKIDAR_USEPOLLING=true
      - WATCHPACK_POLLING=true
    volumes:
      - ./dataholder-frontend:/app
      - /app/node_modules
    depends_on:
      - dataholder
    networks:
      - app-network
    stdin_open: true
    tty: true
```

## API Endpoints Used

The frontend communicates with the following backend endpoints:

### Health & Info
- `GET /` - Server info
- `GET /health` - Health check

### RDAP Queries
- `GET /domain/{domain}` - Query domain
- `GET /ip/{ip}` - Query IP
- `GET /autnum/{asn}` - Query ASN
- `GET /api/rdap/status/{requestId}` - Check pending request status
- `GET /api/rdap/domains` - List available test domains
- `GET /api/rdap/agreements` - List available agreements

### Admin Endpoints
- `GET /api/admin/pending-requests` - List pending requests
- `POST /api/admin/pending-requests/{id}/review` - Approve/deny request
- `GET /api/admin/pending-requests/stats` - Request statistics
- `GET /api/admin/agreement-levels` - List agreement access levels
- `POST /api/admin/agreement-levels` - Update agreement level
- `GET /api/admin/policy` - Get active policy
- `PUT /api/admin/policy` - Update policy
- `GET /api/admin/audit-logs` - Get audit logs

## Tech Stack

- React 18
- React Router 6
- Axios for API communication
- react-hot-toast for notifications
- date-fns for date formatting
- Custom CSS (no external CSS frameworks)

## Design

The UI features a dark industrial theme with:
- Dark color scheme with teal/cyan accents
- JetBrains Mono for code/monospace text
- Plus Jakarta Sans for display text
- Responsive design for desktop and mobile
- Smooth animations and transitions
