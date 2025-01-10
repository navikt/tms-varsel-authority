FROM ghcr.io/navikt/baseimages/temurin:21

ENV JAVA_OPTS='-XX:MaxRAMPercentage=75'

COPY app/build/libs/*.jar ./
