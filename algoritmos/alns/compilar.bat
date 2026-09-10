@echo off
REM Compila el componente planificador de PaqRap en la carpeta out\
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 goto :error
echo Compilado en out\  -^>  java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador ^<ventas.txt^> ...
goto :eof
:error
echo Error de compilacion.
