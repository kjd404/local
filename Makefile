SHELL := /bin/bash

CLUSTER_NAME ?= personal
NAMESPACE ?= personal
REGISTRY_NAME ?= $(CLUSTER_NAME)-registry
REGISTRY_PORT ?= 5000

cluster-up:
	k3d cluster create $(CLUSTER_NAME) --servers 1 --agents 1 \
	--port "8080:80@loadbalancer" \
	--registry-create $(REGISTRY_NAME):0.0.0.0:$(REGISTRY_PORT)

cluster-down:
	k3d cluster delete $(CLUSTER_NAME) || true
	k3d registry delete $(REGISTRY_NAME) || true

deps:
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo update
	buf --version >/dev/null 2>&1 || go install github.com/bufbuild/buf/cmd/buf@latest

install-core:
	helm dependency update charts/platform
	helm upgrade --install platform charts/platform -n $(NAMESPACE) -f charts/platform/values.yaml -f charts/platform/values.local.sops.yaml --create-namespace

build-app:
	cd ops/proto && buf generate
	cd apps/ingest-service && (test -f gradle/wrapper/gradle-wrapper.jar || gradle wrapper --gradle-version 8.4) && ./gradlew bootJar && docker build -t ingest-service:latest .

deploy:
	helm upgrade --install platform charts/platform -n $(NAMESPACE) -f charts/platform/values.yaml -f charts/platform/values.local.sops.yaml

tilt:
	tilt up

.PHONY: cluster-up cluster-down deps install-core build-app deploy tilt
