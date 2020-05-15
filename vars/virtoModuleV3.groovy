def Modules
def Packaging
def Utilities
def GithubRelease

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    def UNSTABLE_CAUSES = []

    node {
        properties([disableConcurrentBuilds()])

        def globalLib = library('global-shared-lib').com.test
		Utilities = globalLib.Utilities
		Packaging = globalLib.Packaging
		Modules = globalLib.Modules
        GithubRelease = globalLib.GithubRelease

        def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "S:\\Buildsv3\\${repoName}\\${escapedBranch}"
        def releaseNotesPath = "${env.WORKSPACE}\\release_notes.txt"
        projectType = 'NETCORE2'
        dir(workspace){
            // def SETTINGS
            // def settingsFileContent
            // configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
            //     settingsFileContent = readFile(SETTINGS_FILE)
            // }
            // SETTINGS = globalLib.Settings.new(settingsFileContent)
            // SETTINGS.setProject('platform-core')
            // SETTINGS.setBranch(env.BRANCH_NAME)
            try {
                stage('Checkout'){
                    deleteDir()
                    checkout scm

                    try
                    {
                        def release = GithubRelease.getLatestGithubReleaseRegexp(this, Utilities.getOrgName(this), Utilities.getRepoName(this), /\d\.\d\.\d[\s]{0,1}[\w]*/, true)
                        echo release.published_at
                        def releaseNotes = Utilities.getReleaseNotesFromCommits(this, release.published_at)
                        echo releaseNotes
                        writeFile file: releaseNotesPath, text: releaseNotes
                    }
                    catch(any)
                    {
                        echo "exception:"
                        echo any.getMessage()
                    }
                }

                stage('Build'){
                    if(Utilities.isPullRequest(this))
                    {
                        withSonarQubeEnv('VC Sonar Server'){
                            withEnv(["BRANCH_NAME=${env.CHANGE_BRANCH}"])
                            {
                                powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -PullRequest -GitHubToken ${env.GITHUB_TOKEN} -skip Restore+Compile"
                                powershell "vc-build Compile"
                            }
                        }
                    }
                    else
                    {
                        withSonarQubeEnv('VC Sonar Server'){
                            powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"
                        }
                        powershell "vc-build Compile"
                    }
                }

                stage('Unit Tests'){
                    powershell "vc-build Test -skip Restore+Compile"
                } 

                stage('Quality Gate'){
                    sleep time: 15
                    // withSonarQubeEnv('VC Sonar Server'){
                    //     powershell "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
                    // }
                    Packaging.endAnalyzer(this)
                    Packaging.checkAnalyzerGate(this)
                }
                 

                stage('Packaging'){                
                    powershell "vc-build Compress -skip Clean+Restore+Compile+Test"
                }

                if(env.BRANCH_NAME == 'feature/migrate-to-vc30')
                {
                    def artifacts = findFiles(glob: 'artifacts\\*.zip')
                    def artifactFileName = artifacts[0].path.split("\\\\").last()
                    def moduleId = artifactFileName.split("_").first()
                    echo "Module id: ${moduleId}"
                    Packaging.saveArtifact(this, 'vc', 'module', moduleId, artifacts[0].path)
                }

                if(env.BRANCH_NAME == 'dev-3.0.0')
                {
                    stage('Publish')
                    {
                        def artifacts = findFiles(glob: 'artifacts\\*.zip')
                        def artifactFileName = artifacts[0].path.split("\\\\").last()
                        def moduleId = artifactFileName.split("_").first()
                        echo "Module id: ${moduleId}"
						Packaging.saveArtifact(this, 'vc', 'module', moduleId, artifacts[0].path)

                        def gitversionOutput = powershell (script: "dotnet gitversion", returnStdout: true, label: 'Gitversion', encoding: 'UTF-8').trim()
                        def gitversionJson = new groovy.json.JsonSlurperClassic().parseText(gitversionOutput)
                        def commitNumber = gitversionJson['CommitsSinceVersionSource']
                        def moduleArtifactName = "${moduleId}_3.0.0-build.${commitNumber}"
                        echo "artifact version: ${moduleArtifactName}"
                        def artifactPath = "${workspace}\\artifacts\\${moduleArtifactName}.zip"
                        powershell "Copy-Item ${artifacts[0].path} -Destination ${artifactPath}"
                        powershell script: "${env.Utils}\\AzCopy10\\AzCopy.exe copy \"${artifactPath}\" \"https://vc3prerelease.blob.core.windows.net/packages${env.ARTIFACTS_BLOB_TOKEN}\"", label: "AzCopy"

                        def packageUri = "https://vc3prerelease.blob.core.windows.net/packages/${moduleArtifactName}.zip"
                        def manifestResult = powershell script: "vc-build PublishModuleManifest -CustomModulePackageUri \"${packageUri}\" -ModulesJsonName \"modules_v3_prerelease.json\"", returnStatus: true
                        if(manifestResult == 423)
                        {
                            UNSTABLE_CAUSES.add("Module Manifest: nothing to commit, working tree clean")
                        }
                        else if(manifestResult != 0)
                        {
                            throw new Exception("Module Manifest: returned nonzero exit code")
                        }
                    }
                }

                if(!Utilities.isPullRequest(this) && env.BRANCH_NAME == 'release/3.0.0')
                {
                    stage('Publish')
                    {
						def artifacts = findFiles(glob: 'artifacts\\*.zip')
                        def artifactFileName = artifacts[0].path.split("\\\\").last()
                        def moduleId = artifactFileName.split("_").first()
                        echo "Module id: ${moduleId}"
						Packaging.saveArtifact(this, 'vc', 'module', moduleId, artifacts[0].path)
                        
                        def ghReleaseResult = powershell script: "vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test", returnStatus: true
                        if(ghReleaseResult == 409)
                        {
                            UNSTABLE_CAUSES.add("Nuget package already exists.")
                        } 
                        else if(ghReleaseResult != 0)
                        {
                            throw new Exception("ERROR: script returned ${ghReleaseResult}")
                        }
                        
                        def orgName = Utilities.getOrgName(this)
                        def releaseNotesFile = new File(releaseNotesPath)
                        def releaseNotesArg = releaseNotesFile.exists() ? "" : "-ReleaseNotes ${releaseNotesPath}"
                        def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} ${releaseNotesArg} -PreRelease -skip Clean+Restore+Compile+Test", returnStatus: true
                        if(releaseResult == 422){
                            UNSTABLE_CAUSES.add("Release already exists on github")
                        } else if(releaseResult !=0 ) {
                            throw new Exception("Github release error")
                        }

                        def manifestResult = powershell script: "vc-build PublishModuleManifest", returnStatus: true
                        if(manifestResult == 423)
                        {
                            UNSTABLE_CAUSES.add("Module Manifest: nothing to commit, working tree clean")
                        }
                        else if(manifestResult != 0)
                        {
                            throw new Exception("Module Manifest: returned nonzero exit code")
                        }
                    }

                    // stage('Deploy'){
                    //     def moduleId = Modules.getModuleId(this)
                    //     def artifacts = findFiles(glob: "artifacts/*.zip")
                    //     def artifactPath = artifacts[0].path
                    //     def dstContentPath = "modules\\${moduleId}"
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtDev')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtQa')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtDemo')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     //Utilities.runSharedPS(this, "v3\\Restart-WebApp.ps1", "-WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']}")
                    // }
                }
            }
            catch (any) {
                currentBuild.result = 'FAILURE'
                throw any
            }
            finally{
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
                Utilities.cleanPRFolder(this)
            }
        }
    }
}