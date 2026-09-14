# Render build: repo root is the build context.
FROM docker.io/maven:3.9-eclipse-temurin-21 AS build
COPY backend /app/backend
WORKDIR /app
RUN mvn -f backend/pom.xml -q package -DskipTests

FROM docker.io/node:22-alpine AS uibuild
COPY frontend/package*.json /app/
WORKDIR /app
RUN npm ci 2>/dev/null || npm install
COPY frontend/ ./
RUN npm run build

FROM docker.io/eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends nginx && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/backend/target/rootscope-*.jar /app/app.jar
COPY --from=uibuild /app/dist /usr/share/nginx/html
COPY deploy/hf/nginx-space.conf /etc/nginx/conf.d/rootscope.conf
COPY deploy/hf/start.sh /start.sh
RUN chmod +x /start.sh && rm -f /etc/nginx/sites-enabled/default
CMD ["/start.sh"]
