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
                    
                    git branch: 'main', credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', url: 'https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git'
                    
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: 'master', credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', url: 'https://alm-github.systems.uk.hsbc/sprintnet/jq.git'
                        sh 'chmod +x ./jq'
                    }
                    
                    sh 'mv ./jq ../'
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh "{ set +x; } 2>/dev/null;"
                        cat Environments.groovy >> env.txt
                        def envList = readFile 'env.txt'
                        
                        properties([
                            parameters([
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Environment',
                                    script: [$class: 'GroovyScript',
                                        fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                        script: envList,
                                        classpath: [],
                                        sandbox: true
                                    ]
                                ],
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Action',
                                    script: [$class: 'GroovyScript',
                                        fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                        script: "return ['start', 'stop', 'restart']",
                                        classpath: [],
                                        sandbox: true
                                    ]
                                ],
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Mancenter',
                                    script: [$class: 'GroovyScript',
                                        fallbackScript: [classpath: [], sandbox: true, script: "return ['Could not get The environments']"],
                                        script: "return ['false', 'true']",
                                        classpath: [],
                                        sandbox: true
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
                    }
                    
                    def deployments = load "${WORKSPACE}/deployment.groovy"
                    def templateId = '84533'
                    def jqCli = "${WORKSPACE}/jq"
                    def containers = 'container01'
                    def file = 'Cluster.json'
                    def clusterSafeUrl = 'clusterSafeUrl.json'
                    
                    def Clusters_List = sh(script: "{ set +x; } 2>/dev/null; cat ${file} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    def Cluster_Safe_URL = sh(script: "{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    
                    // Enhanced hostname validation
                    def Hostname = "${params.Host_Name}"
                    if (Hostname == "" || Hostname == null) {
                        error("Host_Name is empty or null. Please provide a valid hostname.")
                    }
                    
                    def environment = "${params.Environment}"
                    def extravars = "{\"hostname\":\"${params.Host_Name}\", \"clusterName\": \"${Clusters_List}\", \"containerName\": \"${containers}\", \"action\": \"${params.Action}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"mancenter\": \"${params.Mancenter}\"}"
                    
                    if (environment.toLowerCase().startsWith("prod")) {
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
                            environment = "prod"
                            templateId = "28669"
                            
                            def cr_number = "${params.cr_number}"
                            if (cr_number == "") {
                                error("CR Number is empty for Production Environment. Please provide a valid CR number.")
                            }
                            
                            extravars = "{\"cr_number\": \"${params.cr_number}\", \"hostname\":\"${params.Host_Name}\", \"clusterName\": \"${Clusters_List}\", \"containerName\": \"${containers}\", \"action\": \"${params.Action}\", \"cluster_safe_url\": \"${Cluster_Safe_URL}\", \"mancenter\": \"${params.Mancenter}\" }"
                        }
                    }
                    
                    println("Extra-Vars are: " + extravars)
                    
                    // Updated Ansible execution with null checking
                    try {
                        def ansibleOutput = deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)
                        
                        // NEW: Check if ansibleOutput is null before calling contains()
                        if (ansibleOutput == null) {
                            error("Ansible execution returned null output. Check Ansible Tower configuration or credentials.")
                        }
                        
                        // NEW: Only check contains() if ansibleOutput is not null
                        if (ansibleOutput.contains("skipping: no hosts matched")) {
                            error("Ansible execution failed: No matching hosts found for ${params.Host_Name}")
                        }
                        
                        println("Ansible execution completed successfully with output: ${ansibleOutput}")
                        
                    } catch (Exception e) {
                        // NEW: Enhanced error message with exception details
                        println "Error during Ansible execution: ${e.getMessage()}"
                        error("Pipeline failed due to Ansible execution error: ${e.toString()}")
                    }
                }
            }
        }
    }
}
