# Azure Infrastructure Setup Script for PetStore Order Items Reserver
# This script creates the required Azure resources

param(
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName,
    
    [Parameter(Mandatory=$true)]
    [string]$Location = "southeastasia",
    
    [Parameter(Mandatory=$true)]
    [string]$ServiceBusNamespace,
    
    [Parameter(Mandatory=$true)]
    [string]$StorageAccountName,
    
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,
    
    [Parameter(Mandatory=$true)]
    [string]$LogicAppName,
    
    [Parameter(Mandatory=$true)]
    [string]$ManagerEmail
)

Write-Host "Starting Azure infrastructure setup..." -ForegroundColor Green

# 1. Create Resource Group
Write-Host "Creating Resource Group: $ResourceGroupName" -ForegroundColor Yellow
az group create --name $ResourceGroupName --location $Location

# 2. Create Service Bus Namespace (Premium tier for DLQ support)
Write-Host "Creating Service Bus Namespace: $ServiceBusNamespace" -ForegroundColor Yellow
az servicebus namespace create `
    --resource-group $ResourceGroupName `
    --name $ServiceBusNamespace `
    --location $Location `
    --sku Premium

# 3. Create Service Bus Queue with Dead-Letter configuration
Write-Host "Creating Service Bus Queue: order-updates" -ForegroundColor Yellow
az servicebus queue create `
    --resource-group $ResourceGroupName `
    --namespace-name $ServiceBusNamespace `
    --name order-updates `
    --max-delivery-count 3 `
    --enable-dead-lettering-on-message-expiration true `
    --default-message-time-to-live P1D `
    --lock-duration PT5M

# 4. Get Service Bus Connection String
Write-Host "Retrieving Service Bus Connection String..." -ForegroundColor Yellow
$serviceBusConnString = az servicebus namespace authorization-rule keys list `
    --resource-group $ResourceGroupName `
    --namespace-name $ServiceBusNamespace `
    --name RootManageSharedAccessKey `
    --query primaryConnectionString `
    --output tsv

# 5. Create Storage Account
Write-Host "Creating Storage Account: $StorageAccountName" -ForegroundColor Yellow
az storage account create `
    --name $StorageAccountName `
    --resource-group $ResourceGroupName `
    --location $Location `
    --sku Standard_LRS `
    --kind StorageV2

# 6. Get Storage Connection String
Write-Host "Retrieving Storage Connection String..." -ForegroundColor Yellow
$storageConnString = az storage account show-connection-string `
    --name $StorageAccountName `
    --resource-group $ResourceGroupName `
    --query connectionString `
    --output tsv

# 7. Create Blob Container
Write-Host "Creating Blob Container: order-requests" -ForegroundColor Yellow
az storage container create `
    --name order-requests `
    --account-name $StorageAccountName `
    --connection-string $storageConnString

# 8. Create Container Registry (for Function App container)
$acrName = $FunctionAppName + "acr"
Write-Host "Creating Container Registry: $acrName" -ForegroundColor Yellow
az acr create `
    --resource-group $ResourceGroupName `
    --name $acrName `
    --sku Basic `
    --admin-enabled true

# 9. Create App Service Plan for Linux containers
$appServicePlan = $FunctionAppName + "-plan"
Write-Host "Creating App Service Plan: $appServicePlan" -ForegroundColor Yellow
az appservice plan create `
    --name $appServicePlan `
    --resource-group $ResourceGroupName `
    --location $Location `
    --is-linux `
    --sku P1V2

# 10. Create Function App with container deployment
Write-Host "Creating Function App: $FunctionAppName" -ForegroundColor Yellow
az functionapp create `
    --resource-group $ResourceGroupName `
    --name $FunctionAppName `
    --plan $appServicePlan `
    --storage-account $StorageAccountName `
    --runtime java `
    --runtime-version 17 `
    --functions-version 4 `
    --deployment-container-image-name "$acrName.azurecr.io/order-items-reserver:latest"

# 11. Configure Function App Settings
Write-Host "Configuring Function App Settings..." -ForegroundColor Yellow
az functionapp config appsettings set `
    --name $FunctionAppName `
    --resource-group $ResourceGroupName `
    --settings `
        "SERVICEBUS_CONNECTION_STRING=$serviceBusConnString" `
        "SERVICEBUS_QUEUE_NAME=order-updates" `
        "BLOB_CONNECTION_STRING=$storageConnString" `
        "BLOB_CONTAINER_NAME=order-requests" `
        "WEBSITE_ENABLE_SYNC_UPDATE_SITE=true"

# 12. Create Logic App
Write-Host "Creating Logic App: $LogicAppName" -ForegroundColor Yellow
az logic workflow create `
    --resource-group $ResourceGroupName `
    --location $Location `
    --name $LogicAppName `
    --definition "@logic-app/dlq-email-notification.json"

Write-Host "`n==================================================" -ForegroundColor Green
Write-Host "Infrastructure Setup Complete!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host "`nConnection Strings (save these securely):" -ForegroundColor Cyan
Write-Host "Service Bus: $serviceBusConnString" -ForegroundColor White
Write-Host "Storage: $storageConnString" -ForegroundColor White
Write-Host "`nNext Steps:" -ForegroundColor Cyan
Write-Host "1. Build and push Docker image to ACR" -ForegroundColor White
Write-Host "2. Configure Logic App connections (Service Bus and Office 365)" -ForegroundColor White
Write-Host "3. Update petstoreorderservice with Service Bus connection string" -ForegroundColor White
Write-Host "4. Set manager email parameter in Logic App: $ManagerEmail" -ForegroundColor White
Write-Host "==================================================" -ForegroundColor Green
