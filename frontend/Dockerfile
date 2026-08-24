# Pinned by digest — see docker/Dockerfile for the rationale.
FROM node:20-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --legacy-peer-deps
COPY . .
ARG VITE_API_URL=
ENV VITE_API_URL=$VITE_API_URL
ARG VITE_SENTRY_DSN=
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN
ARG VITE_SENTRY_ENVIRONMENT=local
ENV VITE_SENTRY_ENVIRONMENT=$VITE_SENTRY_ENVIRONMENT
ARG VITE_APP_RELEASE=
ENV VITE_APP_RELEASE=$VITE_APP_RELEASE
ARG VITE_ERROR_REPORT_URL=
ENV VITE_ERROR_REPORT_URL=$VITE_ERROR_REPORT_URL
RUN npm run build

FROM nginx:alpine@sha256:db35bfc6b2951e7f8a72db5db120288c127ffaeeb4a6d4b95a26fead017d5913
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
EXPOSE 80 443
