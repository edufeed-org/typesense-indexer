# Stage 1: Build the Uberjar
FROM clojure:tools-deps-bullseye AS builder

# Set environment variables
ENV APP_HOME=/usr/src/app
WORKDIR $APP_HOME

# Copy project files
COPY deps.edn .
COPY build.clj .
COPY src ./src
COPY resources ./resources

# Install Git (required for `git-count-revs` in your build script)
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# Run the build script to create the uberjar
RUN clojure -T:build uber

# Stage 2: Run the Uberjar in a minimal image
FROM openjdk:23-jdk-slim

# Set environment variables
ENV APP_HOME=/usr/src/app
WORKDIR $APP_HOME

# Copy the uberjar from the builder stage
COPY --from=builder /usr/src/app/target/app.jar app.jar

# Define the entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]

