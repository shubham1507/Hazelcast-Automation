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
                    
                    git branch: 'main', 
                        credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                        url: 'https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git'
                    
                    sh 'mkdir jqdir'
                    dir('jqdir') {
                        git branch: 'master', 
                            credentialsId: 'HSBCNET-G3-DEV-GITHUB-OAUTH', 
                            url: 'https://alm-github.systems.uk.hsbc/sprintnet/jq.git'
                        sh '''
                            chmod +x ./jq
                            mv ./jq ../
                        '''
                    }
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh '{ set +x; } 2>/dev/null'
                        sh 'cat Environments.groovy > env.txt'  // Fixed redirection syntax
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
                    
                    def Clusters_List = sh(script: "{ set +x; } 2>/dev/null; cat ${file} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    def Cluster_Safe_URL = sh(script: "{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqCli} -r . '${environment}'", returnStdout: true).trim()
                    
                    def Hostname = "${params.Host_Name}"
                    if (Hostname == '') {
                        println 'Error! Host_Name is Empty. Please enter Host_Name value.'
                        sh '{ set +x; } 2>/dev/null; exit 1'
                    }
                    
                    def environment = "${params.Environment}"
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
                            
                            def cr_number = "${params.cr_number}"
                            if (cr_number == '') {
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
