<#
Repite las corridas cuyo tiempo de pared quedo dominado por una llamada extrema.
Mantiene Windows despierto mientras el orquestador esta activo y conserva los
resultados originales en la campana principal.
#>
param(
    [string]$HeapSize = '2g',
    [string]$Salida = 'algoritmos/experimentacion/resultados/campana-alns100-tabu300-4x3-repeticiones-tiempo',
    [switch]$NoCompilar,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repoRoot

$trabajos = @(
    [pscustomobject]@{ Fecha = '2026-02'; Semilla = 20263; Algoritmo = 'ALNS' },
    [pscustomobject]@{ Fecha = '2026-08'; Semilla = 20262; Algoritmo = 'ALNS' },
    [pscustomobject]@{ Fecha = '2026-08'; Semilla = 20263; Algoritmo = 'ALNS' },
    [pscustomobject]@{ Fecha = '2026-08'; Semilla = 20263; Algoritmo = 'TS' },
    [pscustomobject]@{ Fecha = '2027-03'; Semilla = 20262; Algoritmo = 'TS' },
    [pscustomobject]@{ Fecha = '2027-03'; Semilla = 20263; Algoritmo = 'ALNS' }
)

foreach ($j in $trabajos) {
    $compacta = $j.Fecha -replace '-', ''
    $j | Add-Member -NotePropertyName Carpeta -NotePropertyValue (
        Join-Path $Salida "corridas/$compacta/$($j.Semilla)/$($j.Algoritmo)")
}

Write-Host "Repeticiones temporales: $($trabajos.Count) corridas; salida=$Salida"
if ($DryRun) {
    $trabajos | Format-Table Fecha, Semilla, Algoritmo, Carpeta -AutoSize
    return
}

if (-not $NoCompilar) {
    & .\algoritmos\compilar.bat -Pruebas
    if ($LASTEXITCODE -ne 0) { throw "La compilacion/pruebas fallo con codigo $LASTEXITCODE" }
}

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class ControlSuspensionPaqRap {
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint SetThreadExecutionState(uint flags);
}
'@
$ES_CONTINUOUS = [Convert]::ToUInt32('80000000', 16)
$ES_SYSTEM_REQUIRED = [uint32]0x00000001
$estadoAnterior = [ControlSuspensionPaqRap]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)
if ($estadoAnterior -eq 0) { throw "Windows rechazo la solicitud para impedir la suspension" }

$ventas = 'algoritmos/alns/data/ventas.v20260909'
$bloqueos = 'algoritmos/alns/data/bloqueos.v20260909'
$logs = Join-Path $Salida 'logs'
$manifestPath = Join-Path $Salida 'corridas.csv'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function TieneResultado([string]$carpeta) {
    $resumen = Join-Path $carpeta 'resumen.csv'
    return (Test-Path $resumen) -and (@(Get-Content $resumen -ErrorAction SilentlyContinue).Count -ge 2)
}

try {
    $numero = 0
    foreach ($j in $trabajos) {
        $numero++
        if (TieneResultado $j.Carpeta) {
            Write-Host "[$numero/$($trabajos.Count)] OMITIDA $($j.Fecha) $($j.Semilla) $($j.Algoritmo)"
            continue
        }
        if (Test-Path $j.Carpeta) {
            $preservada = "$($j.Carpeta)-incompleta-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
            Move-Item -LiteralPath $j.Carpeta -Destination $preservada
        }
        $etiqueta = "$($j.Fecha -replace '-', '')-$($j.Semilla)-$($j.Algoritmo)"
        $outLog = Join-Path $logs "$etiqueta.out.log"
        $errLog = Join-Path $logs "$etiqueta.err.log"
        $argumentos = @(
            '-Dfile.encoding=UTF-8', "-Xmx$HeapSize", '-cp', 'algoritmos/out',
            'pe.pucp.paqrap.SimulacionComparada', $j.Algoritmo, $ventas, $bloqueos, '-',
            $j.Fecha, $j.Carpeta, '300', "$($j.Semilla)", '4320', '10', '1', '100', '0', '2'
        )
        $inicio = Get-Date
        Write-Host "[$numero/$($trabajos.Count)] INICIO $etiqueta (suspension inhibida)"
        $proceso = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $repoRoot `
            -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -NoNewWindow
        $proceso.WaitForExit()
        $fin = Get-Date
        $valido = TieneResultado $j.Carpeta
        [pscustomobject]@{
            fecha = $j.Fecha
            semilla = $j.Semilla
            algoritmo = $j.Algoritmo
            inicio_real = $inicio.ToString('o')
            fin_real = $fin.ToString('o')
            tiempo_proceso_real_ms = [Math]::Round(($fin - $inicio).TotalMilliseconds, 3)
            exit_code = $proceso.ExitCode
            resultado_valido = $valido
            iteraciones_ts = 300
            iteraciones_alns = 100
            destruccion_alns = 2
            presupuesto_ms = 0
            max_ciclos = 4320
            sa_min = 10
            factor_carga = 1
            suspension_inhibida = $true
            motivo = 'repeticion_tiempo_atipico'
            carpeta = $j.Carpeta
        } | Export-Csv -Path $manifestPath -NoTypeInformation -Encoding utf8 -Append
        Write-Host "[$numero/$($trabajos.Count)] FIN $etiqueta exit=$($proceso.ExitCode) valido=$valido tiempo=$([Math]::Round(($fin-$inicio).TotalMinutes, 2)) min"
        if ($proceso.ExitCode -ne 0 -or -not $valido) {
            throw "Fallo la repeticion $etiqueta. Revise $errLog y $outLog"
        }
    }
} finally {
    [void][ControlSuspensionPaqRap]::SetThreadExecutionState($ES_CONTINUOUS)
}

$completas = @($trabajos | Where-Object { TieneResultado $_.Carpeta }).Count
Write-Host "Repeticiones terminadas: $completas/$($trabajos.Count). Suspension nuevamente administrada por Windows."
if ($completas -ne $trabajos.Count) { exit 2 }
