#!/bin/bash
# RootScope all-in-one start: Spring Boot on file-backed H2 + nginx UI.
# No Kafka (disabled by default), no Postgres. H2 file lives in /data
# (persistent on HF Spaces) so incidents survive restarts there.
set -e
# Render injects $PORT; default keeps local/HF behavior on 7860.
PORT="${PORT:-7860}"
sed -i "s/listen 7860;/listen $PORT;/" /etc/nginx/conf.d/rootscope.conf

mkdir -p /data
export SERVER_PORT=8080
export SPRING_DATASOURCE_URL='jdbc:h2:file:/data/rootscope;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'

java -Xmx768m -jar /app/app.jar >/tmp/backend.log 2>&1 &
until (echo >/dev/tcp/127.0.0.1/8080) >/dev/null 2>&1; do sleep 2; done

exec nginx -g 'daemon off;'
