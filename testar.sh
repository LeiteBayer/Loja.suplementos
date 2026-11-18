#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh compilar.sh
mkdir -p build/test-classes
javac -encoding UTF-8 --release 17 -cp "build/classes:lib/*" -d build/test-classes src/test/java/br/com/sistema/vendas/*.java
java -cp "build/classes:build/test-classes:lib/*" br.com.sistema.vendas.Testes
java -cp "build/classes:build/test-classes:lib/*" br.com.sistema.vendas.TesteBanco
