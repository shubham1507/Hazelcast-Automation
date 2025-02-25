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
                    
                    // Clone the main repository with debugging
                    git branch: 'main', 
                        credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                        url: 'https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git'
                    sh 'ls -la ${WORKSPACE}'  // Debug: Check files after cloning
                    
                    // Clone and handle jq
                    sh 'mkdir jqdir'
                    dir('jqdir') {
                        git branch: 'master', 
                            credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                            url: 'https://alm-github.systems.uk.hsbc/sprintnet/jq.git'
                        sh '''
                            ls -la  // Debug: List contents to verify jq exists
                            chmod +x ./jq
                            mv ./jq ../
                        '''
                    }
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh '{ set +x; } 2>/dev/null'
                        sh 'ls -la'  // Debug: Check files in the folder
                        sh 'cat Environments.groovy > env.txt'
                        def envList = readFile 'env.txt'
                        
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
                                            script: "return ['Could not get The environments']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: envList
                                        ]
                                    ]
                                ],
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Action',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['Could not get The environments']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['start', 'stop', 'restart']"
                                        ]
                                    ]
                                ],
                                [$class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Mancenter',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['Could not get The environments']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['false', 'true']"
                                        ]
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
                    def containers = 'container81'
                    def file = 'Cluster.json'
                    def clusterSafeUrl = 'clusterSafeUrl.json'
                    
                    // Debug: Print variables before processing
                    println "Environment: ${params.Environment}"
                    println "Host_Name: ${params.Host_Name}"
                    println "Action: ${params.Action}"
                    println "Mancenter: ${params.Mancenter}"
                    
                    // Ensure environment is defined before using it
                    def environment = params.Environment ?: 'dev'  // Default to 'dev' if not set
                    
                    // Check if files exist before reading
                    dir("${WORKSPACE}/${foldername}/") {
                        if (!fileExists('Cluster.json')) {
                            println "Error! Cluster.json does not exist in ${WORKSPACE}/${foldername}/"
                            sh '{ set +x; } 2>/dev/null; exit 1'
                        }
                        if (!fileExists('clusterSafeUrl.json')) {
                            println "Error! clusterSafeUrl.json does not exist in ${WORKSPACE}/${foldername}/"
                            sh '{ set +x; } 2>/dev/null; exit 1'
                        }
                        
                        def Clusters_List = sh(script: "{ set +x; } 2>/dev/null; cat Cluster.json | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                        def Cluster_Safe_URL = sh(script: "{ set +x; } 2>/dev/null; cat clusterSafeUrl.json | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                        
                        // Validate critical variables
                        if (!Clusters_List) {
                            println "Error! clusterName (Clusters_List) is empty for environment ${environment}."
                            sh '{ set +x; } 2>/dev/null; exit 1'
                        }
                        if (!Cluster_Safe_URL) {
                            println "Error! cluster_safe_url is empty for environment ${environment}."
                            sh '{ set +x; } 2>/dev/null; exit 1'
                        }
                    }
                    
                    def Hostname = params.Host_Name?.trim()
                    if (!Hostname) {
                        println 'Error! Host_Name is Empty. Please enter Host_Name value.'
                        sh '{ set +x; } 2>/dev/null; exit 1'
                    }
                    
                    // Validate hostname format
                    if (!Hostname =~ /^[a-zA-Z0-9.-]+$/) {
                        println "Error! Invalid hostname format: ${Hostname}"
                        sh '{ set +x; } 2>/dev/null; exit 1'
                    }
                    
                    def extravars = "{\"hostname\":\"${params.Host_Name}\", \"clusterName\":\"${Clusters_List}\", \"containerName\":\"${containers}\", \"action\":\"${params.Action}\", \"cluster_safe_url\":\"${Cluster_Safe_URL}\", \"mancenter\":\"${params.Mancenter}\"}"
                    
                    if (environment.toLowerCase().startsWith('prod')) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(id: 'Input-username',
                                parameters: [
                                    [$class: 'StringParameterDefinition', 
                                     defaultValue: '', 
                                     description: 'Enter Username:', 
                                     name: 'Username'],
                                    [$class: 'hudson.model.PasswordParameterDefinition', 
                                     description: 'Enter Password:', 
                                     name: 'Password']
                                ],
                                submitterParameter: 'approver')
                            
                            userName = userInput['Username']
                            password = userInput['Password'].toString()
                            environment = 'prod'
                            templateId = '28669'
                            
                            def cr_number = params.cr_number?.trim()
                            if (!cr_number) {
                                println 'Error! CR Number is Empty for Production Environment.'
                                sh '{ set +x; } 2>/dev/null; exit 1'
                            }
                            
                            extravars = "{\"cr_number\":\"${params.cr_number}\", \"hostname\":\"${params.Host_Name}\", \"clusterName\":\"${Clusters_List}\", \"containerName\":\"${containers}\", \"action\":\"${params.Action}\", \"cluster_safe_url\":\"${Cluster_Safe_URL}\", \"mancenter\":\"${params.Mancenter}\"}"
                        }
                    }
                    
                    println("Extra-Vars are: " + extravars)
                    
                    // Trigger Ansible Tower and capture the output
                    def ansibleOutput = deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)
                    
                    // Check if the output contains "skipping: no hosts matched"
                    if (ansibleOutput?.contains("skipping: no hosts matched")) {
                        println 'Error! Ansible playbook output indicates no hosts matched.'
                        sh '{ set +x; } 2>/dev/null; exit 1'
                    }
                }
            }
        }
    }
}
