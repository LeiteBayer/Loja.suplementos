@echo off
chcp 65001 >nul
cd /d "%~dp0"
java -cp "loja-suplementos.jar;lib/*" br.com.sistema.vendas.storage.ConsultarBanco
pause
