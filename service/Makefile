.DEFAULT_GOAL := help

COMPOSE := docker compose
COMPOSE_TEST := docker compose -f docker-compose.test.yml

.PHONY: help up down test test-filter down-test fmt lint

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build (if needed) and start the app + its Postgres instance
	$(COMPOSE) up --build

down: ## Stop and remove the app's containers
	$(COMPOSE) down

test: ## Build (if needed), run the full test suite, and tear the test stack down afterward
	$(COMPOSE_TEST) run --build --rm test; \
	status=$$?; \
	$(COMPOSE_TEST) down; \
	exit $$status

test-filter: ## Run specific tests and tear down afterward, e.g. make test-filter TESTS="com.fortuneavenue.server.rest.UserControllerTest"
	$(COMPOSE_TEST) run --build --rm test ./gradlew --no-daemon test --tests "$(TESTS)"; \
	status=$$?; \
	$(COMPOSE_TEST) down; \
	exit $$status

down-test: ## Stop and remove the test stack's containers
	$(COMPOSE_TEST) down

fmt: ## Reformat Kotlin sources with ktfmt, writing changes to disk
	docker build --target build -t fortune-avenue-build .
	docker run --rm -v $(CURDIR):/workspace fortune-avenue-build ./gradlew --no-daemon ktfmtFormat

lint: ## Check Kotlin formatting without modifying files (what `check` runs)
	docker build --target build -t fortune-avenue-build .
	docker run --rm -v $(CURDIR):/workspace fortune-avenue-build ./gradlew --no-daemon ktfmtCheck
