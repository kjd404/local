SHELL := /bin/bash

CLUSTER_NAME ?= personal
NAMESPACE ?= personal

cluster-up:
	k3d cluster create $(CLUSTER_NAME) --servers 1 --agents 1 --port "8080:80@loadbalancer"

cluster-down:
	k3d cluster delete $(CLUSTER_NAME) || true

deps:
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo update
	buf --version >/dev/null 2>&1 || go install github.com/bufbuild/buf/cmd/buf@latest

install-core:
	helm dependency update charts/platform
	helm upgrade --install platform charts/platform -n $(NAMESPACE) -f charts/platform/values.yaml -f charts/platform/values.local.sops.yaml --create-namespace

build-app:
	cd ops/proto && buf generate
	cd apps/ingest-service && ./gradlew bootJar && docker build -t ingest-service:latest .

deploy:
	helm upgrade --install platform charts/platform -n $(NAMESPACE) -f charts/platform/values.yaml -f charts/platform/values.local.sops.yaml

tilt:
	tilt up

.PHONY: cluster-up cluster-down deps install-core build-app deploy tilt
