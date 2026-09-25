@echo off
setlocal
if not exist "%~dp0out\pe\pucp\paqrap\EjecutarTabu.class" (
  echo Primero ejecute algoritmos\compilar.bat
  exit /b 1
)
java -Dfile.encoding=UTF-8 -cp "%~dp0out" pe.pucp.paqrap.EjecutarTabu %*
exit /b %errorlevel%
