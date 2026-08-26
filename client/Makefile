.DEFAULT_GOAL := help

COMPOSE := docker compose
COMPOSE_TEST := docker compose -f docker-compose.test.yml

.PHONY: help up down build test test-filter down-test

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build (if needed) and start the client on its own (no backend/proxy — see the project root Makefile for the full stack)
	$(COMPOSE) up --build

down: ## Stop and remove the client's container
	$(COMPOSE) down

build: ## Build the production static assets, verifying the build stage succeeds
	docker build --target build -t fortune-avenue-client-build .

test: ## Build (if needed), run the full test suite, and tear the test stack down afterward
	$(COMPOSE_TEST) run --build --rm test; \
	status=$$?; \
	$(COMPOSE_TEST) down; \
	exit $$status

test-filter: ## Run specific tests only and tear down afterward, e.g. make test-filter TESTS="src/validation/rules.test.ts"
	$(COMPOSE_TEST) run --build --rm test npx vitest run $(TESTS); \
	status=$$?; \
	$(COMPOSE_TEST) down; \
	exit $$status

down-test: ## Stop and remove the test stack's containers
	$(COMPOSE_TEST) down
