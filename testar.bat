@echo off
chcp 65001 >nul
cd /d "%~dp0"
call compilar.bat
if errorlevel 1 exit /b 1
if not exist build\test-classes mkdir build\test-classes
javac -encoding UTF-8 --release 17 -cp "build\classes;lib/*" -d build\test-classes src\test\java\br\com\sistema\vendas\*.java
if errorlevel 1 goto erro
java -cp "build\classes;build\test-classes;lib/*" br.com.sistema.vendas.Testes
if errorlevel 1 goto erro
java -cp "build\classes;build\test-classes;lib/*" br.com.sistema.vendas.TesteBanco
if errorlevel 1 goto erro
pause
exit /b 0
:erro
echo Os testes encontraram um problema. Leia a mensagem acima.
pause
exit /b 1
