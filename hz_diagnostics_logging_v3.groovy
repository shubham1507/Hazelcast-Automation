pipeline {
    agent { label 'cm-linux' }
    stages {
        stage('HZ-configfile-deployment') {
            steps {
                script {
                    cleanWs()
                    deleteDir()

                    def folderParts = pwd().split("/")
                    def folderName = folderParts[folderParts.length - 2]

                    git branch: "main", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/dtc-hazelcast/Hazelcast-Services.git"

                    sh "mkdir -p jqdir"
                    dir('jqdir') {
                        git branch: "master", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/sprintnet/jq.git"
                        sh "chmod +x ./jq"
                        sh "mv ./jq ../"
                    }

                    dir("${WORKSPACE}/${folderName}/") {
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile('env.txt')

                        properties([
                            parameters([
                                choice(name: 'Environment', choices: envList, description: 'Select Environment'),
                                string(name: 'HostName', defaultValue: '', description: 'Enter Server Name'),
                                string(name: 'artifactId', defaultValue: '', description: 'Enter artifact name to deploy (leave empty for all config files)'),
                                string(name: 'cr_number', defaultValue: '', description: 'Enter CR Number for Production Deployment'),
                                choice(name: 'LoggingType', choices: ['log4j2', 'jdk'], description: 'Select Logging Type')
                            ])
                        ])
                    }

                    def templateId = "110777"
                    def containers = "container01"
                    def jqCli = "${WORKSPACE}/jq"
                    def file = "Cluster.json"
                    def clusterSafeUrl = "clusterSafeUrl.json"
                    def clusterNameFile = "ClusterName.json"
                    def deployments = load "${WORKSPACE}/deployment.groovy"

                    def environment = params.Environment
                    def hostname = params.HostName
                    def loggingType = params.LoggingType

                    if (hostname.trim() == "") {
                        error "Error! Host_Name is Empty. Please enter a valid Host_Name value."
                    }

                    def clustersList = sh(script: "cat ${file} | ${jqCli} -r .'${environment}'", returnStdout: true).trim()
                    def clusterSafeURL = sh(script: "cat ${clusterSafeUrl} | ${jqCli} -r .'${environment}'", returnStdout: true).trim()
                    def clusterName = sh(script: "cat ${clusterNameFile} | ${jqCli} -r .'${environment}'", returnStdout: true).trim()

                    def extravars = """
                    {
                        "hostname": "${hostname}",
                        "artifactId": "${params.artifactId}",
                        "cluster": "${clustersList}",
                        "container": "${containers}",
                        "env": "${environment}",
                        "cluster_safe_url": "${clusterSafeURL}",
                        "foldername": "${folderName}",
                        "logging_type": "${loggingType}"
                    }
                    """

                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(
                                id: 'Input-username',
                                parameters: [
                                    string(name: 'Username', description: 'Enter Username:'),
                                    password(name: 'Password', description: 'Enter Password:')
                                ],
                                submitterParameter: 'approver'
                            )

                            def userName = userInput['Username']
                            def password = userInput['Password'].toString()
                            def crNumber = params.cr_number
                            if (crNumber.trim() == "") {
                                error "Error! CR Number is Empty for Production Environment."
                            }

                            extravars = """
                            {
                                "cr_number": "${crNumber}",
                                "hostname": "${hostname}",
                                "artifactId": "${params.artifactId}",
                                "cluster": "${clustersList}",
                                "container": "${containers}",
                                "env": "${environment}",
                                "cluster_safe_url": "${clusterSafeURL}",
                                "foldername": "${folderName}",
                                "logging_type": "${loggingType}"
                            }
                            """
                            templateId = "64468"
                        }
                    }

                    println("Extra Vars: ${extravars}")
                    deployments.getApproval("Approve: To execute cluster deployment", "HZ-approvers")
                    deployments.triggerAnsibleTower(templateId, environment, extravars)

                    build job: '/sbi-11385924-wsit-platform/CAAS/Hazelcast/Hazelcast-Services/HZ-TestSuite-AP', parameters: [
                        string(name: 'Cluster_Safe_URL', value: clusterSafeURL),
                        string(name: 'environment', value: clusterName)
                    ]
                }
            }
        }
    }
}
