#
# This script will post message to twitter account
#

Param(  
  	[parameter(Mandatory=$true)]
        $apiurl
     )

$sampleDataStateUrl = "$apiurl/api/platform/sampledata/state"
$sampleDataImportUrl = "$apiurl/api/platform/sampledata/autoinstall"
          
# Initiate sample data installation
$sampleDataImportResult = Invoke-RestMethod $sampleDataImportUrl -Method Post -ErrorAction Stop
Write-Output "Sample data import result: $sampleDataImportResult"

# Wait until sample data have been imported
Write-Output "Waiting for sample data import is completed"
do
{
    try
    {
        Start-Sleep -s 5
        $sampleDataState = Invoke-RestMethod $sampleDataStateUrl -ErrorAction Stop
        Write-Output "Sample data state: $sampleDataState"
    }
    catch
    {
        $message = $_.Exception.Message
        Write-Output "Error: $message"
    }
}
while ($sampleDataState -ne "completed")