SHELL := /bin/bash

CLUSTER_NAME ?= personal
NAMESPACE ?= personal
REGISTRY_NAME ?= $(CLUSTER_NAME)-registry
REGISTRY_PORT ?= 5001

cluster-up:
	@if k3d registry list $(REGISTRY_NAME) >/dev/null 2>&1; then \
	echo "Using existing registry $(REGISTRY_NAME)"; \
	REGISTRY_CMD="--registry-use k3d-$(REGISTRY_NAME):$(REGISTRY_PORT)"; \
	else \
	echo "Creating registry $(REGISTRY_NAME)"; \
	REGISTRY_CMD="--registry-create $(REGISTRY_NAME):0.0.0.0:$(REGISTRY_PORT)"; \
	fi; \
	k3d cluster create $(CLUSTER_NAME) --servers 1 --agents 1 \
	--port "8080:80@loadbalancer" $$REGISTRY_CMD

cluster-down:
	k3d cluster delete $(CLUSTER_NAME) || true
	k3d registry list $(REGISTRY_NAME) >/dev/null 2>&1 && k3d registry delete $(REGISTRY_NAME) || true

clean: cluster-down
	docker volume ls -q | grep '^k3d-$(CLUSTER_NAME)' | xargs -I{} docker volume rm {} || true
	docker network ls -q | grep '^k3d-$(CLUSTER_NAME)' | xargs -I{} docker network rm {} || true

deps:
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo update
	buf --version >/dev/null 2>&1 || go install github.com/bufbuild/buf/cmd/buf@latest

install-core:
	kubectl create namespace $(NAMESPACE) >/dev/null 2>&1 || true

build-app:
	cd ops/proto && buf generate
	cd apps/ingest-service && (test -f gradle/wrapper/gradle-wrapper.jar || gradle wrapper --gradle-version 8.4) && ./gradlew bootJar && docker build -t ingest-service:latest .
	cd apps/teller-poller && (test -f gradle/wrapper/gradle-wrapper.jar || gradle wrapper --gradle-version 8.4) && ./gradlew bootJar && docker build -t teller-poller:latest .

deploy:
	helm upgrade --install platform charts/platform -n $(NAMESPACE) -f charts/platform/values.yaml --create-namespace

tilt:
	tilt up

.PHONY: cluster-up cluster-down clean deps install-core build-app deploy tilt
