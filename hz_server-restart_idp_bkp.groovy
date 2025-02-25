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
                    
                    // Clone the main repository
                    git branch: 'main', 
                        credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                        url: 'https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git'
                    
                    // Clone and handle jq
                    sh 'mkdir jqdir'
                    dir('jqdir') {
                        git branch: 'master', 
                            credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                            url: 'https://alm-github.systems.uk.hsbc/sprintnet/jq.git'
                        sh 'ls -la'  // Debug: List contents to verify jq exists
                        sh 'chmod +x ./jq || echo "chmod failed - jq not found"'
                    }
                    sh 'mv ./jqdir/jq ../ || echo "mv failed - jq not found"'
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh '{ set +x; } 2>/dev/null'
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
                    def containers = 'container01'  // Ensure this matches the expected container postfix
                    def file = 'Cluster.json'
                    def clusterSafeUrl = 'clusterSafeUrl.json'
                    
                    // Debug: Print variables before processing
                    println "Environment: ${params.Environment}"
                    println "Host_Name: ${params.Host_Name}"
                    println "Action: ${params.Action}"
                    println "Mancenter: ${params.Mancenter}"
                    
                    // Ensure environment is defined before using it
                    def environment = params.Environment ?: 'dev'  // Default to 'dev' if not set
                    def Clusters_List = sh(script: "{ set +x; } 2>/dev/null; cat ${file} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    def Cluster_Safe_URL = sh(script: "{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    
                    def Hostname = params.Host_Name?.trim()
                    if (!Hostname) {
                        println 'Error! Host_Name is Empty. Please enter Host_Name value.'
                        sh '{ set +x; } 2>/dev/null; exit 1'
                    }
                    
                    def extravars = [
                        hostname: Hostname,
                        clusterName: Clusters_List,
                        containerName: containers,
                        action: params.Action,
                        cluster_safe_url: Cluster_Safe_URL,
                        mancenter: params.Mancenter
                    ].collect { k, v -> "\"${k}\": \"${v}\"" }.join(', ')
                    extravars = "{${extravars}}"
                    
                    println "Extra-Vars: ${extravars}"
                    
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
                            
                            extravars = [
                                cr_number: cr_number,
                                hostname: Hostname,
                                clusterName: Clusters_List,
                                containerName: containers,
                                action: params.Action,
                                cluster_safe_url: Cluster_Safe_URL,
                                mancenter: params.Mancenter
                            ].collect { k, v -> "\"${k}\": \"${v}\"" }.join(', ')
                            extravars = "{${extravars}}"
                        }
                    }
                    
                    // Debug: Print final extravars before Ansible execution
                    println "Final Extra-Vars for Ansible: ${extravars}"
                    
                    def ansibleOutput = deployments.triggerAnsibleTower(templateId, environment, extravars, userName, password)
                    println "Ansible Output: ${ansibleOutput}"
                    
                    def noHostMatched = ansibleOutput.toLowerCase().contains('no hosts matched')
                    if (noHostMatched) {
                        println 'Error! Ansible playbook skipped because no hosts matched'
                        error 'Pipeline failed due to no matching hosts'
                    } else if (ansibleOutput.toLowerCase().contains('failed')) {
                        println 'Error! Ansible playbook execution failed'
                        error 'Pipeline failed due to Ansible execution failure'
                    } else {
                        println 'Ansible execution completed successfully'
                    }
                }
            }
        }
    }
}
