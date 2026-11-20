FROM maven:3.10.0-eclipse-temurin-25
RUN apt-get update && apt-get install -y --no-install-recommends g++ binutils && rm -rf /var/lib/apt/lists/*
WORKDIR /work
