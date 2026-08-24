.DEFAULT_GOAL := help

COMPOSE := docker compose

.PHONY: help up down build test

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build (if needed) and start the client on its own (no backend/proxy — see the project root Makefile for the full stack)
	$(COMPOSE) up --build

down: ## Stop and remove the client's container
	$(COMPOSE) down

build: ## Build the production static assets, verifying the build stage succeeds
	docker build --target build -t fortune-avenue-client-build .

test: ## Run the test suite (none configured yet — placeholder for when one exists)
	@echo "No client test suite configured yet."
