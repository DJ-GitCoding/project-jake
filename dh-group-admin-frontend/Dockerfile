# ---- Stage 1: Build (React Router v7 framework SSR) ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# ---- Stage 2: Runtime (Express SSR server, non-root) ----
FROM node:20-alpine
ENV NODE_ENV=production
WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY --from=build /app/build ./build
COPY server.js ./

EXPOSE 3000
USER node
CMD ["node", "server.js"]
