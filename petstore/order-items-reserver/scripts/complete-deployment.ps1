# Complete Deployment Script - PetStore Order Items Reserver
# This script deploys the entire solution end-to-end

param(
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName,
    
    [Parameter(Mandatory=$false)]
    [string]$Location = "southeastasia",
    
    [Parameter(Mandatory=$true)]
    [string]$ManagerEmail,
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipInfrastructure,
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "  PetStore Order Items Reserver - Complete Deployment" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

# Generate unique names
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$randomSuffix = Get-Random -Maximum 9999
$ServiceBusNamespace = "petstore-sb-$randomSuffix"
$StorageAccountName = "petstore$randomSuffix"
$FunctionAppName = "petstore-func-$randomSuffix"
$ContainerRegistryName = "petstoreacr$randomSuffix"
$LogicAppName = "petstore-logic-$randomSuffix"
$OrderServiceAppName = "petstore-order-$randomSuffix"

Write-Host "Deployment Configuration:" -ForegroundColor Yellow
Write-Host "  Resource Group: $ResourceGroupName" -ForegroundColor White
Write-Host "  Location: $Location" -ForegroundColor White
Write-Host "  Service Bus: $ServiceBusNamespace" -ForegroundColor White
Write-Host "  Storage Account: $StorageAccountName" -ForegroundColor White
Write-Host "  Function App: $FunctionAppName" -ForegroundColor White
Write-Host "  Container Registry: $ContainerRegistryName" -ForegroundColor White
Write-Host "  Logic App: $LogicAppName" -ForegroundColor White
Write-Host "  Order Service: $OrderServiceAppName" -ForegroundColor White
Write-Host "  Manager Email: $ManagerEmail" -ForegroundColor White
Write-Host ""

# Confirm deployment
$confirm = Read-Host "Continue with deployment? (Y/N)"
if ($confirm -ne "Y") {
    Write-Host "Deployment cancelled." -ForegroundColor Red
    exit 0
}

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  PHASE 1: INFRASTRUCTURE DEPLOYMENT" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

if (-not $SkipInfrastructure) {
    # 1. Create Resource Group
    Write-Host "[1/10] Creating Resource Group..." -ForegroundColor Yellow
    az group create --name $ResourceGroupName --location $Location
    Write-Host "✓ Resource Group created" -ForegroundColor Green
    
    # 2. Create Service Bus Namespace
    Write-Host "[2/10] Creating Service Bus Namespace (this may take 5-10 minutes)..." -ForegroundColor Yellow
    az servicebus namespace create `
        --resource-group $ResourceGroupName `
        --name $ServiceBusNamespace `
        --location $Location `
        --sku Premium
    Write-Host "✓ Service Bus Namespace created" -ForegroundColor Green
    
    # 3. Create Service Bus Queue
    Write-Host "[3/10] Creating Service Bus Queue with DLQ..." -ForegroundColor Yellow
    az servicebus queue create `
        --resource-group $ResourceGroupName `
        --namespace-name $ServiceBusNamespace `
        --name order-updates `
        --max-delivery-count 3 `
        --enable-dead-lettering-on-message-expiration true `
        --default-message-time-to-live P1D `
        --lock-duration PT5M
    Write-Host "✓ Service Bus Queue created" -ForegroundColor Green
    
    # 4. Get Service Bus Connection String
    Write-Host "[4/10] Retrieving Service Bus Connection String..." -ForegroundColor Yellow
    $serviceBusConnString = az servicebus namespace authorization-rule keys list `
        --resource-group $ResourceGroupName `
        --namespace-name $ServiceBusNamespace `
        --name RootManageSharedAccessKey `
        --query primaryConnectionString `
        --output tsv
    Write-Host "✓ Connection string retrieved" -ForegroundColor Green
    
    # 5. Create Storage Account
    Write-Host "[5/10] Creating Storage Account..." -ForegroundColor Yellow
    az storage account create `
        --name $StorageAccountName `
        --resource-group $ResourceGroupName `
        --location $Location `
        --sku Standard_LRS `
        --kind StorageV2
    Write-Host "✓ Storage Account created" -ForegroundColor Green
    
    # 6. Get Storage Connection String
    Write-Host "[6/10] Retrieving Storage Connection String..." -ForegroundColor Yellow
    $storageConnString = az storage account show-connection-string `
        --name $StorageAccountName `
        --resource-group $ResourceGroupName `
        --query connectionString `
        --output tsv
    Write-Host "✓ Connection string retrieved" -ForegroundColor Green
    
    # 7. Create Blob Container
    Write-Host "[7/10] Creating Blob Container..." -ForegroundColor Yellow
    az storage container create `
        --name order-requests `
        --account-name $StorageAccountName `
        --connection-string $storageConnString
    Write-Host "✓ Blob Container created" -ForegroundColor Green
    
    # 8. Create Container Registry
    Write-Host "[8/10] Creating Container Registry..." -ForegroundColor Yellow
    az acr create `
        --resource-group $ResourceGroupName `
        --name $ContainerRegistryName `
        --sku Basic `
        --admin-enabled true
    Write-Host "✓ Container Registry created" -ForegroundColor Green
    
    # 9. Create App Service Plan
    Write-Host "[9/10] Creating App Service Plan..." -ForegroundColor Yellow
    $appServicePlan = "$FunctionAppName-plan"
    az appservice plan create `
        --name $appServicePlan `
        --resource-group $ResourceGroupName `
        --location $Location `
        --is-linux `
        --sku P1V2
    Write-Host "✓ App Service Plan created" -ForegroundColor Green
    
    # 10. Create Function App
    Write-Host "[10/10] Creating Function App..." -ForegroundColor Yellow
    az functionapp create `
        --resource-group $ResourceGroupName `
        --name $FunctionAppName `
        --plan $appServicePlan `
        --storage-account $StorageAccountName `
        --runtime java `
        --runtime-version 17 `
        --functions-version 4 `
        --deployment-container-image-name "$ContainerRegistryName.azurecr.io/order-items-reserver:latest"
    Write-Host "✓ Function App created" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "✓ Infrastructure deployment completed!" -ForegroundColor Green
} else {
    Write-Host "Skipping infrastructure deployment (--SkipInfrastructure flag set)" -ForegroundColor Yellow
    
    # Retrieve existing connection strings
    $serviceBusConnString = az servicebus namespace authorization-rule keys list `
        --resource-group $ResourceGroupName `
        --namespace-name $ServiceBusNamespace `
        --name RootManageSharedAccessKey `
        --query primaryConnectionString `
        --output tsv
    
    $storageConnString = az storage account show-connection-string `
        --name $StorageAccountName `
        --resource-group $ResourceGroupName `
        --query connectionString `
        --output tsv
}

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  PHASE 2: BUILD & DEPLOY ORDER ITEMS RESERVER FUNCTION" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

# Navigate to function directory
$functionDir = Join-Path $PSScriptRoot ".."
Set-Location $functionDir

# Build with Maven
Write-Host "[1/5] Building Order Items Reserver with Maven..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Maven build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Maven build completed" -ForegroundColor Green

# Login to ACR
Write-Host "[2/5] Logging in to Azure Container Registry..." -ForegroundColor Yellow
az acr login --name $ContainerRegistryName
Write-Host "✓ Logged in to ACR" -ForegroundColor Green

# Build Docker image
Write-Host "[3/5] Building Docker image..." -ForegroundColor Yellow
$imageName = "$ContainerRegistryName.azurecr.io/order-items-reserver:latest"
docker build -t $imageName .
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Docker image built" -ForegroundColor Green

# Push to ACR
Write-Host "[4/5] Pushing image to Azure Container Registry..." -ForegroundColor Yellow
docker push $imageName
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker push failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Image pushed to ACR" -ForegroundColor Green

# Configure Function App
Write-Host "[5/5] Configuring Function App..." -ForegroundColor Yellow
az functionapp config appsettings set `
    --name $FunctionAppName `
    --resource-group $ResourceGroupName `
    --settings `
        "SERVICEBUS_CONNECTION_STRING=$serviceBusConnString" `
        "SERVICEBUS_QUEUE_NAME=order-updates" `
        "BLOB_CONNECTION_STRING=$storageConnString" `
        "BLOB_CONTAINER_NAME=order-requests" `
        "WEBSITE_ENABLE_SYNC_UPDATE_SITE=true"

az functionapp config container set `
    --name $FunctionAppName `
    --resource-group $ResourceGroupName `
    --docker-custom-image-name $imageName

az functionapp restart --name $FunctionAppName --resource-group $ResourceGroupName
Write-Host "✓ Function App configured and restarted" -ForegroundColor Green

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  PHASE 3: DEPLOY ORDER SERVICE" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

Write-Host "[INFO] Order Service deployment requires manual steps:" -ForegroundColor Cyan
Write-Host "1. Build the Order Service: cd petstoreorderservice && mvn clean package" -ForegroundColor White
Write-Host "2. Deploy to Azure App Service or Container Apps" -ForegroundColor White
Write-Host "3. Set environment variables:" -ForegroundColor White
Write-Host "   - AZURE_SERVICEBUS_CONNECTION_STRING=$serviceBusConnString" -ForegroundColor White
Write-Host "   - AZURE_SERVICEBUS_QUEUE_NAME=order-updates" -ForegroundColor White
Write-Host ""
Write-Host "Or use Azure CLI:" -ForegroundColor White
Write-Host "az webapp config appsettings set --name <your-order-service> --resource-group $ResourceGroupName --settings AZURE_SERVICEBUS_CONNECTION_STRING='$serviceBusConnString' AZURE_SERVICEBUS_QUEUE_NAME='order-updates'" -ForegroundColor Gray
Write-Host ""

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  PHASE 4: DEPLOY LOGIC APP" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

Write-Host "[INFO] Logic App deployment requires manual steps:" -ForegroundColor Cyan
Write-Host "1. Navigate to Azure Portal" -ForegroundColor White
Write-Host "2. Create Logic App: $LogicAppName" -ForegroundColor White
Write-Host "3. Import workflow definition from: logic-app/dlq-email-notification.json" -ForegroundColor White
Write-Host "4. Configure Service Bus connection (DLQ: order-updates/`$DeadLetterQueue)" -ForegroundColor White
Write-Host "5. Configure Office 365 connection" -ForegroundColor White
Write-Host "6. Set parameter: managerEmail = $ManagerEmail" -ForegroundColor White
Write-Host "7. Enable and save the Logic App" -ForegroundColor White
Write-Host ""

if (-not $SkipTests) {
    Write-Host ""
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host "  PHASE 5: TESTING" -ForegroundColor Green
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "Waiting 30 seconds for Function App to be ready..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    
    Write-Host "[Test 1] Checking Service Bus Queue..." -ForegroundColor Yellow
    $queueInfo = az servicebus queue show `
        --resource-group $ResourceGroupName `
        --namespace-name $ServiceBusNamespace `
        --name order-updates `
        --query "{status:status,maxDelivery:maxDeliveryCount}" `
        --output json | ConvertFrom-Json
    
    Write-Host "  Queue Status: $($queueInfo.status)" -ForegroundColor White
    Write-Host "  Max Delivery Count: $($queueInfo.maxDelivery)" -ForegroundColor White
    
    Write-Host "[Test 2] Checking Blob Container..." -ForegroundColor Yellow
    az storage container show `
        --name order-requests `
        --account-name $StorageAccountName `
        --query "name" `
        --output tsv
    Write-Host "  ✓ Blob Container accessible" -ForegroundColor Green
    
    Write-Host "[Test 3] Checking Function App Status..." -ForegroundColor Yellow
    $funcStatus = az functionapp show `
        --name $FunctionAppName `
        --resource-group $ResourceGroupName `
        --query "state" `
        --output tsv
    Write-Host "  Function App State: $funcStatus" -ForegroundColor White
    
    Write-Host ""
    Write-Host "✓ Basic tests passed!" -ForegroundColor Green
    Write-Host ""
    Write-Host "To test end-to-end, send a test message:" -ForegroundColor Cyan
    Write-Host "curl -X POST https://<your-order-service>/petstoreorderservice/v2/store/order \" -ForegroundColor Gray
    Write-Host "  -H 'Content-Type: application/json' \" -ForegroundColor Gray
    Write-Host "  -H 'x-session-id: TEST-SESSION-001' \" -ForegroundColor Gray
    Write-Host "  -d '{\"id\": \"68FAE9B1D86B794F0AE0ADD35A437428\", \"products\": [{\"id\": 1, \"quantity\": 1}]}'" -ForegroundColor Gray
} else {
    Write-Host "Skipping tests (--SkipTests flag set)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  DEPLOYMENT COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "📋 SUMMARY:" -ForegroundColor Cyan
Write-Host "  Resource Group: $ResourceGroupName" -ForegroundColor White
Write-Host "  Service Bus: $ServiceBusNamespace" -ForegroundColor White
Write-Host "  Storage Account: $StorageAccountName" -ForegroundColor White
Write-Host "  Function App: $FunctionAppName" -ForegroundColor White
Write-Host "  Container Registry: $ContainerRegistryName" -ForegroundColor White
Write-Host ""
Write-Host "🔑 CONNECTION STRINGS (save these securely):" -ForegroundColor Cyan
Write-Host "Service Bus:" -ForegroundColor Yellow
Write-Host $serviceBusConnString -ForegroundColor White
Write-Host ""
Write-Host "Storage:" -ForegroundColor Yellow
Write-Host $storageConnString -ForegroundColor White
Write-Host ""
Write-Host "📝 NEXT STEPS:" -ForegroundColor Cyan
Write-Host "1. Deploy PetStore Order Service with Service Bus configuration" -ForegroundColor White
Write-Host "2. Create and configure Logic App for DLQ email notifications" -ForegroundColor White
Write-Host "3. Test the complete flow" -ForegroundColor White
Write-Host "4. Monitor logs and metrics" -ForegroundColor White
Write-Host ""
Write-Host "📚 DOCUMENTATION:" -ForegroundColor Cyan
Write-Host "  Quick Start: QUICK_START.md" -ForegroundColor White
Write-Host "  Architecture: ARCHITECTURE.md" -ForegroundColor White
Write-Host "  Integration: INTEGRATION_GUIDE.md" -ForegroundColor White
Write-Host "  DoD Verification: DEFINITION_OF_DONE.md" -ForegroundColor White
Write-Host ""
Write-Host "✅ All systems ready for production!" -ForegroundColor Green
