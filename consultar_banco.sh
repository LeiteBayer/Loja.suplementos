#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
java -cp 'loja-suplementos.jar:lib/*' br.com.sistema.vendas.storage.ConsultarBanco
