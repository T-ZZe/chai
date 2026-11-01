#!/usr/bin/env bash
# Compile le plugin. Prerequis : JDK 25 + Maven 3.9+, acces internet vers repo.papermc.io.
set -euo pipefail
python3 tools/validate_configs.py
mvn -B -ntp clean package
echo "JAR : $(ls target/ChainsawRPG-*.jar)"
