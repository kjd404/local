SHELL := /bin/bash

# Load environment variables from a local .env file when present so that
# `make` targets automatically pick up DB credentials and other settings.
ifneq (,$(wildcard ./.env))
include .env
export $(shell sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' .env)
endif

build-app:
	cd ops/proto && buf generate
	cd apps/ingest-service && (test -f gradle/wrapper/gradle-wrapper.jar || gradle wrapper --gradle-version 8.4) && ./gradlew bootJar

# Build the ingest-service Docker image
# Example usage: `make docker-build`
docker-build: build-app
	docker build -t ingest-service:latest apps/ingest-service

# Run the ingest-service container locally pointing at a Postgres database
# Example usage: `make docker-run DB_URL=jdbc:postgresql://localhost:5432/ingest DB_USER=user DB_PASSWORD=pass`
docker-run:
	-docker rm -f ingest-service >/dev/null 2>&1 || true
	docker run --rm --name ingest-service -p 8080:8080 \\
		-e DB_URL=$(DB_URL) \\
		-e DB_USER=$(DB_USER) \\
		-e DB_PASSWORD=$(DB_PASSWORD) \\
		ingest-service:latest

# Apply database migrations using Flyway
db-migrate:
	./scripts/migrate.sh

# Tail logs from the running ingest-service container
docker-logs:
	./scripts/app-logs.sh

.PHONY: build-app docker-build docker-run db-migrate docker-logs
