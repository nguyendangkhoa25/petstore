# Build and Deploy Script for Order Items Reserver Azure Function

param(
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName,
    
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,
    
    [Parameter(Mandatory=$true)]
    [string]$ContainerRegistryName
)

Write-Host "Building and deploying Order Items Reserver Function..." -ForegroundColor Green

# Navigate to project directory
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location $projectDir

# 1. Clean and build with Maven
Write-Host "Building project with Maven..." -ForegroundColor Yellow
mvn clean package

if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    exit 1
}

# 2. Login to ACR
Write-Host "Logging in to Azure Container Registry..." -ForegroundColor Yellow
az acr login --name $ContainerRegistryName

# 3. Build Docker image
$imageName = "$ContainerRegistryName.azurecr.io/order-items-reserver:latest"
Write-Host "Building Docker image: $imageName" -ForegroundColor Yellow
docker build -t $imageName .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker build failed!" -ForegroundColor Red
    exit 1
}

# 4. Push to ACR
Write-Host "Pushing image to Azure Container Registry..." -ForegroundColor Yellow
docker push $imageName

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker push failed!" -ForegroundColor Red
    exit 1
}

# 5. Update Function App to use new image
Write-Host "Updating Function App with new container image..." -ForegroundColor Yellow
az functionapp config container set `
    --name $FunctionAppName `
    --resource-group $ResourceGroupName `
    --docker-custom-image-name $imageName

# 6. Restart Function App
Write-Host "Restarting Function App..." -ForegroundColor Yellow
az functionapp restart --name $FunctionAppName --resource-group $ResourceGroupName

Write-Host "`n==================================================" -ForegroundColor Green
Write-Host "Deployment Complete!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host "Function App URL: https://$FunctionAppName.azurewebsites.net" -ForegroundColor Cyan
Write-Host "`nTo view logs:" -ForegroundColor Cyan
Write-Host "az functionapp log tail --name $FunctionAppName --resource-group $ResourceGroupName" -ForegroundColor White
Write-Host "==================================================" -ForegroundColor Green
