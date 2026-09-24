@echo off
REM Ejecuta N simulaciones hasta el colapso con ALNS, una por semilla (1..N), una tras otra.
REM Uso:      experimentos_colapso.bat N [opciones extra de DemoPlanificador]
REM Ejemplos: experimentos_colapso.bat 30
REM           experimentos_colapso.bat 30 --iteraciones 50
REM Cada corrida deja resultados\experimentos\alns\alns_semilla_S.csv (resumen) y .txt (salida).
REM Las semillas que ya tienen su CSV se omiten, asi que puede relanzarse para completar la campana.
REM En Linux, experimentos_colapso.sh hace lo mismo en paralelo.
setlocal enabledelayedexpansion
cd /d "%~dp0"

if "%~1"=="" (
    echo Uso: experimentos_colapso.bat N [opciones extra]
    exit /b 1
)
set N=%~1
set VENTAS=data\ventas.v20260909\ventas.202601.txt
set BLOQUEOS=data\bloqueos.v20260909\bloqueo.2601.txt
set MANT=data\mant.preventivo.09.10.txt
set DESTINO=resultados\experimentos\alns

call compilar.bat || exit /b 1
if not exist "%DESTINO%" mkdir "%DESTINO%"

for /l %%S in (1,1,%N%) do (
    if exist "%DESTINO%\alns_semilla_%%S.csv" (
        echo semilla %%S: ya existe, se omite
    ) else (
        echo semilla %%S: simulando...
        java -Xmx1g -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador "%VENTAS%" "%BLOQUEOS%" "%MANT%" --colapso --dia 1 --hora 0 --sa 10 --k 7 --iteraciones 200 --semilla %%S %2 %3 %4 %5 %6 %7 %8 %9 --csv-resumen "%DESTINO%\alns_semilla_%%S.csv.tmp" > "%DESTINO%\alns_semilla_%%S.txt" 2>&1
        move /y "%DESTINO%\alns_semilla_%%S.csv.tmp" "%DESTINO%\alns_semilla_%%S.csv" > nul
        findstr /c:"COLAPSO LOG" "%DESTINO%\alns_semilla_%%S.txt"
    )
)
echo Resultados en %DESTINO%\
endlocal
