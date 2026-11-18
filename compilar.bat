@echo off
chcp 65001 >nul
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
(dir /s /b src\main\java\*.java) > build\fontes.txt
powershell -NoProfile -Command "$f = Get-Content 'build/fontes.txt'; $f = $f | ForEach-Object { ([char]34).ToString() + $_.Replace('\','/') + [char]34 }; [System.IO.File]::WriteAllLines((Join-Path (Get-Location) 'build/fontes.txt'), $f, (New-Object System.Text.UTF8Encoding($false)))"
javac -encoding UTF-8 --release 17 -cp "lib/*" -d build\classes @build\fontes.txt
if errorlevel 1 goto erro
xcopy src\main\resources\* build\classes\ /E /I /Y >nul
jar --create --file loja-suplementos.jar --manifest MANIFEST.MF -C build\classes .
if errorlevel 1 goto erro
echo Compilacao concluida. Agora abra iniciar.bat.
pause
exit /b 0
:erro
echo Falha ao compilar. Confira se um JDK 17 ou superior esta instalado.
pause
exit /b 1
