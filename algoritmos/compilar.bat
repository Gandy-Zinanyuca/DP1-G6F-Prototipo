@echo off
setlocal EnableDelayedExpansion
pushd "%~dp0"
if not exist out mkdir out
set "sources=out\sources-%RANDOM%-%RANDOM%.txt"
type nul > "%sources%" || goto :error
(
  for /r comun\src %%f in (*.java) do (
    set "source=%%f"
    echo "!source:\=/!"
  )
  for /r alns\src %%f in (*.java) do (
    set "source=%%f"
    echo "!source:\=/!"
  )
  for /r tabu\src %%f in (*.java) do (
    set "source=%%f"
    echo "!source:\=/!"
  )
  for /r experimentacion\src %%f in (*.java) do (
    set "source=%%f"
    echo "!source:\=/!"
  )
) >> "%sources%"
if errorlevel 1 goto :error
javac --release 17 -encoding UTF-8 -d out @"%sources%"
if errorlevel 1 goto :error
del "%sources%"
java -cp out pe.pucp.paqrap.IntegracionALNSTest
if errorlevel 1 goto :error
java -cp out pe.pucp.paqrap.RestriccionesEstrictasTest
if errorlevel 1 goto :error
echo Compilacion y pruebas correctas. Clases en algoritmos\out.
popd
exit /b 0
:error
popd
exit /b 1
