@echo off
REM Simulacion hasta el colapso: arranca en el mes indicado y encadena los meses siguientes de data\
REM hasta que un pedido no pueda entregarse a tiempo (o se acaben los datos).
REM Uso:      colapso.bat AAAAMM [opciones extra]
REM Ejemplos: colapso.bat 202601
REM           colapso.bat 202601 --iteraciones 1000 --semilla 7
REM Salida en resultados\colapso_AAAAMM.txt; cada corrida agrega una fila a resultados\experimentos_colapso.csv
REM Las opciones extra reemplazan a las de por defecto (--dia 1 --hora 0 --sa 10 --k 7 --iteraciones 300).
setlocal
cd /d "%~dp0"

if "%~1"=="" (
    echo Uso: colapso.bat AAAAMM [opciones extra]
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

echo Simulando hasta el colapso desde %MES% ...  salida en resultados\colapso_%MES%.txt
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador "%VENTAS%" "%BLOQUEOS%" "%MANT%" --colapso --dia 1 --hora 0 --sa 10 --k 7 --iteraciones 300 --csv-resumen resultados\experimentos_colapso.csv %2 %3 %4 %5 %6 %7 %8 %9 > "resultados\colapso_%MES%.txt"
findstr /c:"COLAPSO LOG" /c:"Fin de la simul" /c:"Causa del" /c:"Periodo simulado" /c:"Tiempo real" "resultados\colapso_%MES%.txt"
endlocal
