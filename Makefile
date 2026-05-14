GOBIN := $(shell go env GOPATH)/bin

.PHONY: generate
generate:
	cd proto && PATH="$(GOBIN):$$PATH" buf generate

.PHONY: lint-proto
lint-proto:
	cd proto && buf lint
