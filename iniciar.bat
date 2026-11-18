@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Abra http://localhost:8080 no navegador quando aparecer "Loja iniciada".
echo Mantenha esta janela aberta. Para parar, pressione Ctrl+C.
java -jar loja-suplementos.jar
if errorlevel 1 (
 echo Nao foi possivel iniciar. Confira Java 17 ou superior e se a porta 8080 esta livre.
 pause
)
