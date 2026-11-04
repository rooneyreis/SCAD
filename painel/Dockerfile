# Build do painel React (Vite) e servidor nginx com proxy para os nós.
FROM node:20-alpine AS build
WORKDIR /painel
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy.conf /etc/nginx/scad/proxy.conf
COPY --from=build /painel/dist /usr/share/nginx/html
EXPOSE 8080 8081 8082 8083
