<#
.SYNOPSIS
    Demonstração: vários clientes compram a ÚLTIMA unidade de um produto ao mesmo tempo; só um consegue.

.DESCRIPTION
    1) Um "grossista" compra tudo menos 1 unidade do produto na cidade escolhida (fica PENDENTE no painel).
    2) N clientes compram essa última unidade EM SIMULTÂNEO (pedidos HTTP em paralelo à API da loja).
    Resultado esperado: 1 × 201 (pedido criado) e N-1 × 409 ("esgotou entretanto").
    Depois, no painel do armazém, recuse o pedido do grossista para libertar a reserva.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\demo-ultima-unidade.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\demo-ultima-unidade.ps1 -Cidade CENTRO -Produto OLEO-5 -Clientes 8
#>
param(
    [ValidateSet('NORTE', 'CENTRO', 'SUL')] [string] $Cidade = 'SUL',
    [string] $Produto = 'FEIJAO-10',
    [int] $Clientes = 6,
    [string] $Loja = 'http://localhost:8085'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$api = "$Loja/api/$($Cidade.ToLower())/loja"

function Disponivel {
    # (parênteses: no PowerShell 5.1 a lista JSON só é desdobrada no pipeline assim)
    $c = (Invoke-RestMethod "$api/catalogo") | Where-Object { $_.armazemId -eq $Cidade }
    ($c.produtos | Where-Object { $_.id -eq $Produto }).disponivel
}
function Corpo([string] $nome, [int] $quantidade) {
    @{ armazemId = $Cidade; clienteNome = $nome; clienteTelefone = '840000000'
       itens = @(@{ produtoId = $Produto; quantidade = $quantidade }) } | ConvertTo-Json -Depth 4
}

$d = Disponivel
if ($d -lt 2) { throw "Só há $d de $Produto em ${Cidade}: escolha outro produto." }
Write-Host "Disponível de $Produto em $Cidade : $d"
$grossista = Invoke-RestMethod "$api/pedidos" -Method Post -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes((Corpo 'Grossista (demo)' ($d - 1))))
Write-Host "Grossista reservou $($d - 1) -> $($grossista.id) (PENDENTE). Resta: $(Disponivel)"

# Todos os pedidos partem ao mesmo tempo (tarefas assíncronas do HttpClient)
$http = New-Object System.Net.Http.HttpClient
$tarefas = 1..$Clientes | ForEach-Object {
    $conteudo = New-Object System.Net.Http.StringContent((Corpo "Cliente $_" 1), [Text.Encoding]::UTF8, 'application/json')
    $http.PostAsync("$api/pedidos", $conteudo)
}
[System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]] $tarefas)

$i = 0
foreach ($t in $tarefas) {
    $i++
    $r = $t.Result
    $corpo = $r.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    if ([int] $r.StatusCode -eq 201) { Write-Host ("Cliente {0}: {1} CONSEGUIU -> {2}" -f $i, [int] $r.StatusCode, $corpo.id) -ForegroundColor Green }
    else { Write-Host ("Cliente {0}: {1} {2}" -f $i, [int] $r.StatusCode, $corpo.detail) -ForegroundColor Yellow }
}
Write-Host "Disponível agora: $(Disponivel). Recuse $($grossista.id) no painel do armazém para libertar a reserva."
