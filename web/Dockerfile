# web/Dockerfile
# Multi-stage: 1) Vite build (statik dosyalar)  2) nginx ile servis
# NOT: VITE_* değişkenleri BUILD sırasında gömülür → prod URL'leri build-arg ile gelir.

# ---- 1. AŞAMA: BUILD ----
FROM node:22-alpine AS build
WORKDIR /app
# Bağımlılıklar (cache için önce package*.json)
COPY package*.json ./
RUN npm ci
# Kaynak
COPY . .
# Prod env değişkenleri build-arg olarak gelir, Vite bunları gömer
ARG VITE_KEYCLOAK_URL
ARG VITE_KEYCLOAK_REALM
ARG VITE_KEYCLOAK_CLIENT_ID
ARG VITE_API_BASE_URL
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- 2. AŞAMA: SERVE ----
FROM nginx:alpine
# Vite çıktısı dist/ → nginx kök dizini
COPY --from=build /app/dist /usr/share/nginx/html
# SPA routing: bilinmeyen yollar index.html'e (React Router için)
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
