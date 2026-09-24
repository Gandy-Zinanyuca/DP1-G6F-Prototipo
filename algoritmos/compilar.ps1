param(
    [ValidateSet('todos', 'alns', 'tabu')][string]$Algoritmo = 'todos',
    [switch]$Pruebas
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path 'out' | Out-Null
    $raices = @('comun/src/main/java', 'alns/src/main/java', 'tabu/src/main/java', 'experimentacion/src/main/java', 'experimentacion/src/test/java')
    $fuentes = @(Get-ChildItem $raices -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    $fuentes += @('alns/src/pe/pucp/paqrap/alns/SelectorAdaptativo.java', 'alns/src/pe/pucp/paqrap/alns/CriterioAceptacion.java', 'alns/src/pe/pucp/paqrap/alns/ParametrosALNS.java')
    & javac --release 17 -encoding UTF-8 -d out $fuentes
    if ($LASTEXITCODE -ne 0) { throw 'Fallo al compilar los motores comparables' }
    Write-Host 'Motores comparables compilados: algoritmos/out (ejecucion independiente)'
    if ($Pruebas) {
        & java -cp out pe.pucp.paqrap.RestriccionesEstrictasTest
        if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas compartidas TS/ALNS' }
        & java -cp out pe.pucp.paqrap.ExperimentacionTest
        if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas de experimentacion' }
        & java -cp out pe.pucp.paqrap.SimulacionComparadaTest
        if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas de simulacion compartida' }
        & java -cp out pe.pucp.paqrap.AlimentacionTest
        if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas de alimentacion' }
    }
} finally {
    Pop-Location
}
