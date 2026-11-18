#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
find src/main/java -name '*.java' > build/fontes.txt
javac -encoding UTF-8 --release 17 -cp 'lib/*' -d build/classes @build/fontes.txt
cp -R src/main/resources/. build/classes/
jar --create --file loja-suplementos.jar --manifest MANIFEST.MF -C build/classes .
printf '%s\n' 'Compilação concluída: loja-suplementos.jar'
