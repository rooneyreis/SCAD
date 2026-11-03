<#
.SYNOPSIS
    Abre na Firewall do Windows as portas TCP de entrada usadas pelo SCAD nesta máquina.

.DESCRIPTION
    Tem de ser executado como Administrador. Cria uma regra de entrada por porta, com o
    prefixo "SCAD", válida em todos os perfis de rede (Domínio, Privado, Público).
    Pode ser executado várias vezes (as regras antigas do SCAD são substituídas).

.PARAMETER Maquina
    M1 (NORTE), M2 (CENTRO) ou M3 (SUL).

.PARAMETER Remover
    Remove todas as regras "SCAD" em vez de as criar.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-portas.ps1 -Maquina M1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-portas.ps1 -Remover
#>
param(
    [ValidateSet('M1', 'M2', 'M3')]
    [string] $Maquina,
    [switch] $Remover
)

$ErrorActionPreference = 'Stop'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Write-Error 'Execute este script numa janela PowerShell aberta como Administrador.'
}

# Remove regras anteriores do SCAD (torna o script idempotente)
Get-NetFirewallRule -DisplayName 'SCAD *' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if ($Remover) {
    Write-Host 'Regras SCAD removidas.'
    return
}
if (-not $Maquina) {
    Write-Error 'Indique a máquina: -Maquina M1 | M2 | M3'
}

# Portas comuns a todas as máquinas
$portas = [ordered]@{
    8080 = 'no-scad (API REST + painel)'
    3701 = 'agente de stock CORBA'
    5432 = 'PostgreSQL primário'
    5433 = 'PostgreSQL réplica'
    9864 = 'HDFS DataNode (HTTP)'
    9866 = 'HDFS DataNode (dados)'
    9867 = 'HDFS DataNode (IPC)'
}
switch ($Maquina) {
    'M1' {
        $portas[8020] = 'HDFS NameNode (RPC)'
        $portas[9870] = 'HDFS NameNode (UI web)'
        $portas[3700] = 'Central de Auditoria CORBA'
        $portas[2809] = 'CORBA Naming Service'
    }
    'M3' {
        $portas[8090] = 'fornecedor-mock (REST + SOAP)'
    }
}

foreach ($porta in $portas.Keys) {
    New-NetFirewallRule -DisplayName "SCAD $porta - $($portas[$porta])" `
        -Direction Inbound -Protocol TCP -LocalPort $porta -Action Allow -Profile Any | Out-Null
    Write-Host ("  aberta {0,-5} {1}" -f $porta, $portas[$porta])
}
Write-Host "Firewall configurada para $Maquina ($($portas.Count) portas)."
