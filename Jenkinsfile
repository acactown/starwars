stage 'Compile And Tests'

def gradle;

node {

        // get source code
        checkout scm

        gradle = load 'jenkins/gradle.groovy'

        // check that the whole project compiles
        //gradle 'clean compileJava'
        gradle.cleanAndCompile()

        // save source code so we don't need to get it every time and also avoids conflicts
        stash excludes: 'build/', includes: '**', name: 'source'

        // execute required tests for commit stage in parallel
        parallel (
             "unit tests" : {
                gradle.test()
             },
             "commit integration tests" : {
                gradle.test('integration-test', '-PhappyPath')
             }
           )

        // save coverage reports for being processed during code quality phase.
        stash includes: 'build/jacoco/*.exec', name: 'unitCodeCoverage'
        stash includes: 'integration-test/build/jacoco/*.exec', name: 'commitIntegrationCodeCoverage'

        // publish JUnit results to Jenkins
        step([$class: 'JUnitResultArchiver', testResults: '**/build/test-results/*.xml'])
}

stage 'Code Quality'

node {

   parallel (
        'pmd' : {
            // static code analysis
            unstash 'source'

            gradle.codeQuality()
            step([$class: 'PmdPublisher', pattern: 'build/reports/pmd/*.xml'])
        },
        'jacoco': {
            // jacoco report rendering
            unstash 'source'
            unstash 'unitCodeCoverage'
            unstash 'commitIntegrationCodeCoverage'

            gradle.aggregateJaCoCoReports()
            publishHTML(target: [reportDir:'build/reports/jacoco/jacocoRootTestReport/html', reportFiles: 'index.html', reportName: 'Code Coverage'])
        }
      )
}

stage 'Integration Tests'

stage 'Assemble Binaries'

def dockerImages
def starwarsImage

def tagVersion = "latest"
def planetsImageName;

node {

    dockerImages = load 'jenkins/docker.groovy'

    // unstash source code and assemble the application
    // with BUILD_NUMBER
    unstash 'source'
    withEnv(["SOURCE_BUILD_NUMBER=${env.BUILD_NUMBER}"]) {
        gradle.assembleApplication()
    }

    // find created war, read the manifest to take the version

    def warFiles = findFiles(glob: 'build/libs/*.war')

    if (warFiles.length == 1) {
        def man = readManifest file: "build/libs/${warFiles[0].name}"
        tagVersion = man.main['Implementation-Version'] != null ? man.main['Implementation-Version'] : 'latest'
    } else {
        echo "0 or More than one WAR file generated by build."
        currentBuild.result = "UNSTABLE"
    }

    // load configuration for knowing the name of the image
    def content = readFile('gradle/config.groovy')
    def configuration = gradle.conf(content)

    // purge old docker images
    dockerImages.purge("${configuration['common.docker.organization']}/${configuration['common.docker.image']}", 2)

    planetsImageName = "${configuration['common.docker.organization']}/${configuration['common.docker.image']}:${tagVersion}"

    // create docker image with version
    starwarsImage = docker.build planetsImageName

    // runs container tests to be sure that the image is correctly created and it works
    withEnv(["starwars_planets=${planetsImageName}"]) {
        //gradle.test('container-test')
        //step([$class: 'JUnitResultArchiver', testResults: 'container-test/build/test-results/*.xml'])
    }

}


stage name: 'Publish Binaries', concurrency: 1

node {
    unstash 'source'
    // Moves WAR to artifact repostitory
    //gradle.publishApplication()

    //Docker push
}

input message: "Deploy Application to Local"

setCheckpoint('Before Deploying to Test')

stage name: 'Acceptance Stage in Local', concurrency: 1
node {
    unstash 'source'
    withEnv(["starwars_planets=${planetsImageName}"]) {
        try {
            gradle.run('startDockerCompose')

            //gradle.test('acceptance-test')
            //gradle.run(':acceptance-test:aggregate')
            //publishHTML(target: [reportDir:'acceptance-test/target/site/serenity', reportFiles: 'index.html', reportName: 'SerenityBDD report'])
            //step([$class: 'JUnitResultArchiver', testResults: 'acceptance-test/build/test-results/*.xml'])

            //gradle.test('stress-test')
            //publishHTML(target: [reportDir:'stress-test/build/reports/gatling-results/averageorbitalperiodsimulation-*', reportFiles: 'index.html', reportName: 'Gatling report'])

            //We need to get all pact files of consumers that has some connection with planets
            def pact = load('jenkins/pact.groovy')
            pact.withRequiredPacts {
                gradle.test('producer-test')
            }

        } finally {
            gradle.run('removeDockerCompose')
        }
    }
}

void setCheckpoint(String message) {
    try {
        checkpoint(message)
    } catch (NoSuchMethodError _) {
        echo 'Checkpoint feature available in CloudBees Jenkins Enterprise.'
    }
}

void gradle(String tasks, String switches = null) {
    String gradleCommand = "";
    gradleCommand += './gradlew '
    gradleCommand += tasks

    if(switches != null) {
        gradleCommand += ' '
        gradleCommand += switches
    }

    sh gradleCommand.toString()
}