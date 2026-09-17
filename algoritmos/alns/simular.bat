@echo off
REM Simula un mes completo con los datos de data\ y guarda la salida en resultados\sim_AAAAMM.txt
REM Uso:      simular.bat AAAAMM [opciones extra]
REM Ejemplos: simular.bat 202609
REM           simular.bat 202609 --iteraciones 1000 --k 12
REM Las opciones extra reemplazan a las de por defecto (--dia 1 --hora 0 --ciclos 4464 --sa 10 --k 7 --iteraciones 300).
setlocal
cd /d "%~dp0"

if "%~1"=="" (
    echo Uso: simular.bat AAAAMM [opciones extra]
    exit /b 1
)
set MES=%~1
set AAMM=%MES:~2,4%
set VENTAS=data\ventas.v20260909\ventas.%MES%.txt
set BLOQUEOS=data\bloqueos.v20260909\bloqueo.%AAMM%.txt
set MANT=data\mant.preventivo.09.10.txt

if not exist "%VENTAS%" (
    echo No existe %VENTAS%
    exit /b 1
)
if not exist out\pe\pucp\paqrap\DemoPlanificador.class (
    echo Falta compilar: ejecute compilar.bat
    exit /b 1
)
if not exist resultados mkdir resultados

echo Simulando %MES% ...  salida en resultados\sim_%MES%.txt
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador "%VENTAS%" "%BLOQUEOS%" "%MANT%" --dia 1 --hora 0 --ciclos 4464 --sa 10 --k 7 --iteraciones 300 %2 %3 %4 %5 %6 %7 %8 %9 > "resultados\sim_%MES%.txt"
findstr /c:"Resumen" /c:"COLAPSO LOG" "resultados\sim_%MES%.txt"
endlocal
