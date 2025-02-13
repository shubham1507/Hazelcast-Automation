pipeline {
    agent { label 'cm-linux' }
    
    stages {
        stage('Server-Restart') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    def userName = ''
                    def password = ''
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]
                    
                    // Clone Hazelcast Services repository
                    git branch: "main", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/dtc-hazelcast/Hazelcast-Services.git"
                    
                    sh "mkdir jqdir"
                    
                    dir('jqdir') {
                        // Clone jq repository
                        git branch: "master", credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.sbi/sprintnet/jq.git"
                        sh """
                            chmod +x ./jq
                            mv ./jq ../
                        """
                    }
                    
                    dir("${WORKSPACE}/${foldername}") {
                        sh """
                            { set +x; } 2>/dev/null;
                            cat Environments.groovy >> env.txt
                        """
                        
                        def envList = readFile 'env.txt'
                        
                        // Defining user input parameters
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
                                        script: "${envList}"
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
                                        fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                        script: "return ['false', 'true']"
                                    ]
                                ],
                                [$class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter Server Name',
                                    name: 'Host_Name'
                                ],
                                [$class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter CR Number for Production Deployment',
                                    name: 'cr_number'
                                ]
                            ])
                        ])
                        
                        def deployments = load "${WORKSPACE}/deployment.groovy"
                        def templateId = "84533"
                        def containers = "container01"
                        def jqCli = "${WORKSPACE}/jq"
                        def file = "Cluster.json"
                        def clusterSafeUrl = "clusterSafeUrl.json"
                        
                        // Fetch Cluster details from JSON files using jq
                        def Clusters_List = sh(script: "cat ${file} | ${jqCli} -r .'${params.Environment}'", returnStdout: true).trim()
                        def Cluster_Safe_URL = sh(script: "cat ${clusterSafeUrl} | ${jqCli} -r .'${params.Environment}'", returnStdout: true).trim()
                        
                        def Hostname = "${params.Host_Name}".trim()

                        // **Fix 1: Validate if Hostname is empty**
                        if (Hostname == "") {
                            error("Error! Host_Name is Empty. Please enter Host_Name value.")
                        }

                        // **Fix 2: Validate if the Hostname is reachable**
                        def hostValidation = sh(script: "ping -c 2 ${Hostname}", returnStatus: true)
                        if (hostValidation != 0) {
                            error("Error! Host '${Hostname}' is unreachable. Please check the Host_Name value.")
                        }

                        def extravars = """
                            {
                                "hostname": "${params.Host_Name}",
                                "clusterName": "${Clusters_List}",
                                "containerName": "${containers}",
                                "action": "${params.Action}",
                                "cluster_safe_url": "${Cluster_Safe_URL}",
                                "mancenter": "${params.Mancenter}"
                            }
                        """
                        
                        // **Production Environment Handling**
                        if (params.Environment.toLowerCase().startsWith("prod")) {
                            timeout(time: 120, unit: 'SECONDS') {
                                def userInput = input(id: 'Input-username',
                                    parameters: [
                                        [$class: 'StringParameterDefinition', defaultValue: '', description: 'Enter Username:', name: 'Username'],
                                        [$class: 'hudson.model.PasswordParameterDefinition', description: 'Enter Password:', name: 'Password']
                                    ],
                                    submitterParameter: 'approver'
                                )
                                
                                userName = userInput['Username']
                                password = userInput['Password'].toString()
                                templateId = "28669"
                                
                                // **Fix 3: Validate CR Number for Production**
                                if (params.cr_number == "") {
                                    error("Error! CR Number is Empty for Production Environment.")
                                }
                                
                                extravars = """
                                    {
                                        "cr_number": "${params.cr_number}",
                                        "hostname": "${params.Host_Name}",
                                        "clusterName": "${Clusters_List}",
                                        "containerName": "${containers}",
                                        "action": "${params.Action}",
                                        "cluster_safe_url": "${Cluster_Safe_URL}",
                                        "mancenter": "${params.Mancenter}"
                                    }
                                """
                            }
                        }
                        
                        println("Extra-Vars are: " + extravars)

                        // **Fix 4: Capture Ansible Execution Result**
                        def ansibleExecutionResult = deployments.triggerAnsibleTower(templateId, params.Environment, extravars, userName, password)

                        // **Fix 5: Validate if Ansible Execution was Successful**
                        if (ansibleExecutionResult != "Success") {
                            error("Ansible execution failed. Please check logs for details.")
                        }

                        println("Server action '${params.Action}' executed successfully on ${params.Host_Name}")
                    }
                }
            }
        }
    }
}
