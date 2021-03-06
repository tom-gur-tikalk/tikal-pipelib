package tpl.ci.maven

import tpl.ci.tplBaseCiPipeline
import tpl.services.Deployer

/**
 * Created by ccohen on 7/6/17.
 */
class tplMavenCiPipeline extends tplBaseCiPipeline {

    def server
    def rtMaven
    def buildInfo

    def artifactoryName
    def mavenName
    def snapshotRepo
    def releaseRepo
    def vSnapshotRepo
    def vReleaseRepo

    def gitRepoUrl
    def gitCredentialsId

    boolean dryRun = false
    boolean release = false
    String releaseVersion
    String developmentVersion

    def containerName
    def dockerRegisteryPrefix
    def containerTag
    def helmRepoURL
    def helmRepo
    def helmGitRepo
    def helmGitRepoBranch
    def helmCrendetialId
    def awsCrendetialId
    def kubeContext
    def dockerRegisteryUrl
    def dockerPath

    tplMavenCiPipeline(script) {
        super(script)

    }

    @Override
    void initParams(){
        containerName = script.params.containerName;
        dockerRegisteryPrefix = script.params.dockerRegisteryPrefix;
        containerTag = script.params.containerTag;
        dockerRegisteryUrl= (script.params.get('dockerRegisteryUrl') == null ) ? 'https://index.docker.io/v1' : script.params.get('dockerRegisteryUrl')
        dockerPath =  (script.params.get('dockerPath') == null ) ? '' : script.params.get('dockerPath')
        helmRepoURL = (script.params.get('helmRepoURL') == null ) ? 'Missing Helm Repo Url param' : script.params.get('helmRepoURL')
        helmRepo = (script.params.get('helmRepo') == null ) ? 'Missing Helm Repo param' : script.params.get('helmRepo')
        helmGitRepo = (script.params.get('helmGitRepo') == null ) ? 'Missing Helm Git Repo param' : script.params.get('helmGitRepo')
        helmGitRepoBranch = (script.params.get('helmGitRepoBranch') == null ) ? 'Missing Helm Git Repo branch param' : script.params.get('helmGitRepoBranch')
        helmCrendetialId = (script.params.get('helmCrendetialId') == null ) ? 'Missing Helm-Jenkins Credentials id' : script.params.get('helmCrendetialId')
        awsCrendetialId = (script.params.get('awsCrendetialId') == null ) ? 'Missing AWS-Jenkins Credentials id' : script.params.get('awsCrendetialId')
        kubeContext = (script.params.get('kubeContext') == null ) ? 'Missing kubeContext' : script.params.get('kubeContext')
    }

    @Override
    void runImpl() {
        try {
            runStage('Setup', this.&setup)
            runStage('Checkout', this.&checkout)
           // runStage('Test', this.&unitTests)
            runStage('Build', this.&build)
            runStage('Deploy', this.&deploy)
        } catch (e) {
            script.currentBuild.result = "FAILURE"
            throw e
        }
//        finally {
//            buildNotifier()
//        }

    }

    @Override
    void setup() {
        gitConfig()
        initParams()

        // automatically capture environment variables while downloading and uploading files
    }

    @Override
    void checkout() {
        script.checkout script.scm
    }

    @Override
    void unitTests() {
        rtMaven.run pom: 'pom.xml', goals: 'clean test'

        script.junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml, target/failsafe-reports/*.xml'
    }

    @Override
    void build() {
        script.dir("${script.env.WORKSPACE}") {
            script.withCredentials([script.usernamePassword(credentialsId: 'dockerHub', passwordVariable: 'DOCKER_REGISTRY_PASS', usernameVariable: 'DOCKER_REGISTRY_USER')]) {
                def mavenHome = script.tool 'MVN-3.5.0'
                script.withEnv(["SERVICE_TAG=${script.env.JOB_NAME}-${script.env.BUILD_NUMBER}"]) {
                    script.sh "${mavenHome}/bin/mvn clean install"
                }
            }

        }


    }
    @Override
    void deploy() {
        logger.info "Helm Deploy"
        def deployer = new Deployer(script,"${script.env.JOB_NAME}",containerTag,helmRepoURL,helmRepo,helmGitRepo,helmGitRepoBranch,helmCrendetialId,awsCrendetialId,kubeContext)
        deployer.deploy()
    }



    void gitConfig() {
   }

    void gitCommit() {

    }


    String computeReleaseVersion() {
        logger.info 'compute release version'

        script.pom = script.readMavenPom file: 'pom.xml'
        String version = script.pom.version
        try {
            if (version.endsWith('-SNAPSHOT')) {
                version = version.substring(0, version.length() - "SNAPSHOT".length() - 1)
            } else {
                script.error 'failed to compute release version'
            }
        } catch (e) {
            logger.error e.message
        }

        return version
    }

    String computeNextVersion() {
        String version = script.pom.version
        try {
//            DefaultVersionInfo dvi = new DefaultVersionInfo(version)
//            version = dvi.getNextVersion().getSnapshotVersionString()


            logger.info 'next development version: ' version
        } catch (e) {
            logger.info e.message
        }

        return version
    }

    void computeScmTag(String tag) {
        // set scm/tag to '[name]-[version]'
        script.pom.scm.tag = tag
        script.writeMavenPom model: script.pom
    }

    void buildNotifier() {

        def subject = script.env.JOB_NAME + ' - Build #' + script.currentBuild.number + ' - ' + script.currentBuild.currentResult
        script.emailext(
                to: 'user@domain.com',
                subject: subject,
                body: script.env.BUILD_URL
//                attachLog: true,
                //recipientProviders: [[$class: 'DevelopersRecipientProvider']]
        )
    }
}
