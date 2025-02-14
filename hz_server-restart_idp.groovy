pipeline {
    agent { label 'cm-linux' }
    stages {
        stage('Server-Restart') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    def userName = ""
                    def password = ""
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]

                    // Clone the repository
                    git branch: "main", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git"

                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: "master", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/sprintnet/jq.git"
                        
                        // Debugging: Check if jq exists
                        sh "ls -l"

                        sh "chmod +x jq"
                    }

                    // Move jq to the main workspace
                    sh "mv jqdir/jq ."

                    // Debug: List contents of the folder
                    sh "ls -l ${WORKSPACE}/${foldername}/"

                    dir("${WORKSPACE}/${foldername}/") {
                        if (fileExists("Environments.groovy")) {
                            sh "set +x 2>/dev/null"
                            sh "cat Environments.groovy > env.txt"
                            envList = readFile 'env.txt'
                        } else {
                            error "Error: Environments.groovy file not found!"
                        }
                    }

                    properties([
                        parameters([
                            [$class: 'ChoiceParameter',
                                choiceType: 'PT_SINGLE_SELECT',
                                filterLength: 1,
                                filterable: false,
                                name: 'Environment',
                                script: [
                                    $class: 'GroovyScript',
                                    fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                    script: [classpath: [], sandbox: true, script: "${envList}"]
                                ]
                            ],
                            [$class: 'ChoiceParameter',
                                choiceType: 'PT_SINGLE_SELECT',
                                filterLength: 1,
                                filterable: false,
                                name: 'Action',
                                script: [
                                    $class: 'GroovyScript',
                                    fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                    script: [classpath: [], sandbox: true, script: "return ['start', 'stop', 'restart']"]
                                ]
                            ],
                            [$class: 'ChoiceParameter',
                                choiceType: 'PT_SINGLE_SELECT',
                                filterLength: 1,
                                filterable: false,
                                name: 'Mancenter',
                                script: [
                                    $class: 'GroovyScript',
                                    fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                    script: [classpath: [], sandbox: true, script: "return ['false', 'true']"]
                                ]
                            ],
                            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter Server Name', name: 'Host_Name'],
                            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter CR Number for Production Deployment', name: 'cr_number']
                        ])
                    ])

                    deployments = load "${WORKSPACE}/deployment.groovy"
                    templateId = "84533"
                    jqcli = "${WORKSPACE}/jq"
                    containers = "container01"
                    file = "Cluster.json"
                    clusterSafeUrl = "clusterSafeUrl.json"

                    environment = "${params.Environment}"
                    
                    if (!fileExists(file)) {
                        error "Error! ${file} not found. Please check the repository."
                    }
                    Clusters_List = sh(script: "set +x; cat ${file} | ${jqcli} -r . '${environment}'", returnStdout: true).trim()
                    
                    if (!Clusters_List?.trim()) {
                        error("Error! Cluster name could not be determined for environment: ${environment}")
                    }

                    Cluster_Safe_URL = sh(script: "set +x; cat ${clusterSafeUrl} | ${jqcli} -r . '${environment}'", returnStdout: true).trim()
                    Hostname = "${params.Host_Name}"

                    if (Hostname == "") {
                        error "Error! Host_Name is Empty. Please enter Host_Name value."
                    }

                    extravars = "{"hostname":"${params.Host_Name}", "clusterName": "${Clusters_List}", "containerName": "${containers}", "action": "${params.Action}", "cluster_safe_url": "${Cluster_Safe_URL}", "mancenter": "${params.Mancenter}"}"

                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            userInput = input(id: 'Input-username',
                                parameters: [
                                    [$class: 'StringParameterDefinition', defaultValue: '', description: 'Enter Username:', name: 'Username'],
                                    [$class: 'hudson.model.PasswordParameterDefinition', description: 'Enter Password:', name: 'Password']
                                ],
                                submitterParameter: 'approver')
                        }
                        userName = userInput['Username']
                        password = userInput['Password'].toString()
                        environment = "prod"
                        templateId = "28669"
                        cr_number = "${params.cr_number}"

                        if (cr_number == "") {
                            error "Error! CR Number is Empty for Production Environment."
                        }
                    }

                    println("Extra-Vars are: " + extravars)

                    def ansibleOutput = deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)

                    if (ansibleOutput.contains("skipping: no hosts matched") || ansibleOutput.contains("No hosts matched")) {
                        error "Error! Invalid Host_Name provided. Please check and retry."
                    }
                }
            }
        }
    }
}
