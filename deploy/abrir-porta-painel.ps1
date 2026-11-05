<#
.SYNOPSIS
    Abre na Firewall do Windows APENAS as portas dos painéis e da loja do SCAD (entrada TCP, todos os
    perfis de rede: -Profile Any), para que possam ser abertos a partir de outros portáteis/telemóveis:
      http://<IP deste PC>:8081  painel do NORTE
      http://<IP deste PC>:8082  painel do CENTRO
      http://<IP deste PC>:8083  painel do SUL
      http://<IP deste PC>:8080  vista geral
      http://<IP deste PC>:8085  loja (clientes)

.DESCRIPTION
    Executar numa PowerShell aberta como Administrador. Idempotente (substitui as regras anteriores).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1 -Remover
#>
param([switch] $Remover, [int[]] $Portas = @(8080, 8081, 8082, 8083, 8085))

$ErrorActionPreference = 'Stop'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Error 'Execute este script numa janela PowerShell aberta como Administrador.' }

Get-NetFirewallRule -DisplayName 'SCAD painel*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if ($Remover) { Write-Host 'Regras dos painéis e da loja SCAD removidas.'; return }

New-NetFirewallRule -DisplayName "SCAD painel e loja ($($Portas -join ', '))" -Direction Inbound -Protocol TCP `
    -LocalPort $Portas -Action Allow -Profile Any | Out-Null
Write-Host "Firewall do Windows: portas $($Portas -join ', ') abertas (entrada TCP, todos os perfis)."

$wslconfig = Join-Path $env:USERPROFILE '.wslconfig'
if ((Test-Path $wslconfig) -and (Select-String -Path $wslconfig -Pattern '^\s*networkingMode\s*=\s*mirrored' -Quiet)) {
    Write-Warning ("O WSL está em modo 'mirrored': as portas do Docker só respondem em localhost. Comente a linha " +
        "networkingMode=mirrored em $wslconfig, execute 'wsl --shutdown' e reinicie o Docker Desktop.")
}

$nomes = @{ 8080 = 'vista geral'; 8081 = 'NORTE'; 8082 = 'CENTRO'; 8083 = 'SUL'; 8085 = 'LOJA (clientes)' }
$ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|Loopback' }
foreach ($ip in $ips) {
    Write-Host "Rede '$($ip.InterfaceAlias)':"
    foreach ($p in $Portas) { Write-Host ("   http://{0}:{1}   {2}" -f $ip.IPAddress, $p, $nomes[$p]) }
}
