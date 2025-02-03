pipeline {
    agent {
        label 'cm-linux'
    }
    stages {
        stage('HZ-logging-configuration') {
            steps {
                script {
                    cleanWs()
                    def userName = ""
                    def password = ""
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]
                    
                    git branch: "main", 
                        credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH", 
                        url: "https://alm-github.systems.uk.SBI/CMB-Platform/Hazelcast-Services.git"
                    
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: "master", 
                            credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH", 
                            url: "https://alm-github.systems.uk.SBI/sprintnet/jq.git"
                        
                        sh """
                            chmod +x ./jq
                            mv ./jq ../
                        """
                    }
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh """
                            { set +x; } 2>/dev/null;
                            cat Environments.groovy >> env.txt
                        """
                        envList = readFile 'env.txt'
                        properties([
                            parameters([
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Environment',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['Could not get the environments']"
                                        ],
                                        classpath: [],
                                        sandbox: true,
                                        script: "${envList}"
                                    ]
                                ],
                                [$class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter Server Name',
                                    name: 'HostName'
                                ],
                                [$class: 'ChoiceParameterDefinition',
                                    choices: ['log4j2', 'jdk'],
                                    description: 'Select logging implementation',
                                    name: 'LoggingType'
                                ],
                                [$class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter CR Number for Production Deployment',
                                    name: 'cr_number'
                                ]
                            ])
                        ])
                    }
                    
                    def deployments = load "${WORKSPACE}/deployment.groovy"
                    def environment = "${params.Environment}"
                    def Clusters_List = sh(script: """{ set +x; } 2>/dev/null; cat Cluster.json | jq -r .${environment}""", returnStdout: true).trim()
                    def Cluster_Safe_URL = sh(script: """{ set +x; } 2>/dev/null; cat clusterSafeUrl.json | jq -r .${environment}""", returnStdout: true).trim()
                    def Hostname = "${params.HostName}"
                    def LoggingType = "${params.LoggingType}"

                    if (Hostname == "") {
                        println "Error! Host_Name is Empty. Please enter Host_Name value."
                        sh "{ set +x; } 2>/dev/null; exit 1"
                    }
                    
                    // Template ID for logging configuration
                    def templateId = "110777"
                    def containers = "container01"
                    def extravars = """{
                        "hostname": "${Hostname}",
                        "cluster": "${Clusters_List}",
                        "container": "${containers}",
                        "env": "${environment}",
                        "cluster_safe_url": "${Cluster_Safe_URL}",
                        "foldername": "${foldername}",
                        "logging_type": "${LoggingType}",
                        "hz_root": "/opt/HZ"
                    }"""

                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(
                                id: 'Input-username',
                                parameters: [
                                    [$class: 'StringParameterDefinition', defaultValue: '', description: 'Enter Username:', name: 'Username'],
                                    [$class: 'hudson.model.PasswordParameterDefinition', description: 'Enter Password:', name: 'Password']
                                ],
                                submitterParameter: 'approver'
                            )
                            userName = userInput['Username']
                            password = userInput['Password'].toString()
                            templateId = "64468"
                            environment = "prod"
                            def cr_number = "${params.cr_number}"
                            if (cr_number == "") {
                                println "Error! CR Number is Empty for Production Environment."
                                sh "{ set +x; } 2>/dev/null; exit 1"
                            }
                            extravars = """{
                                "cr_number": "${cr_number}",
                                "hostname": "${Hostname}",
                                "cluster": "${Clusters_List}",
                                "container": "${containers}",
                                "env": "${environment}",
                                "cluster_safe_url": "${Cluster_Safe_URL}",
                                "foldername": "${foldername}",
                                "logging_type": "${LoggingType}",
                                "hz_root": "/opt/HZ"
                            }"""
                        }
                    }

                    println("Extra-Vars are: " + extravars)
                    deployments.getApproval("Approve: To configure logging for ${LoggingType}", "HZ-approvers")
                    
                    // Execute pre-logging setup
                    if (LoggingType == "log4j2") {
                        sh """
                            ssh ${Hostname} "mkdir -p /opt/HZ/commonlib"
                            ssh ${Hostname} "cd /opt/HZ/commonlib && wget https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.20.0/log4j-api-2.20.0.jar -O log4j-api.jar"
                            ssh ${Hostname} "cd /opt/HZ/commonlib && wget https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.20.0/log4j-core-2.20.0.jar -O log4j-core.jar"
                            ssh ${Hostname} "chmod 755 /opt/HZ/commonlib/log4j-api.jar /opt/HZ/commonlib/log4j-core.jar"
                        """
                    }
                    
                    deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)
                    
                    // Restart containers after configuration
                    sh """
                        ssh ${Hostname} "/opt/HZ/hazelcast/bin/stopContainer.sh -cn ${Clusters_List} -cp container01"
                        sleep 10
                        ssh ${Hostname} "/opt/HZ/hazelcast/bin/startContainer.sh -cn ${Clusters_List} -cp container01"
                    """
                }
            }
        }
    }
}
