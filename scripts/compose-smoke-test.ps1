param(
  [string]$GatewayUrl = "http://localhost:8080",
  [int]$MaxWaitSeconds = 240
)

function Cleanup {
  docker compose down -v --remove-orphans | Out-Host
}

try {
  Write-Host "Starting compose stack..."
  docker compose up -d --build | Out-Host

  Write-Host "Waiting for gateway health..."
  $start = Get-Date
  while ($true) {
    try {
      $response = Invoke-WebRequest -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5
      if ($response.StatusCode -eq 200) { break }
    } catch {
      # keep waiting
    }

    if ((Get-Date) -gt $start.AddSeconds($MaxWaitSeconds)) {
      Write-Error "Gateway health did not become ready in ${MaxWaitSeconds}s."
      docker compose ps | Out-Host
      docker compose logs --no-color gateway-service | Out-Host
      exit 1
    }
    Start-Sleep -Seconds 5
  }

  Write-Host "Smoke check: CVE list endpoint..."
  $status = 0
  try {
    $resp = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/cves?page=0&size=1" -Method Get -TimeoutSec 10
    $status = $resp.StatusCode
  } catch {
    $status = $_.Exception.Response.StatusCode.Value__
  }

  if ($status -ne 200) {
    Write-Error "CVE list returned status $status"
    exit 1
  }

  Write-Host "Compose smoke test passed."
}
finally {
  Cleanup
}
