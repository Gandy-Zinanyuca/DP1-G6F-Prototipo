@echo off
setlocal
if not exist "%~dp0alns\out\pe\pucp\paqrap\DemoPlanificador.class" (
  echo Primero ejecute algoritmos\compilar.bat
  exit /b 1
)
java -Dfile.encoding=UTF-8 -cp "%~dp0alns\out" pe.pucp.paqrap.DemoPlanificador %*
exit /b %errorlevel%
