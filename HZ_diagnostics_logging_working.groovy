pipeline {
    agent { 
        label 'cm-linux' 
    }
    
    stages {
        stage('HZ-configfile-deployment') {
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    def userName
                    def password
                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]
                    
                    git branch: "main",
                        credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH",
                        url: "https://alm-github.systems.uk.SBI/dtc-hazelcast/Hazelcast-Services.git"
                    
                    sh("mkdir jqdir")
                    dir('jqdir') {
                        git branch: "master",
                            credentialsId: "SBINET-G3-DEV-GITHUB-OAUTH",
                            url: "https://alm-github.systems.uk.SBI/sprintnet/jq.git"
                    }
                    
                    sh "chmod +x ./jq"
                    sh "mv ./jq ../"
                    
                    dir("${workspace}/${foldername}/") {
                        sh "{ set +x; } 2>/dev/null;"
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile 'env.txt'
                        
                        properties([
                            parameters([
                                [
                                    $class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Environment',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return['Could not get The environments']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "${envList}"
                                        ]
                                    ]
                                ],
                                [
                                    $class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'Logging_type',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return ['Could not get logging option']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: '''return ["jdk", "log4j2"]'''
                                        ]
                                    ]
                                ],
                                [
                                    $class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'mancenter',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return['Could not get The mancenter']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: '''return ["Yes", "No"]'''
                                        ]
                                    ]
                                ],
                                [
                                    $class: 'ChoiceParameter',
                                    choiceType: 'PT_SINGLE_SELECT',
                                    filterLength: 1,
                                    filterable: false,
                                    name: 'backup',
                                    script: [
                                        $class: 'GroovyScript',
                                        fallbackScript: [
                                            classpath: [],
                                            sandbox: true,
                                            script: "return['Could not get backup']"
                                        ],
                                        script: [
                                            classpath: [],
                                            sandbox: true,
                                            script: '''return ["Yes", "No"]'''
                                        ]
                                    ]
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter Server Name',
                                    name: 'HostName'
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter sourcePath',
                                    name: 'sourcePath'
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter destPath',
                                    name: 'destPath'
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter artifactId',
                                    name: 'artifactId'
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter artifact name which want to deploy else keep empty for config file deployment',
                                    name: 'artifactId'
                                ],
                                [
                                    $class: 'StringParameterDefinition',
                                    defaultValue: '',
                                    description: 'Please enter CR Number for Production Deployment',
                                    name: 'cr_number'
                                ]
                            ])
                        ])

                        def templateId = "110777"
                        def containers = "container01"
                        def jqCli = "${workspace}/jq"
                        def file = "Cluster.json"
                        def clusterSafeUrl = "clusterSafeUrl.json"
                        def clusterNameFile = "ClusterName.json"
                        def deployments = load "${workspace}/deployment.groovy"
                        def environment = "${params.Environment}"
                        
                        def Clusters_List = sh(
                            script: """{ set +x; } 2>/dev/null; cat ${file} | ${jqCli} -r .'${environment}'""",
                            returnStdout: true
                        ).trim()
                        
                        def ClusterName = sh(
                            script: """{ set +x; } 2>/dev/null; cat ${clusterNameFile} | ${jqCli} -r .'${environment}'""",
                            returnStdout: true
                        ).trim()
                        
                        def Cluster_Safe_URL = sh(
                            script: """{ set +x; } 2>/dev/null; cat ${clusterSafeUrl} | ${jqCli} -r .'${environment}'""",
                            returnStdout: true
                        ).trim()
                        
                        def Hostname = "${params.HostName}"
                        
                        if (Hostname == "") {
                            println "Error! Host_Name is Empty. Please enter Host_Name value."
                            sh "{ set +x; } 2>/dev/null; exit 1"
                        }

                        templateId = "110777"
                        containers = "container01"
                        def extravars = """{"hostname": "${Hostname}", "artifactId":"${params.artifactId}", "cluster": "${Clusters_List}", "container": "${containers}", "env": "${environment}", "cluster_safe_url": "${Cluster_Safe_URL}", "mancenter": "${mancenter}", "Logging_type": "${Logging_type}", "sourcePath": "${sourcePath}", "destPath": "${destPath}", "artifactId": "${artifactId}"}"""

                        if(environment.toLowerCase().startsWith("prod")) {
                            timeout(time: 120, unit: 'SECONDS') {
                                def userInput = input(
                                    id: 'Input-username',
                                    parameters: [
                                        [
                                            $class: 'StringParameterDefinition',
                                            defaultValue: '',
                                            description: 'Enter Username:',
                                            name: 'Username'
                                        ],
                                        [
                                            $class: 'hudson.model.PasswordParameterDefinition',
                                            description: 'Enter Password:',
                                            name: 'Password'
                                        ]
                                    ],
                                    submitterParameter: 'approver'
                                )
                                
                                userName = userInput['Username']
                                password = userInput['Password'].toString()
                                templateId = "64468"
                                def env = "prod"
                                def cr_number = "${params.cr_number}"
                                
                                if(cr_number == "") {
                                    println "Error! CR Number is Empty for Production Environment."
                                    sh "{ set +x; } 2>/dev/null; exit 1"
                                }
                                
                                extravars = """{"cr_number": "${cr_number}", "hostname":"${Hostname}", "artifactId":"${params.artifactId}", "cluster": "${Clusters_List}", "container": "${containers}", "env": "${environment}", "cluster_safe_url": "${Cluster_Safe_URL}", "mancenter": "${mancenter}", "Logging_type": "${Logging_type}", "sourcePath": "${sourcePath}", "destPath": "${destPath}", "artifactId":"${artifactId}"}"""
                            }
                        }
                        
                        println("Extra-Vars are: " + extravars)
                        deployments.getApproval("Approve: To update config files into cluster", "HZ-approvers")
                        deployments.triggerAnsibleTower(templateId, env, extravars, userName, password)
                    }
                }
            }
        }
    }
}