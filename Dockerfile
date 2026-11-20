FROM maven:3.9.16-eclipse-temurin-25
RUN apt-get update && apt-get install -y --no-install-recommends g++ binutils && rm -rf /var/lib/apt/lists/*
WORKDIR /work
