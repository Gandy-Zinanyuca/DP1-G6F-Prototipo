@echo off
setlocal
if not exist "%~dp0out\pe\pucp\paqrap\EjecutarALNS.class" call "%~dp0compilar.bat"
if errorlevel 1 goto :error
java -cp "%~dp0out" pe.pucp.paqrap.EjecutarALNS %*
set "resultado=%errorlevel%"
exit /b %resultado%
:error
exit /b 1
