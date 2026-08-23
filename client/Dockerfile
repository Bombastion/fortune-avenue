# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /workspace

# Copy just the package manifest first so dependency install is cached
# in its own layer and only re-runs when it changes. There's no
# package-lock.json committed yet (see README), so this falls back to
# `npm install`; once a lockfile exists, switch this to `npm ci`.
COPY package.json package-lock.json* ./
RUN npm install

# Now copy sources and build the static assets.
COPY . .
RUN npm run build

# --- Runtime stage ---
FROM nginx:1.27-alpine AS runtime
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
