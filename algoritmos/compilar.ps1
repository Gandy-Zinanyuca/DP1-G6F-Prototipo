param(
    [ValidateSet('todos', 'alns', 'tabu')][string]$Algoritmo = 'todos',
    [switch]$Pruebas
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if ($Algoritmo -in @('todos', 'alns')) {
        New-Item -ItemType Directory -Force -Path 'alns/out' | Out-Null
        $fuentes = @(Get-ChildItem 'alns/src' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
        & javac --release 17 -encoding UTF-8 -d alns/out $fuentes
        if ($LASTEXITCODE -ne 0) { throw 'Fallo al compilar ALNS' }
        Write-Host 'ALNS compilado: algoritmos/alns/out'
    }
    if ($Algoritmo -in @('todos', 'tabu')) {
        New-Item -ItemType Directory -Force -Path 'tabu/out' | Out-Null
        $raices = @('comun/src/main/java', 'tabu/src/main/java', 'experimentacion/src/main/java', 'experimentacion/src/test/java')
        $fuentes = @(Get-ChildItem $raices -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
        & javac --release 17 -encoding UTF-8 -d tabu/out $fuentes
        if ($LASTEXITCODE -ne 0) { throw 'Fallo al compilar Tabu Search' }
        Write-Host 'TS compilado: algoritmos/tabu/out'
    }
    if ($Pruebas) {
        if ($Algoritmo -in @('todos', 'tabu')) {
            & java -cp tabu/out pe.pucp.paqrap.RestriccionesTabuTest
            if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas TS' }
        }
        if ($Algoritmo -in @('todos', 'alns')) {
            & java '-Dfile.encoding=UTF-8' -cp alns/out pe.pucp.paqrap.PruebaPlanificador alns/data/ventas.v20260909/ventas.202601.txt alns/data/bloqueos.v20260909/bloqueo.2601.txt --dia 1 --hora 1 --iteraciones 20
            if ($LASTEXITCODE -ne 0) { throw 'Fallo en las pruebas ALNS' }
        }
    }
} finally {
    Pop-Location
}
