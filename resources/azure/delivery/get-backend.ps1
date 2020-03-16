<#
Download code from one Azure Web App using Kudu API in zip archive, extract it, add file with modules versions, delete 'App_Data' folder and *.zip files from archive, create archive and upload it to another Azure Web App site.

See:
https://github.com/projectkudu/kudu/wiki/REST-API
https://theposhwolf.com/howtos/PowerShell-and-Zip-Files/

Prerequirements:
- Credentials from publish profiles Azure Web Apps
- PowerShell version higher 5.0 (use $PSVersionTable.PSVersion to show current PS version)
#> 

Param(
	[parameter(Mandatory = $true)]
	$AppName,
	[parameter(Mandatory = $true)]
	$BackendPath,
    $ResourceGroupName,
    $SubscriptionID
)

# FILE TO GET PLATFORM VERSION FROM
$PlatformVersionFile = $BackendPath+"\platform\bin\VirtoCommerce.Platform.Web.dll"

# FILE WITH MODULES VERSIONS TO ADD TO ARCHIVE
$FileToAddPath = $BackendPath + '\platform\versions.txt'

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID


$DestResourceGroupName = $ResourceGroupName
$DestWebAppName = $AppName

# Getting Publish Profile
Write-Output "Getting publishing profile for $DestWebAppName app"
$tmpPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $DestWebAppName `
           -ResourceGroupName $DestResourceGroupName `
           -OutputFile $tmpPublishProfile -Format WebDeploy -ErrorAction Stop

# DOWNLOAD WEBSITE CODE
Write-Output "DOWNLOAD WEBSITE CODE"
$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"
$sourcewebapp_msdeployUrl = "https://${AppName}.scm.azurewebsites.net/msdeploy.axd?site=${AppName}"
& $msdeploy -verb:sync -source:contentPath="D:\home\site\wwwroot\",computerName=$sourcewebapp_msdeployUrl,publishSettings=$tmpPublishProfile -dest:contentPath=$BackendPath

#Removing App_Data
Remove-Item $BackendPath\\platform\\App_Data -Recurse -Force -ErrorAction Continue