#!/usr/bin/env bash
# Exit on error
set -o errexit

# Install dependencies
lein deps

# Compile into a single JAR
lein uberjar
