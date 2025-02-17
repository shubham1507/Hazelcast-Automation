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

                    // Cloning repositories
                    git branch: "main", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git"
                    
                    sh("mkdir jqdir")
                    
                    dir('jqdir') {
                        git branch: "master", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/sprintnet/jq.git"
                        
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
                    }

                    def envList = readFile('env.txt').split('\n').collect { it.trim() }

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
                                    script: """
                                        def environments = ${envList}
                                        return environments
                                    """
                                ]
                            ],
                            [$class: 'ChoiceParameter',
                                choiceType: 'PT_SINGLE_SELECT',
                                filterLength: 1,
                                filterable: false,
                                name: 'Action',
                                script: [
                                    $class: 'GroovyScript',
                                    fallbackScript: [],
                                    classpath: [],
                                    sandbox: true,
                                    script: "return ['start', 'stop', 'restart']"
                                ]
                            ],
                            [$class: 'ChoiceParameter',
                                choiceType: 'PT_SINGLE_SELECT',
                                filterLength: 1,
                                filterable: false,
                                name: 'Mancenter',
                                script: [
                                    $class: 'GroovyScript',
                                    fallbackScript: [],
                                    classpath: [],
                                    sandbox: true,
                                    script: "return ['false', 'true']"
                                ]
                            ],
                            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter Server Name', name: 'Host_Name'],
                            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Please enter CR Number for Production Deployment', name: 'cr_number']
                        ])
                    ])

                    // Load deployment script
                    def deployments = load "${WORKSPACE}/deployment.groovy"

                    def templateId = "84533"
                    def jqcli = "${WORKSPACE}/jq"
                    def containers = "container01"
                    def file = "Cluster.json"
                    def clusterSafeUrl = "clusterSafeUrl.json"

                    def environment = "${params.Environment}"

                    def Clusters_List = sh(script: """{ set +x; } 2>/dev/null; cat ${file} | ${jqcli} -r . '${environment}' """, returnStdout: true).trim()
                    def Cluster_Safe_URL = sh(script: """{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqcli} -r . '${environment}' """, returnStdout: true).trim()

                    def Hostname = "${params.Host_Name}"
                    if (Hostname.trim() == "") {
                        error "Error! Host_Name is Empty. Please enter Host_Name."
                    }

                    def extravars = """{
                        "hostname": "${params.Host_Name}",
                        "clusterName": "${Clusters_List}",
                        "containerName": "${containers}",
                        "action": "${params.Action}",
                        "cluster_safe_url": "${Cluster_Safe_URL}",
                        "mancenter": "${params.Mancenter}"
                    }"""

                    // Handling production environment
                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(id: 'Input-username',
                                parameters: [
                                    string(name: 'Username', defaultValue: '', description: 'Enter Username'),
                                    password(name: 'Password', description: 'Enter Password')
                                ]
                            )
                            userName = userInput['Username']
                            password = userInput['Password'].toString()
                        }

                        environment = "prod"
                        templateId = "28669"

                        def cr_number = "${params.cr_number}"
                        if (cr_number.trim() == "") {
                            error "Error! CR Number is Empty for Production."
                        }

                        extravars = """{
                            "cr_number": "${params.cr_number}",
                            "hostname": "${params.Host_Name}",
                            "clusterName": "${Clusters_List}",
                            "containerName": "${containers}",
                            "action": "${params.Action}",
                            "cluster_safe_url": "${Cluster_Safe_URL}",
                            "mancenter": "${params.Mancenter}"
                        }"""
                    }

                    println("Extra-Vars: " + extravars)

                    // Triggering Ansible
                    def ansibleOutput = deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)

                    println "Ansible Output: ${ansibleOutput}"

                    if (ansibleOutput != null && ansibleOutput.contains("skipping: no hosts matched")) {
                        error "Error! No hosts matched in Ansible playbook."
                    }
                }
            }
        }
    }
}
