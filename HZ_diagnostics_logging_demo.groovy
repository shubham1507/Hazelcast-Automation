//https://gemini.google.com/app/75da30c60909a5e7?is_sa=1&is_sa=1&android-min-version=301356232&ios-min-version=322.0&campaign_id=bkws&utm_source=sem&utm_source=google&utm_medium=paid-media&utm_medium=cpc&utm_campaign=bkws&utm_campaign=2024enIN_gemfeb&pt=9008&mt=8&ct=p-growth-sem-bkws&gad_source=1&gclid=Cj0KCQiA4-y8BhC3ARIsAHmjC_HxmBNwqJO0q8fqwCAqW9j9YZDu7dJP7e897gXI5UGFNs_zqzc9Qg4aAu9sEALw_wcB&gclsrc=aw.ds
pipeline {
    agent { label 'cm-linux' }
    stages {
        stage('HZ-diagnostic-logging') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    def userName
                    def password
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]

                    git branch: "main", credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.SBI/dtc-hazelcast/Hazelcast-Services.git"
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: "master", credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.SBI/sprintnet/jq.git"
                    }
                    sh "chmod +x ./jq"
                    sh "mv ./jq ../"

                    dir("${workspace}/${foldername}/") {
                        sh "{ set +x; } 2>/dev/null;"
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile 'env.txt'

                        properties([
                            parameters([
                                [$class: 'ChoiceParameter', choiceType: 'PT_SINGLE_SELECT', filterLength: 1, filterable: false, name: 'Environment', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], sandbox: true, script: "return['Could not get The environments']"], script: [classpath: [], sandbox: true, script: "${envList}"]]],
                                [$class: 'ChoiceParameter', choiceType: 'PT_SINGLE_SELECT', filterLength: 1, filterable: false, name: 'Logging_type', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], sandbox: true, script: "return [ 'Could not get loggign option']"], script: [classpath: [], sandbox: true, script: '''return ["jdk", "log4j2"]''']]],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter Server Name', name: 'HostName'],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter sourcePath', name: 'sourcePath'],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter destPath', name: 'destPath'],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter artifactId', name: 'artifactId'],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter artifact name which want to deploy else keep empty for config file deployment', name: 'artifactName'],
                                [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter CR Number for Production Deployment', name: 'cr_number']
                            ])
                        ])

                        def templateId = "110777"
                        def containers = "container01"
                        def jqcli = "${workspace}/jq"
                        def file = "Cluster.json"
                        def clusterSafeUrl = "clusterSafeUrl.json"
                        def clusterNameFile = "ClusterName.json"
                        def deployments = load "${workspace}/deployment.groovy"
                        def environment = "${params.Environment}"
                        def loggingType = "${params.Logging_type}"
                        def Hostname = "${params.HostName}"
                        def artifactName = "${params.artifactName}"

                        def Clusters_List = sh(script: """{ set +x; } 2>/dev/null; cat ${file} | ${jqcli} -r .'${environment}'""", returnStdout: true).trim()
                        def Cluster_Safe_URL = sh(script: """{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqcli} -r .'${environment}'""", returnStdout: true).trim()

                        if (Hostname == "") {
                            println "Error! Host_Name is Empty. Please enter Host_Name value."
                            sh "{ set +x; } 2>/dev/null; exit 1"
                        }

                        // Fetch log4j JARs (using Maven - recommended)
                        withMaven {
                            sh "mvn dependency:get -DgroupId=org.apache.logging.log4j -DartifactId=log4j-api -Dversion=2.17.2 -Ddest=/opt/HZ/commonlib" // Replace with your desired version
                            sh "mvn dependency:get -DgroupId=org.apache.logging.log4j -DartifactId=log4j-core -Dversion=2.17.2 -Ddest=/opt/HZ/commonlib" // Replace with your desired version
                        }

                        sh "mkdir -p /opt/HZ/commonlib" // Create the commonlib directory if it doesn't exist
                        sh "chmod 755 /opt/HZ/commonlib/log4j-api-*.jar /opt/HZ/commonlib/log4j-core-*.jar"

                        def extravars = [
                            hostname: Hostname,
                            artifactId: params.artifactId,
                            cluster: Clusters_List,
                            container: containers,
                            env: environment,
                            cluster_safe_url: Cluster_Safe_URL,
                            Logging_type: loggingType,
                            sourcePath: params.sourcePath,
                            destPath: params.destPath,
                            artifactName: artifactName
                        ]

                        if (environment.toLowerCase().startsWith("prod")) {
                            timeout(time: 120, unit: 'SECONDS') {
                                def userInput = input(id: 'Input-username', parameters: [[$class: 'StringParameterDefinition', defaultValue: '', description: 'Enter Username:', name: 'Username'], [$class: 'hudson.model.PasswordParameterDefinition', description: 'Enter Password:', name: 'Password']], submitterParameter: 'approver')
                                userName = userInput['Username']
                                password = userInput['Password'].toString()
                                templateId = "64468"
                                def env = "prod"
                                def cr_number = "${params.cr_number}"

                                if (cr_number == "") {
                                    println "Error! CR Number is Empty for Production Environment."
                                    sh "{ set +x; } 2>/dev/null; exit 1"
                                }
                                extravars.cr_number = cr_number
                            }
                        }

                        def extravarsJson = groovy.json.JsonOutput.toJson(extravars)
                        println("Extra-Vars are: " + extravarsJson)

                        deployments.getApproval("Approve: To update config files into cluster", "HZ-approvers")
                        deployments.triggerAnsibleTower(templateId, environment, extravarsJson, userName, password)
                    }
                }
            }
        }
    }
}
