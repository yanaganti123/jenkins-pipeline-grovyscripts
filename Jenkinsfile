import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.StoredConfig;

node
{
 git "git@github.com:yanaganti123/jenkins-pipeline-grovyscripts.git"
}

def upstreamRepoUrl = "https://github.com/yanaganti123/jenkins-pipeline-grovyscripts"
Jenkins j = Jenkins.getInstance();
File workflowLibDir = new File(j.getRootPath().toString(), "workflow-libs")

println "cmd /c \"c:\\Program Files (x86)\\Git\\cmd\\git.exe\" pull upstream master".execute(null, workflowLibDir).text
println "cmd /c ls -la".execute(null, workflowLibDir).text
