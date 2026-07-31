FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN apk add --no-cache build-base python3 vips-dev \
    && npm ci --legacy-peer-deps
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

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80 443
