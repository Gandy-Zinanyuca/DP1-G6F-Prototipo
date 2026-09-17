@echo off
setlocal
if not exist "%~dp0out\pe\pucp\paqrap\EjecutarTabu.class" call "%~dp0compilar.bat"
if errorlevel 1 goto :error
java -cp "%~dp0out" pe.pucp.paqrap.EjecutarTabu %*
set "resultado=%errorlevel%"
exit /b %resultado%
:error
exit /b 1
