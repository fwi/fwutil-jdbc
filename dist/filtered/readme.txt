This package contains the compiled (Java 8) code from ${project.artifactId} (${project.url}).

The Maven install script (file "mvninstall") can be used to install the artifacts locally.

The Maven deploy-script (file "mvndeploy") needs to be modified before it can be used to deploy the artifacts. 
Search for "-Durl" and point it to the Maven (shared) repository.
If credentials are required to deploy, use the "repositoryId" option.
See for more info https://maven.apache.org/plugins/maven-deploy-plugin/deploy-file-mojo.html
