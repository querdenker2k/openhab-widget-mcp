# OpenHAB Widget MCP

A Quarkus-based MCP (Model Context Protocol) server and REST API for managing OpenHAB custom widgets, items, and pages. It provides browser automation via Playwright to capture screenshots of OpenHAB widgets and pages.

## Features

- **MCP Server via SSE**: Compatible with Claude Desktop and other MCP clients
- **Screenshot Capture**: Playwright-based browser automation to capture widget and page screenshots
- **Swagger UI**: Interactive API documentation available at `/q/swagger-ui`
- **Docker Support**: Ready-to-use container image

## Prerequisites

- Java 21 (for local builds)
- Maven 3.8+
- Docker (for container builds)
- OpenHAB instance (for runtime)

### Linux System Dependencies

If you are running the application on a **bare Linux system** (not via Docker), you might need to install the following dependencies for Playwright:

```bash
apt-get install libglib2.0-0t64 \
    libnss3 \
    libnspr4 \
    libdbus-1-3 \
    libatk1.0-0t64 \
    libatk-bridge2.0-0t64 \
    libatspi2.0-0t64 \
    libx11-6 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libdrm2 \
    libxcb1 \
    libxkbcommon0 \
    libpango-1.0-0 \
    libcairo2 \
    libasound2t64 \
    libcups2t64 \
    libatspi2.0-0t64
```

> **Note:** When using the **Docker image**, these dependencies and the necessary browser binaries (Chromium) are already pre-installed.

## Configuration

All configuration is done via `application.yaml` or environment variables. The following parameters are available:

### OpenHAB Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `openhab.url` | String | `http://localhost:8080` | URL of your OpenHAB instance |
| `openhab.api-token` | String | *(empty)* | API token for OpenHAB authentication |
| `openhab.username` | String | *(empty)* | Username for OpenHAB authentication |
| `openhab.password` | String | *(empty)* | Password for OpenHAB authentication |
| `openhab.output-dir` | String | `/tmp/openhab-screenshots` | Directory for saved screenshots |


### Environment Variable Mapping

Environment variables override configuration properties using the `*_` prefix convention:

```bash
export OPENHAB_URL=http://your-openhab:8080
export OPENHAB_API_TOKEN=your-api-token
export OPENHAB_USERNAME=your-username
export OPENHAB_PASSWORD=your-password
export OPENHAB_OUTPUT_DIR=/tmp/openhab-screenshots
```

## Local Development

### Build and Run

```bash
# Build the project
mvn clean package

# Run locally
mvn quarkus:dev
```

Or run the packaged JAR:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Docker Container

The image is automatically built and published to GitHub Container Registry (GHCR) on every push to the main branch.

### Pull from GHCR

```bash
docker pull ghcr.io/querdenker2k/openhab-widget-mcp:latest
```

oder für einen spezifischen Branch:

```bash
docker pull ghcr.io/querdenker2k/openhab-widget-mcp:main
```

### Run the Container

```bash
docker run -d \
  --name openhab-widget-mcp \
  -p 8081:8081 \
  -e OPENHAB_URL=http://your-openhab:8080 \
  -e OPENHAB_API_TOKEN=your-api-token \
  -e OPENHAB_USERNAME=your-username \
  -e OPENHAB_PASSWORD=your-password \
  -e OPENHAB_OUTPUT_DIR=/tmp/openhab-screenshots \
  -v /tmp/openhab-screenshots:/tmp/openhab-screenshots \
  ghcr.io/querdenker2k/openhab-widget-mcp:main
```

### Build the Container Locally

```bash
# Build using Quarkus Docker support
mvn clean package -Dquarkus.container-image.build=true

# Or using Docker directly
docker build -f src/main/docker/Dockerfile.jvm -t openhab-widget-mcp:latest .
```

### Run the Container

```bash
docker run -d \
  --name openhab-widget-mcp \
  -p 8081:8081 \
  -e OPENHAB_URL=http://your-openhab:8080 \
  -e OPENHAB_API_TOKEN=your-api-token \
      - OPENHAB_USERNAME=your-username \
      - OPENHAB_PASSWORD=your-password \
  -e OPENHAB_OUTPUT_DIR=/tmp/openhab-screenshots \
  -v /tmp/openhab-screenshots:/tmp/openhab-screenshots \
  openhab-widget-mcp:latest
```

### Docker Compose

```yaml
version: '3.8'

services:
  openhab-widget-mcp:
    image: ghcr.io/querdenker2k/openhab-widget-mcp:latest
    ports:
      - "8081:8081"
    environment:
      - OPENHAB_URL=http://your-openhab:8080
      - OPENHAB_API_TOKEN=your-api-token
      - OPENHAB_USERNAME=your-username
      - OPENHAB_PASSWORD=your-password
      - OPENHAB_OUTPUT_DIR=/tmp/openhab-screenshots
    volumes:
      - screenshots:/tmp/openhab-screenshots
    restart: unless-stopped

volumes:
  screenshots:
```

### Required Environment Variables Summary

| Variable | Required | Default | Description |
|----------|---------|---------|-------------|
| `OPENHAB_URL` | Yes* | `http://localhost:8080` | Your OpenHAB instance URL |
| `OPENHAB_API_TOKEN` | Yes | *(empty)* | API token for authentication |
| `OPENHAB_USERNAME` | Yes | *(empty)* | Username for authentication |
| `OPENHAB_PASSWORD` | Yes | *(empty)* | Password for authentication |
| `OPENHAB_OUTPUT_DIR` | No | `/tmp/openhab-screenshots` | Screenshot output directory |

> \* You must provide a valid `OPENHAB_URL` pointing to your OpenHAB instance.

## API Documentation

Once running, the Swagger UI is available at:

```
http://localhost:8081/q/swagger-ui
```

## MCP Integration

This service exposes an MCP server via SSE (Server-Sent Events). Configure your MCP client (e.g., Claude Desktop) to connect to the SSE endpoint.

## Building for Production

```bash
mvn clean package -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
```

This will build and push the container image to your configured registry.