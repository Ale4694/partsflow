# syntax=docker/dockerfile:1

# ---- Stage 1: build the static files ---------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

# Install dependencies first, so they are cached until package-lock.json changes
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Stage 2: serve them with nginx ----------------------------------------
# The unprivileged image does not run as root (it listens on 8080 instead of 80).
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/partsflow-web/browser /usr/share/nginx/html

EXPOSE 8080
